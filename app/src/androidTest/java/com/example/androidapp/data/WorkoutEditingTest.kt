package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for correcting a past workout (ROADMAP P1.7).
 *
 * Before this, `Finish` was a one-way door: a mistyped weight appeared in history
 * and could never be fixed. These assert the correction actually lands, and that
 * the history list's SQL aggregates follow it — which is also what keeps the
 * list's volume formula and the detail screen's agreeing.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutEditingTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutRepository

    private val clock = TimeSource { Instant.parse("2026-09-28T08:00:00Z") }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomWorkoutRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun correctingASet_changesWhatHistoryReports() = runTest {
        seedFinishedWorkout()
        val setId = database.backupDao().allSets().single().id
        // 100 kg x 5 = 500,000 gram-reps to begin with.
        assertEquals(500_000L, historyVolume())

        val result = repository.updateSet(setId, reps = 8, weightGrams = 100_000L, rpe = null, note = null)

        assertTrue(result is DataResult.Success)
        assertEquals("the list's aggregate must follow the edit", 800_000L, historyVolume())
    }

    @Test
    fun deletingAPastWorkout_removesItFromHistory() = runTest {
        val sessionId = seedFinishedWorkout()
        assertTrue(database.workoutDao().observeHistory().first().isNotEmpty())

        val result = repository.deleteSession(sessionId)

        assertTrue(result is DataResult.Success)
        assertTrue(
            "a deleted workout must not linger in the history list",
            database.workoutDao().observeHistory().first().isEmpty(),
        )
    }

    @Test
    fun correctingASetThatIsGone_failsAsNotFound() = runTest {
        seedFinishedWorkout()
        val setId = database.backupDao().allSets().single().id
        repository.deleteSet(setId)

        val result = repository.updateSet(setId, reps = 5, weightGrams = 1_000L, rpe = null, note = null)

        // A stale screen holding a deleted set's id must not resurrect it.
        assertEquals(DataError.NotFound, (result as DataResult.Failure).error)
    }

    @Test
    fun correctingASet_recordsItsRpeAndComment() = runTest {
        seedFinishedWorkout()
        val setId = database.backupDao().allSets().single().id

        val result = repository.updateSet(
            setId,
            reps = 5,
            weightGrams = 100_000L,
            rpe = 8,
            note = "Felt heavy",
        )

        assertTrue(result is DataResult.Success)
        val stored = database.workoutDao().findSetById(setId)!!
        assertEquals(8, stored.rpe)
        assertEquals("Felt heavy", stored.note)
        // The columns an edit does not touch are preserved, not invented.
        assertEquals(0, stored.setIndex)
        assertEquals("se1", stored.sessionExerciseId)
    }

    @Test
    fun aBlankComment_isStoredAsNull_andRpeCanBeCleared() = runTest {
        seedFinishedWorkout()
        val setId = database.backupDao().allSets().single().id
        repository.updateSet(setId, reps = 5, weightGrams = 100_000L, rpe = 8, note = "Felt heavy")

        repository.updateSet(setId, reps = 5, weightGrams = 100_000L, rpe = null, note = "   ")

        val stored = database.workoutDao().findSetById(setId)!!
        assertNull(stored.note)
        assertNull("clearing an RPE is how a mistyped one is undone", stored.rpe)
    }

    @Test
    fun anRpeOutsideTheScale_isRefused_withoutTouchingTheSet() = runTest {
        seedFinishedWorkout()
        val setId = database.backupDao().allSets().single().id

        val result = repository.updateSet(
            setId,
            reps = 5,
            weightGrams = 100_000L,
            rpe = 11,
            note = null,
        )

        assertTrue((result as DataResult.Failure).error is DataError.Invalid)
        assertNull("a refused write must leave the set alone", database.workoutDao().findSetById(setId)!!.rpe)
    }

    private suspend fun historyVolume(): Long =
        database.workoutDao().observeHistory().first().single().volumeGrams

    private suspend fun seedFinishedWorkout(): String {
        database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "back-squat",
                    name = "Back Squat",
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

        val session = database.workoutDao().findOrCreateActiveSession(id = "s1", now = 1_000L).session
        database.workoutDao().insertSessionExercise(
            SessionExerciseEntity(
                id = "se1",
                sessionId = session.id,
                exerciseId = "back-squat",
                position = 0,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null,
            ),
        )
        database.workoutDao().insertSet(
            SetEntryEntity(
                id = "set1",
                sessionExerciseId = "se1",
                setIndex = 0,
                reps = 5,
                weightGrams = 100_000L,
                setType = SetType.NORMAL,
                completedAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deletedAt = null,
            ),
        )
        database.workoutDao().markFinished(id = session.id, at = 2_000L)
        return session.id
    }
}
