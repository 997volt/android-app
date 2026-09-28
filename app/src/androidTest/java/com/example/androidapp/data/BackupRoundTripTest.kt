package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented round-trip tests for backup and restore (ROADMAP P1.12).
 *
 * This is the feature's real acceptance test: the file format is only worth
 * anything if a database survives export and re-import through actual SQLite,
 * with the foreign keys and soft deletes intact.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomBackupRepository

    private val clock = TimeSource { Instant.parse("2026-09-28T08:00:00Z") }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomBackupRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportThenImportIntoAnEmptyDatabase_restoresEveryRow() = runTest {
        seedAWorkout()
        val before = counts()

        val json = exportedJson()

        // Simulate the reinstall: the same file, an empty database.
        database.clearAllTables()
        val summary = (repository.import(json) as DataResult.Success).data

        assertEquals(before, counts())
        assertEquals("the summary must account for what came back", before.total, summary.total)

        // The values that matter survived, not just the row counts.
        val set = database.backupDao().allSets().single()
        assertEquals(100_000L, set.weightGrams)
        assertEquals(5, set.reps)
        assertEquals(SetType.WARMUP, set.setType)
    }

    @Test
    fun importingTheSameFileTwice_addsNothingTheSecondTime() = runTest {
        seedAWorkout()
        val json = exportedJson()

        // The database already holds everything, as it would after a re-import.
        val second = (repository.import(json) as DataResult.Success).data

        assertTrue("a duplicate import must be a no-op, not a doubled log", second.wasAlreadyComplete)
        assertEquals(0, second.total)
        assertEquals(1, database.backupDao().allSessions().size)
        assertEquals(1, database.backupDao().allSets().size)
    }

    @Test
    fun aMalformedFile_isRejectedWithoutTouchingTheData() = runTest {
        seedAWorkout()
        val before = counts()

        val result = repository.import("not a backup at all")

        val error = (result as DataResult.Failure).error
        assertTrue("a bad file should be Invalid, not a storage error", error is DataError.Invalid)
        assertNotNull((error as DataError.Invalid).message)
        assertEquals("a refused import must leave the database untouched", before, counts())
    }

    @Test
    fun softDeletedRowsAreCarried_notDropped() = runTest {
        // Dropping them would restore into a database that differs from the original.
        database.exerciseDao().insertAll(listOf(seedExercise()))
        database.exerciseDao().softDelete("back-squat", deletedAt = 999L)

        val json = exportedJson()
        database.clearAllTables()
        repository.import(json)

        val restored = database.backupDao().allExercises().single()
        assertEquals("the soft delete is data too", 999L, restored.deletedAt)
    }

    private suspend fun exportedJson(): String =
        (repository.export() as DataResult.Success).data

    private suspend fun counts(): Counts = Counts(
        exercises = database.backupDao().allExercises().size,
        sessions = database.backupDao().allSessions().size,
        sessionExercises = database.backupDao().allSessionExercises().size,
        sets = database.backupDao().allSets().size,
    )

    private data class Counts(
        val exercises: Int,
        val sessions: Int,
        val sessionExercises: Int,
        val sets: Int,
    ) {
        val total: Int get() = exercises + sessions + sessionExercises + sets
    }

    private suspend fun seedAWorkout() {
        val exerciseDao = database.exerciseDao()
        val workoutDao = database.workoutDao()

        exerciseDao.insertAll(listOf(seedExercise()))

        val session = workoutDao.findOrCreateActiveSession(id = "session-1", now = 1_000L)
        val sessionExerciseId = "se-1"
        workoutDao.insertSessionExercise(
            com.example.androidapp.data.local.SessionExerciseEntity(
                id = sessionExerciseId,
                sessionId = session.id,
                exerciseId = "back-squat",
                position = 0,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deletedAt = null,
            ),
        )
        workoutDao.insertSet(
            com.example.androidapp.data.local.SetEntryEntity(
                id = "set-1",
                sessionExerciseId = sessionExerciseId,
                setIndex = 0,
                reps = 5,
                weightGrams = 100_000L,
                setType = SetType.WARMUP,
                completedAt = 2_000L,
                createdAt = 2_000L,
                updatedAt = 2_000L,
                deletedAt = null,
            ),
        )
    }

    private fun seedExercise() = ExerciseEntity(
        id = "back-squat",
        name = "Back Squat",
        primaryMuscle = MuscleGroup.QUADS,
        secondaryMuscles = listOf(MuscleGroup.GLUTES),
        equipment = Equipment.BARBELL,
        movementPattern = MovementPattern.SQUAT,
        isCustom = false,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
    )
}
