package com.example.androidapp.data

import com.example.androidapp.platform.CrashLog
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.ProgramDeloadEntity
import com.example.androidapp.data.local.ProgramEntity
import com.example.androidapp.data.local.ProgramSlotEntity
import com.example.androidapp.data.local.ProgramSlotExerciseEntity
import com.example.androidapp.data.local.ProgramSlotSetEntity
import com.example.androidapp.data.local.TemplateEntity
import com.example.androidapp.data.local.TemplateExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.ZoneOffsetSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.platform.CrashLogStore
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /** Kept so a clear can be asserted against the diagnostics as well as the rows. */
    private lateinit var crashLogs: CrashLogStore

    /** The real settings, because goals are the one thing in the file that is not a database row. */
    private lateinit var settings: PreferencesSettingsRepository

    private val clock = TimeSource { Instant.parse("2026-09-28T08:00:00Z") }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        settings = PreferencesSettingsRepository(ApplicationProvider.getApplicationContext())
        repository = RoomBackupRepository(
            database = database,
            timeSource = clock,
            settings = settings,
            // Nothing is recorded here, but the export path has to be built the
            // same way the app builds it.
            crashLogStore = CrashLogStore(Files.createTempDirectory("crash-logs").toFile()).also {
                crashLogs = it
            },
        )
        workouts = RoomWorkoutRepository(database, clock, ZoneOffsetSource { 0 })
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
        assertEquals("the assistance survived too", 20_000L, set.assistanceGrams)
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

        workouts.updateSet(setId, reps = 20, weightGrams = 100_000L, rpeHalves = null, note = null)

        repository.import(json)

        assertEquals(
            "the local edit must survive an import",
            20,
            database.backupDao().allSets().single().reps,
        )
    }

    @Test
    fun metricTargets_comeBackFromTheFile_butOneAlreadyHereIsLeftAlone() = runTest {
        // Goals are settings rather than rows, so they are the one thing the database round trip
        // cannot prove — and they were absent from every export, so a restore dropped them (N39).
        val restoredGoal = "body.weight"
        val keptGoal = "workout.rpe"
        settings.setGoal(restoredGoal, 82.0)
        settings.setGoal(keptGoal, 7.0)
        val json = exportedJson()

        // The reinstall: one target is gone locally, and another was changed after the export.
        settings.setGoal(restoredGoal, null)
        settings.setGoal(keptGoal, 9.5)

        repository.import(json)

        val goals = settings.observeGoals().first()
        assertEquals("a target the file had and the device did not", 82.0, goals[restoredGoal]!!, 0.0001)
        assertEquals("a target changed locally is newer than the file", 9.5, goals[keptGoal]!!, 0.0001)
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
        templateSets = database.backupDao().allTemplateSets().size,
    )

    private data class Counts(
        val exercises: Int,
        val sessions: Int,
        val sessionExercises: Int,
        val sets: Int,
        val templates: Int,
        val templateExercises: Int,
        val templateSets: Int,
    ) {
        val total: Int
            get() = exercises + sessions + sessionExercises + sets + templates +
                templateExercises + templateSets
    }

    private suspend fun seedAWorkout() {
        val exerciseDao = database.exerciseDao()
        val workoutDao = database.workoutDao()

        exerciseDao.insertAll(listOf(seedExercise()))

        val session = workoutDao
            .findOrCreateActiveSession(id = "session-1", now = 1_000L, zoneOffsetMinutes = 0)
            .session
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
                // The plan's rest and cue, and the plan's sets: N14's table is the one
                // most easily forgotten, because a plan that loses its sets still
                // restores as a template with the right exercises in it.
                restSeconds = 180,
                techniqueNote = "Slow descent",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                deletedAt = null,
            ),
        )
        templateDao.insertTemplateSet(
            com.example.androidapp.data.local.TemplateSetEntity(
                id = "template-set-1",
                templateExerciseId = "template-exercise-1",
                setIndex = 0,
                role = SetType.TOP_SET,
                // An assisted plan target: the column the codec must name (N15), and
                // null weight beside it, because a plan's load is one number.
                targetWeightGrams = null,
                targetAssistanceGrams = 20_000L,
                targetRepsMin = 1,
                targetRepsMax = 2,
                targetRpeHalves = 18,
                note = "grind",
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
                assistanceGrams = 20_000L,
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

    @Test
    fun aPlanSurvivesTheRoundTrip_withItsSetsRestAndCue() = runTest {
        // The reason this exists: the codec is hand-written, so a table or column the
        // DTO does not name is dropped on export and lost on restore, in silence
        // (ROADMAP N14 — the same trap N9's location was).
        seedAWorkout()
        val before = counts()

        val json = exportedJson()
        database.clearAllTables()
        repository.import(json)

        assertEquals(before, counts())

        val sets = database.backupDao().allTemplateSets()
        assertEquals(1, sets.size)
        val set = sets.single()
        assertEquals(SetType.TOP_SET, set.role)
        assertNull("an assisted target has no weight of its own", set.targetWeightGrams)
        assertEquals(20_000L, set.targetAssistanceGrams)
        assertEquals(1, set.targetRepsMin)
        assertEquals(2, set.targetRepsMax)
        assertEquals(18, set.targetRpeHalves)
        assertEquals("grind", set.note)

        val exercise = database.backupDao().allTemplateExercises().single()
        assertEquals(180, exercise.restSeconds)
        assertEquals("Slow descent", exercise.techniqueNote)
    }

    @Test
    fun aSlotPrescription_survivesTheRoundTrip() = runTest {
        // ROADMAP P3.8: what a slot prescribes is authored setup, and the codec is hand-written —
        // so a table it is not told about is dropped on export and every slot silently returns to
        // the template's targets. The percentage is the target only this table can carry.
        database.exerciseDao().insertAll(listOf(seedExercise()))
        database.templateDao().insertTemplate(
            TemplateEntity(id = "t1", name = "Heavy lower", createdAt = 1L, updatedAt = 1L, deletedAt = null),
        )
        database.programDao().insertProgram(
            ProgramEntity(
                id = "p1",
                name = "Upper/Lower",
                isActive = true,
                position = 0,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
            ),
        )
        database.programDao().insertSlot(
            ProgramSlotEntity(
                id = "slot1",
                programId = "p1",
                templateId = "t1",
                position = 0,
                weekday = DayOfWeek.MONDAY,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
            ),
        )
        // A deload week (ROADMAP P3.10): authored setup the app cannot recompute, so losing it
        // would restore every backed-off week as a miss.
        database.programDeloadDao().insertDeload(
            ProgramDeloadEntity(
                id = "deload1",
                programId = "p1",
                weekStart = 20_305L,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
            ),
        )
        database.programPrescriptionDao().insertSlotExercise(
            ProgramSlotExerciseEntity(
                id = "pse1",
                slotId = "slot1",
                exerciseId = "back-squat",
                restSeconds = 150,
                techniqueNote = "brace hard",
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
            ),
        )
        database.programPrescriptionDao().insertSlotSet(
            ProgramSlotSetEntity(
                id = "pss1",
                slotExerciseId = "pse1",
                setIndex = 0,
                role = SetType.TOP_SET,
                targetAssistanceGrams = 20_000L,
                targetRepsMin = 1,
                targetRepsMax = 2,
                targetRpeHalves = 18,
                targetPercentOf1Rm = 85,
                note = "grind",
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
            ),
        )

        val json = exportedJson()
        database.clearAllTables()
        // The import must *succeed*, not roll back: a slot names a template through a foreign key,
        // and a restore that inserted the program tables before the templates failed the whole
        // transaction and left an empty database behind. Asserted here rather than trusted to the
        // reads below, so the failure is the import and not a puzzling empty list.
        val imported = repository.import(json)
        assertTrue("a restore into an empty database must not roll back: $imported", imported is DataResult.Success)

        val exercise = database.programPrescriptionDao().observeSlotExercises("slot1").first().single()
        assertEquals(150, exercise.restSeconds)
        assertEquals("brace hard", exercise.techniqueNote)

        val set = database.programPrescriptionDao().observeSlotSets("slot1").first().single()
        assertEquals(SetType.TOP_SET, set.role)
        assertEquals(20_000L, set.targetAssistanceGrams)
        assertEquals(18, set.targetRpeHalves)
        assertEquals("the one target a template's planned set cannot carry", 85, set.targetPercentOf1Rm)
        assertEquals("grind", set.note)

        val deload = database.programBackupDao().allProgramDeloads().single()
        assertEquals("the deloaded week survives", 20_305L, deload.weekStart)
        assertEquals("p1", deload.programId)
    }

    @Test
    fun clearingEverything_leavesNoUserRows_andPutsTheLibraryBack() = runTest {
        // ROADMAP N18's trap, in the form the roadmap asks for it: the seeder runs on
        // *open*, so a clear that only emptied tables would leave the library empty until
        // the process restarted — a clean start that looks broken.
        seedAWorkout()
        settings.setGoal("body.weight", 80.0)
        crashLogs.record(
            CrashLog(
                timestamp = 1_000L,
                exceptionClass = "java.lang.IllegalStateException",
                stackTrace = "at example",
                appVersion = "1.5",
                versionCode = 6,
                androidVersion = "36",
                deviceModel = "test",
            ),
        )
        val before = counts()
        assertTrue("the fixture must have something to clear", before.total > 0)

        val cleared = repository.clearAllUserData()

        assertTrue("clearing is a value, not a throw", cleared is DataResult.Success)
        val after = counts()
        assertEquals("sessions", 0, after.sessions)
        assertEquals("session exercises", 0, after.sessionExercises)
        assertEquals("sets", 0, after.sets)
        assertEquals("templates", 0, after.templates)
        assertEquals("template exercises", 0, after.templateExercises)
        assertEquals("planned sets", 0, after.templateSets)
        assertTrue(
            "the exercise library is app content and must come back, not stay empty",
            after.exercises >= SEEDED_LIBRARY_MINIMUM,
        )
        assertEquals("and the crash logs are diagnostics about what just went", 0, crashLogs.all().size)
        assertEquals(
            "a target is authored content rather than a display preference, so clearing takes it too",
            emptyMap<String, Double>(),
            settings.observeGoals().first(),
        )
    }

    @Test
    fun anExportTakenBeforeAClear_stillImports() = runTest {
        // The whole point of exporting first: platform backup is off, so the file is the
        // only thing that outlives the action — and it has to still work afterwards.
        seedAWorkout()
        val json = exportedJson()
        val before = counts()

        repository.clearAllUserData()
        val summary = (repository.import(json) as DataResult.Success).data

        val after = counts()
        assertEquals("the workouts come back", before.sessions, after.sessions)
        assertEquals(before.sessionExercises, after.sessionExercises)
        assertEquals(before.sets, after.sets)
        assertEquals(before.templates, after.templates)
        assertEquals(before.templateExercises, after.templateExercises)
        assertEquals(before.templateSets, after.templateSets)
        assertTrue("and the summary accounts for them", summary.total > 0)
    }

    private companion object {
        /**
         * The shipped library's size, as a floor rather than an exact count: the seeder
         * tops up, so a later release adding an exercise must not break this.
         */
        const val SEEDED_LIBRARY_MINIMUM = 30
    }
}
