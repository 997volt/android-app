package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.TemplateEntity
import com.example.androidapp.data.local.TemplateExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.ZoneOffsetSource
import com.example.androidapp.domain.repository.StartedSession
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Session-level writes, end to end through the repository (ROADMAP N4).
 *
 * The readiness prompt is driven by [StartedSession.isNew], so that flag is
 * asserted exactly rather than assumed: getting it wrong would either nag on every
 * resume or never ask at all.
 */
@RunWith(AndroidJUnit4::class)
class RoomWorkoutSessionTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutRepository

    private val clock = TimeSource { Instant.parse("2026-09-29T08:00:00Z") }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomWorkoutRepository(database, clock, ZoneOffsetSource { 0 })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aFreshlyOpenedSession_isNew_andKeepsItsReadinessNote() = runTest {
        val started = start()
        assertTrue(started.isNew)

        assertTrue(repository.setReadinessNote(started.id, "Shoulders still sore") is DataResult.Success)

        assertEquals(
            "Shoulders still sore",
            repository.observeSession(started.id).first()?.readinessNote,
        )
    }

    @Test
    fun resuming_doesNotReportANewSession() = runTest {        val first = start()
        val second = start()

        assertEquals("the open session must be reused", first.id, second.id)
        // This is what stops the prompt re-appearing on every resume (N4).
        assertFalse(second.isNew)
    }

    @Test
    fun aBlankNote_isStoredAsNull_ratherThanAnEmptyString() = runTest {
        val started = start()
        repository.setReadinessNote(started.id, "Shoulders sore")
        assertEquals(
            "Shoulders sore",
            repository.observeSession(started.id).first()?.readinessNote,
        )

        repository.setReadinessNote(started.id, "   ")

        assertNull(
            "clearing the field must leave one representation of nothing",
            repository.observeSession(started.id).first()?.readinessNote,
        )
    }

    @Test
    fun setReadinessNote_onAGoneSession_isNotFound() = runTest {
        val failure = repository.setReadinessNote("no-such-session", "x") as DataResult.Failure

        assertEquals(DataError.NotFound, failure.error)
    }

    @Test
    fun startingFromATemplate_opensTheWorkoutWithItsExercisesInOrder() = runTest {
        seedTemplate("t1", listOf("back-squat", "bench-press"))

        val started = start(templateId = "t1")

        assertEquals(
            listOf("Back Squat", "Bench Press"),
            repository.observeSessionExercises(started.id).first().map { it.exerciseName },
        )
    }

    @Test
    fun theSeededExercises_arePositionsZeroUpwards_likeThePickerWouldWrite() = runTest {
        seedTemplate("t1", listOf("back-squat", "bench-press"))

        val started = start(templateId = "t1")

        assertEquals(
            listOf(0, 1),
            repository.observeSessionExercises(started.id).first().map { it.position },
        )
    }

    @Test
    fun resumingWithATemplate_doesNotSeedItAgain() = runTest {
        seedTemplate("t1", listOf("back-squat"))
        val first = start(templateId = "t1")

        // A second start — the user tapped Start from template again, or the
        // screen was recreated — must not duplicate the exercises (N3).
        val second = start(templateId = "t1")

        assertEquals(first.id, second.id)
        assertEquals(
            1,
            repository.observeSessionExercises(first.id).first().size,
        )
    }

    @Test
    fun startingFromAnEmptyTemplate_opensAnEmptyWorkout() = runTest {
        seedTemplate("t1", emptyList())

        val started = start(templateId = "t1")

        assertTrue(repository.observeSessionExercises(started.id).first().isEmpty())
    }

    @Test
    fun startingEmpty_leavesTheTemplateAlone() = runTest {
        seedTemplate("t1", listOf("back-squat"))

        val started = start()

        assertTrue(repository.observeSessionExercises(started.id).first().isEmpty())
        // The template is a plan, not a log: starting a workout must not consume it.
        assertEquals(
            listOf("back-squat"),
            database.templateDao().findExerciseIdsInOrder("t1"),
        )
    }

    /** A template is seeded once, so the copy must not be shared with the session. */
    @Test
    fun editingTheSession_doesNotTouchTheTemplate() = runTest {
        seedTemplate("t1", listOf("back-squat", "bench-press"))
        val started = start(templateId = "t1")
        val benchPress = repository.observeSessionExercises(started.id).first()[1]

        repository.removeExercise(benchPress.id)

        assertEquals(
            listOf("back-squat"),
            repository.observeSessionExercises(started.id).first().map { it.exerciseId },
        )
        assertEquals(
            listOf("back-squat", "bench-press"),
            database.templateDao().findExerciseIdsInOrder("t1"),
        )
    }

    private suspend fun seedTemplate(
        templateId: String,
        exerciseIds: List<String>,
        restSeconds: Int? = null,
        techniqueNote: String? = null,
    ) {
        database.templateDao().insertTemplate(
            TemplateEntity(
                id = templateId,
                name = "Push day",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deletedAt = null,
            ),
        )
        exerciseIds.forEachIndexed { index, exerciseId ->
            database.exerciseDao().insertAll(
                listOf(
                    ExerciseEntity(
                        id = exerciseId,
                        name = exerciseId.split("-").joinToString(" ") { part ->
                            part.replaceFirstChar { it.uppercase() }
                        },
                        primaryMuscle = MuscleGroup.QUADS,
                        secondaryMuscles = emptyList(),
                        equipment = Equipment.BARBELL,
                        movementPattern = MovementPattern.SQUAT,
                        isCustom = false,
                        createdAt = 0L,
                        updatedAt = 0L,
                        deletedAt = null,
                    ),
                ),
            )
            database.templateDao().insertTemplateExercise(
                TemplateExerciseEntity(
                    id = "$templateId-$exerciseId",
                    templateId = templateId,
                    exerciseId = exerciseId,
                    position = index,
                    restSeconds = restSeconds,
                    techniqueNote = techniqueNote,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    deletedAt = null,
                ),
            )
        }
    }

    private suspend fun start(templateId: String? = null): StartedSession =
        (repository.startOrResumeSession(templateId) as DataResult.Success).data

    @Test
    fun startingFromAPlan_carriesItsRestAndCueOntoTheSession() = runTest {
        // ROADMAP N14: the plan's rest and cue exist to be used, and the fallback to
        // the library's is only observable if the plan's value wins.
        seedTemplate("t1", listOf("back-squat"), restSeconds = 180, techniqueNote = "Slow descent")

        val started = start("t1")
        val row = repository.observeSessionExercises(started.id).first().single()

        assertEquals("the plan's break, not the library's", 180, row.restSeconds)
        assertEquals("Slow descent", row.techniqueNote)
    }

    @Test
    fun aPlanThatPrescribesNoRest_leavesTheLibrarysShowing() = runTest {
        seedTemplate("t1", listOf("back-squat"))

        val started = start("t1")
        val row = repository.observeSessionExercises(started.id).first().single()

        // The exercise seeded by this test has no library rest of its own, so the
        // fallback shows as null rather than as an override with nothing (N5).
        assertNull(row.restSeconds)
        assertNull(row.techniqueNote)
    }
}
