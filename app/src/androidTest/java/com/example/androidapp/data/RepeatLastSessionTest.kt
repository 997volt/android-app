package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.WorkoutSessionEntity
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.repository.StartedSession
import com.example.androidapp.domain.ZoneOffsetSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Repeating the last workout, against a real database (ROADMAP N29).
 *
 * Two of this feature's three edge cases are SQL rules — a library row deleted since is skipped, and
 * the same exercise twice stays twice — so a fake cannot hold them: it would agree with whatever the
 * query was supposed to do. These seed rows and read back what the app would show.
 */
class RepeatLastSessionTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutRepository

    private val clock = TimeSource { Instant.parse("2026-10-01T08:00:00Z") }

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomWorkoutRepository(database, clock, ZoneOffsetSource { 0 })
        database.exerciseDao().insertAll(
            listOf(exercise("back-squat"), exercise("bench-press"), exercise("row")),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun itCopiesTheExercises_inTheOrderTheyWerePerformed() = runTest {
        seedFinishedWorkout("back-squat", "bench-press")

        val started = opened()
        assertEquals(
            listOf("back-squat", "bench-press"),
            repository.observeSessionExercises(started).first().map { it.exerciseId },
        )
    }

    @Test
    fun anExerciseDeletedFromTheLibrary_isSkipped_whileTheRestRepeat() = runTest {
        seedFinishedWorkout("back-squat", "bench-press")
        // Deleted from the library since the workout was performed.
        database.exerciseDao().softDelete("bench-press", deletedAt = 2_000L)

        val started = opened()
        assertEquals(
            "the surviving exercise repeats, the deleted one is skipped",
            listOf("back-squat"),
            repository.observeSessionExercises(started).first().map { it.exerciseId },
        )
    }

    @Test
    fun anExercisePerformedTwice_repeatsTwice() = runTest {
        seedFinishedWorkout("back-squat", "bench-press", "back-squat")

        val started = opened()
        assertEquals(
            "what was performed twice was performed twice",
            listOf("back-squat", "bench-press", "back-squat"),
            repository.observeSessionExercises(started).first().map { it.exerciseId },
        )
    }

    @Test
    fun withNoFinishedWorkout_itOpensAnEmptySession() = runTest {
        // The action is hidden in this case (a screen rule), and the repository's answer is an empty
        // session rather than a failure: there is simply nothing to copy.
        val started = opened()
        assertTrue(repository.observeSessionExercises(started).first().isEmpty())
    }

    /** Opens a session by repeating, and returns its id. */
    private suspend fun opened(): String {
        val result = repository.repeatLastSession()
        assertTrue("repeating opens a session: $result", result is DataResult.Success)
        return (result as DataResult.Success<StartedSession>).data.id
    }

    /** A finished workout holding [exerciseIds] in order. */
    private suspend fun seedFinishedWorkout(vararg exerciseIds: String) {
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
        exerciseIds.forEachIndexed { index, exerciseId ->
            dao.insertSessionExercise(
                SessionExerciseEntity(
                    id = "past-$index",
                    sessionId = "past",
                    exerciseId = exerciseId,
                    position = index,
                    restSeconds = null,
                    techniqueNote = null,
                    finishedAt = null,
                    supersetGroup = null,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    deletedAt = null,
                ),
            )
        }
    }

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
