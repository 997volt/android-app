package com.example.androidapp.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the history aggregates (ROADMAP P1.6).
 *
 * The totals are computed in SQL, which is the part of this feature most likely to
 * be subtly wrong — a `JOIN` that double-counts, or a soft delete that leaks into
 * the volume. Asserting the numbers against hand-built data is the only way to
 * know the query says what it looks like it says.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutHistoryQueryTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var dao: WorkoutDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        dao = database.workoutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun totals_countOnlyLiveSetsAndLiveExercises() = runTest {
        val session = dao.findOrCreateActiveSession(id = "s1", now = 1_000L)
        seedExercise("back-squat")
        seedExercise("bench-press")
        dao.insertSessionExercise(sessionExercise("se1", session.id, "back-squat", 0))
        dao.insertSessionExercise(sessionExercise("se2", session.id, "bench-press", 1))

        // 2 sets on the first exercise (100 kg x 5, 100 kg x 5) and 1 on the second.
        dao.insertSet(setEntry("s-1", "se1", reps = 5, weightGrams = 100_000))
        dao.insertSet(setEntry("s-2", "se1", reps = 5, weightGrams = 100_000))
        dao.insertSet(setEntry("s-3", "se2", reps = 8, weightGrams = 60_000))

        // A deleted set and a deleted exercise must not contribute.
        dao.insertSet(setEntry("s-4", "se2", reps = 8, weightGrams = 60_000))
        dao.softDeleteSet("s-4", at = 2_000L)
        dao.insertSessionExercise(sessionExercise("se3", session.id, "bench-press", 2))
        dao.softDeleteSessionExercise("se3", at = 2_000L)

        dao.markFinished(id = session.id, at = 3_000L)

        val summary = dao.observeHistory().first().single()

        assertEquals("deleted exercise excluded", 2, summary.exerciseCount)
        assertEquals("deleted set excluded", 3, summary.setCount)
        // (100,000 x 5) x 2 + (60,000 x 8) = 1,480,000 gram-reps
        assertEquals(1_480_000L, summary.volumeGrams)
    }

    @Test
    fun anOpenWorkoutIsNotHistory() = runTest {
        // An in-progress session belongs on the workout screen, not in a list you
        // are meant to be reading.
        dao.findOrCreateActiveSession(id = "open", now = 1_000L)

        assertEquals(emptyList<WorkoutSummaryRow>(), dao.observeHistory().first())
    }

    @Test
    fun aFinishedWorkoutWithNoSets_stillAppears_atZeroVolume() = runTest {
        val session = dao.findOrCreateActiveSession(id = "empty", now = 1_000L)
        dao.markFinished(id = session.id, at = 2_000L)

        val summary = dao.observeHistory().first().single()

        assertEquals(0, summary.setCount)
        assertEquals(0L, summary.volumeGrams)
    }

    @Test
    fun historyIsNewestFirst() = runTest {
        val older = dao.findOrCreateActiveSession(id = "older", now = 1_000L)
        dao.markFinished(id = older.id, at = 2_000L)
        val newer = dao.findOrCreateActiveSession(id = "newer", now = 5_000L)
        dao.markFinished(id = newer.id, at = 6_000L)

        assertEquals(listOf("newer", "older"), dao.observeHistory().first().map { it.id })
    }

    private suspend fun seedExercise(id: String) {
        database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = id,
                    name = id,
                    primaryMuscle = com.example.androidapp.domain.model.MuscleGroup.BACK,
                    secondaryMuscles = emptyList(),
                    equipment = com.example.androidapp.domain.model.Equipment.BARBELL,
                    movementPattern = com.example.androidapp.domain.model.MovementPattern.HINGE,
                    isCustom = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                ),
            ),
        )
    }

    private fun sessionExercise(id: String, sessionId: String, exerciseId: String, position: Int) =
        SessionExerciseEntity(
            id = id,
            sessionId = sessionId,
            exerciseId = exerciseId,
            position = position,
            createdAt = 0L,
            updatedAt = 0L,
            deletedAt = null,
        )

    private fun setEntry(id: String, sessionExerciseId: String, reps: Int, weightGrams: Long) =
        SetEntryEntity(
            id = id,
            sessionExerciseId = sessionExerciseId,
            setIndex = 0,
            reps = reps,
            weightGrams = weightGrams,
            setType = com.example.androidapp.domain.model.SetType.NORMAL,
            completedAt = 0L,
            createdAt = 0L,
            updatedAt = 0L,
            deletedAt = null,
        )
}
