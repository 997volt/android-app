package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.WorkoutSessionEntity
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.repository.TemplateSetEdit
import com.example.androidapp.domain.ZoneOffsetSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Saving a finished workout as a plan, against a real database (ROADMAP N31).
 *
 * The rules worth this suite are the ones a fake would agree with whatever it was told: a deleted
 * library exercise being skipped, the copy's order, and the copy standing independent of its source.
 */
class TemplateFromSessionTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var workouts: RoomWorkoutRepository
    private lateinit var templates: RoomTemplateRepository

    private val clock = TimeSource { Instant.parse("2026-10-02T08:00:00Z") }

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        workouts = RoomWorkoutRepository(database, clock, ZoneOffsetSource { 0 })
        templates = RoomTemplateRepository(database, clock)
        database.exerciseDao().insertAll(
            listOf(exercise("back-squat"), exercise("bench-press"), exercise("row")),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun itCopiesTheExercisesAndTheirSets_inOrder() = runTest {
        seedFinishedWorkout(
            "back-squat" to listOf(SetType.WARMUP to 5, SetType.NORMAL to 3),
            "bench-press" to listOf(SetType.NORMAL to 8),
        )

        val templateId = saved("Push day")

        val planned = templates.observeExercises(templateId).first()
        assertEquals(
            "the order is the order they were performed",
            listOf("back-squat", "bench-press"),
            planned.map { it.exerciseId },
        )
        assertEquals(
            "the warm-up is still a warm-up",
            listOf(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL),
            planned.flatMap { it.sets }.map { it.role },
        )
        assertEquals(
            "and the reps came with it",
            listOf(5, 3, 8),
            planned.flatMap { it.sets }.map { it.targetRepsMax },
        )
    }

    @Test
    fun anExerciseDeletedFromTheLibrary_isSkipped_whileTheRestCopy() = runTest {
        seedFinishedWorkout(
            "back-squat" to listOf(SetType.NORMAL to 5),
            "bench-press" to listOf(SetType.NORMAL to 5),
        )
        database.exerciseDao().softDelete("bench-press", deletedAt = 2_000L)

        val templateId = saved("Push day")

        assertEquals(
            "the surviving exercise copies, the deleted one is skipped",
            listOf("back-squat"),
            templates.observeExercises(templateId).first().map { it.exerciseId },
        )
    }

    @Test
    fun anEmptyWorkout_refusesToBecomeAPlan() = runTest {
        // Seeded as a finished workout with no exercises at all.
        seedFinishedWorkout()

        val result = templates.createTemplateFromSession("past", "Nothing")

        assertTrue("refused rather than made empty: $result", result is DataResult.Failure)
        assertTrue(templates.observeTemplates().first().isEmpty())
    }

    @Test
    fun theCopyIsIndependent_inBothDirections() = runTest {
        seedFinishedWorkout("back-squat" to listOf(SetType.NORMAL to 5))
        val templateId = saved("Push day")

        // Changing the source workout must not reach the plan.
        database.workoutDao().insertSet(
            set("extra", "past-0", index = 1, reps = 20, type = SetType.NORMAL),
        )
        assertEquals(
            "the plan did not gain the set added afterwards",
            listOf(5),
            templates.observeExercises(templateId).first().single().sets.map { it.targetRepsMax },
        )

        // And changing the plan must not reach the workout.
        val plannedSet = templates.observeExercises(templateId).first().single().sets.single()
        templates.updateSet(
            plannedSet.id,
            TemplateSetEdit(
                role = plannedSet.role,
                targetWeightGrams = plannedSet.targetWeightGrams,
                targetAssistanceGrams = plannedSet.targetAssistanceGrams,
                targetRepsMin = plannedSet.targetRepsMin,
                targetRepsMax = 99,
                targetRpeHalves = plannedSet.targetRpeHalves,
                note = plannedSet.note,
            ),
        )
        val sourceSets = workouts.observeSets("past").first()
        // Two rows: the one the plan was made from, and the one added afterwards. Both are the
        // workout's own; neither was touched by the plan's edit.
        assertEquals("the workout still reads as it was performed", listOf(5, 20), sourceSets.map { it.reps })
        assertEquals("and the plan's edit did not reach it", 5, sourceSets.first().reps)
    }

    @Test
    fun startingTheCopiedPlan_arrivesWithTheSameExercises() = runTest {
        seedFinishedWorkout(
            "back-squat" to listOf(SetType.NORMAL to 5),
            "bench-press" to listOf(SetType.NORMAL to 5),
        )
        val templateId = saved("Push day")

        val started = workouts.startOrResumeSession(templateId) as DataResult.Success

        assertEquals(
            "the plan seeds a session in the same order",
            listOf("back-squat", "bench-press"),
            workouts.observeSessionExercises(started.data.id).first().map { it.exerciseId },
        )
    }

    /** Saves the seeded workout as a plan and returns its id. */
    private suspend fun saved(name: String): String {
        val result = templates.createTemplateFromSession("past", name)
        assertTrue("saving succeeded: $result", result is DataResult.Success)
        return (result as DataResult.Success<String>).data
    }

    /** A finished workout holding [exercises], each with the given sets in order. */
    private suspend fun seedFinishedWorkout(vararg exercises: Pair<String, List<Pair<SetType, Int>>>) {
        val dao = database.workoutDao()
        dao.insertSession(
            WorkoutSessionEntity(
                id = "past",
                startedAt = 1_000L,
                finishedAt = 2_000L,
                notes = null,
                restEndsAt = null,
                readinessNote = null,
                createdAt = 1_000L,
                updatedAt = 2_000L,
                deletedAt = null,
            ),
        )
        exercises.forEachIndexed { index, (exerciseId, sets) ->
            val rowId = "past-$index"
            dao.insertSessionExercise(
                SessionExerciseEntity(
                    id = rowId,
                    sessionId = "past",
                    exerciseId = exerciseId,
                    position = index,
                    restSeconds = 120,
                    techniqueNote = null,
                    finishedAt = null,
                    supersetGroup = if (index == 0) 1 else null,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    deletedAt = null,
                ),
            )
            sets.forEachIndexed { setIndex, (type, reps) ->
                dao.insertSet(set("past-$index-$setIndex", rowId, setIndex, reps, type))
            }
        }
    }

    private fun set(
        id: String,
        sessionExerciseId: String,
        index: Int,
        reps: Int,
        type: SetType,
    ) = SetEntryEntity(
        id = id,
        sessionExerciseId = sessionExerciseId,
        setIndex = index,
        reps = reps,
        weightGrams = 100_000L,
        assistanceGrams = 0L,
        setType = type,
        rpeHalves = null,
        note = null,
        completedAt = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = null,
    )

    private fun exercise(id: String) = ExerciseEntity(
        id = id,
        name = id,
        primaryMuscle = MuscleGroup.QUADS,
        secondaryMuscles = emptyList(),
        equipment = Equipment.BARBELL,
        movementPattern = MovementPattern.SQUAT,
        isCustom = false,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )
}
