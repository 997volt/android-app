package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.TemplateEntity
import com.example.androidapp.data.local.TemplateExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.platform.CrashLogStore
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.flow.first
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

    /** The real delete path, so the test exercises what the UI does. */
    private lateinit var workouts: RoomWorkoutRepository

    private val clock = TimeSource { Instant.parse("2026-09-28T08:00:00Z") }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomBackupRepository(
            database = database,
            timeSource = clock,
            // Nothing is recorded here, but the export path has to be built the
            // same way the app builds it.
            crashLogStore = CrashLogStore(Files.createTempDirectory("crash-logs").toFile()),
        )
        workouts = RoomWorkoutRepository(database, clock)
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

        // What the ratings and N9's location look like after a restore.
        val exercise = database.backupDao().allSessionExercises().single()
        assertEquals(8, exercise.muscleFeel)
        assertEquals(4, exercise.jointPain)
        assertEquals("left shoulder", exercise.jointPainNote)
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

    @Test
    fun importingAfterDeletingAWorkout_bringsItBack() = runTest {
        // The scenario a user actually has: export, delete something by mistake,
        // import to undo it. The old additive-only import could not do this,
        // because a soft delete leaves the row (and its id) in the database, so
        // INSERT OR IGNORE skipped every row it was meant to restore.
        seedAWorkout()
        val json = exportedJson()
        val sessionId = database.backupDao().allSessions().single().id

        workouts.deleteSession(sessionId)
        assertTrue(
            "the workout should be gone from history first",
            database.workoutDao().observeHistory().first().isEmpty(),
        )

        val summary = (repository.import(json) as DataResult.Success).data

        assertTrue("the import reported nothing to do: $summary", summary.total > 0)
        assertEquals(
            "the deleted workout should be back in history",
            1,
            database.workoutDao().observeHistory().first().size,
        )
    }

    @Test
    fun importing_doesNotOverwriteARowThatIsStillLive() = runTest {
        // The other half of "bring back what is gone; never overwrite what is
        // there": a local edit may be newer than the file, and clobbering it would
        // lose work done after the export.
        seedAWorkout()
        val json = exportedJson()
        val setId = database.backupDao().allSets().single().id

        workouts.updateSet(setId, reps = 20, weightGrams = 100_000L, rpe = null, note = null)

        repository.import(json)

        assertEquals(
            "the local edit must survive an import",
            20,
            database.backupDao().allSets().single().reps,
        )
    }

    private suspend fun exportedJson(): String =
        (repository.export() as DataResult.Success).data

    private suspend fun counts(): Counts = Counts(
        exercises = database.backupDao().allExercises().size,
        sessions = database.backupDao().allSessions().size,
        sessionExercises = database.backupDao().allSessionExercises().size,
        sets = database.backupDao().allSets().size,
        templates = database.backupDao().allTemplates().size,
        templateExercises = database.backupDao().allTemplateExercises().size,
    )

    private data class Counts(
        val exercises: Int,
        val sessions: Int,
        val sessionExercises: Int,
        val sets: Int,
        val templates: Int,
        val templateExercises: Int,
    ) {
        val total: Int
            get() = exercises + sessions + sessionExercises + sets + templates + templateExercises
    }

    private suspend fun seedAWorkout() {
        val exerciseDao = database.exerciseDao()
        val workoutDao = database.workoutDao()

        exerciseDao.insertAll(listOf(seedExercise()))

        val session = workoutDao.findOrCreateActiveSession(id = "session-1", now = 1_000L).session
        val sessionExerciseId = "se-1"
        workoutDao.insertSessionExercise(
            com.example.androidapp.data.local.SessionExerciseEntity(
                id = sessionExerciseId,
                sessionId = session.id,
                exerciseId = "back-squat",
                position = 0,
                finishedAt = 2_000L,
                // The ratings and N9's location are columns the hand-written codec
                // has to name explicitly: one missing from the DTO is dropped by
                // export and lost on restore, silently. The round trip is what
                // catches that, so it seeds all three.
                muscleFeel = 8,
                jointPain = 4,
                jointPainNote = "left shoulder",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deletedAt = null,
            ),
        )
        // A template too (N3): a plan is data the user made, and the export is the
        // only escape hatch, so losing it on restore would be losing work.
        val templateDao = database.templateDao()
        templateDao.insertTemplate(
            TemplateEntity(
                id = "template-1",
                name = "Push day",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deletedAt = null,
            ),
        )
        templateDao.insertTemplateExercise(
            TemplateExerciseEntity(
                id = "template-exercise-1",
                templateId = "template-1",
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
        // Finish it: history only holds finished sessions, so without this the
        // workout is never in the history the restore is asserted against.
        workoutDao.markFinished(id = session.id, at = 2_000L)
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
