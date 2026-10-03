package com.example.androidapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the committed schema baseline and every migration against it
 * (ROADMAP F5).
 *
 * These tests are the reason migrations are safe to write at all: each one runs
 * the real migration over a database created from the *previous* exported
 * schema, then validates the result against the next one. A column typo or a
 * forgotten index fails here rather than on a user's phone.
 *
 * `app/schemas/` is wired in as instrumented assets by `app/build.gradle.kts`.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkoutDatabase::class.java,
    )

    @Test
    fun version1Schema_matchesTheCommittedBaseline() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    @Test
    fun migration1To2_addsSessionTables_andKeepsTheExerciseLibrary() {
        // A v1 database with a real library row in it.
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES
                    ('back-squat', 'Back Squat', 'QUADS', '', 'BARBELL',
                     'SQUAT', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // The migration is purely additive, so the library must survive it. This
        // is the assertion that would have caught a careless DROP/recreate.
        migrated.query("SELECT COUNT(*) FROM exercises").use { cursor ->
            cursor.moveToFirst()
            assertEquals("library rows must survive the upgrade", 1, cursor.getInt(0))
        }

        migrated.query("SELECT COUNT(*) FROM workout_sessions").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }

        migrated.close()
    }

    @Test
    fun migration2To3_addsSetEntriesAndTheRestColumn() {
        // Start from a real v2 database so the ALTER runs against a table that
        // already has rows — the case a fresh-install test would never cover.
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO workout_sessions (id, startedAt, finishedAt, notes, createdAt, updatedAt, deletedAt) VALUES ('s1', 1, NULL, NULL, 1, 1, NULL)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        migrated.query("SELECT COUNT(*) FROM set_entries").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        // The pre-existing session survives, and its new column reads as "not resting".
        migrated.query("SELECT restEndsAt FROM workout_sessions WHERE id = 's1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.count)
            assertTrue("restEndsAt must default to null", cursor.isNull(0))
        }

        migrated.close()
    }

    @Test
    fun migration3To4_addsRestAndTechniqueNote_leavingThemUnset() {
        // Start from a v3 database holding a library row: N5's ALTERs have to run
        // against a table that already has data, which a fresh install never covers.
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES
                    ('back-squat', 'Back Squat', 'QUADS', '', 'BARBELL',
                     'SQUAT', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        migrated.query(
            "SELECT restSeconds, techniqueNote FROM exercises WHERE id = 'back-squat'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("the existing row must survive", 1, cursor.count)
            // Unset is the state that means "use the app default", so a backfilled
            // value here would silently change every existing exercise.
            assertTrue("an unset rest must read as null", cursor.isNull(0))
            assertTrue("an unset cue must read as null", cursor.isNull(1))
        }

        migrated.close()
    }

    @Test
    fun migration4To5_addsTheReadinessNote_leavingItUnset() {
        // A v4 database with a finished session in it: the ALTER has to run against
        // a table that already holds history (ROADMAP N4).
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                """
                INSERT INTO workout_sessions
                    (id, startedAt, finishedAt, notes, restEndsAt, createdAt, updatedAt, deletedAt)
                VALUES
                    ('s1', 1, 2, NULL, NULL, 1, 2, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        migrated.query("SELECT readinessNote FROM workout_sessions WHERE id = 's1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("the existing session must survive", 1, cursor.count)
            // Nothing is backfilled: an old workout simply has no note.
            assertTrue("readinessNote must default to null", cursor.isNull(0))
        }

        migrated.close()
    }

    @Test
    fun migration5To6_addsRpeAndComment_leavingThemUnset() {
        // A v5 database with a real logged set in it (ROADMAP N6). Every parent row
        // is inserted too, so the ALTERs run against a table that already holds data.
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES
                    ('back-squat', 'Back Squat', 'QUADS', '', 'BARBELL',
                     'SQUAT', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sessions
                    (id, startedAt, finishedAt, notes, restEndsAt, readinessNote,
                     createdAt, updatedAt, deletedAt)
                VALUES ('s1', 1, 2, NULL, NULL, NULL, 1, 2, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_exercises
                    (id, sessionId, exerciseId, position, createdAt, updatedAt, deletedAt)
                VALUES ('se1', 's1', 'back-squat', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO set_entries
                    (id, sessionExerciseId, setIndex, reps, weightGrams, setType,
                     completedAt, createdAt, updatedAt, deletedAt)
                VALUES ('set1', 'se1', 0, 5, 100000, 'NORMAL', NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        migrated.query("SELECT rpe, note FROM set_entries WHERE id = 'set1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("the existing set must survive", 1, cursor.count)
            // Unset is what the one-tap log path keeps writing, so nothing is
            // backfilled and an old set reads as "no RPE, no comment".
            assertTrue("rpe must default to null", cursor.isNull(0))
            assertTrue("note must default to null", cursor.isNull(1))
        }

        migrated.close()
    }

    @Test
    fun migration6To7_addsFinishedAt_leavingItUnset() {
        // A v6 database with a real session exercise in it (ROADMAP N7).
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES
                    ('back-squat', 'Back Squat', 'QUADS', '', 'BARBELL',
                     'SQUAT', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sessions
                    (id, startedAt, finishedAt, notes, restEndsAt, readinessNote,
                     createdAt, updatedAt, deletedAt)
                VALUES ('s1', 1, NULL, NULL, NULL, NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_exercises
                    (id, sessionId, exerciseId, position, createdAt, updatedAt, deletedAt)
                VALUES ('se1', 's1', 'back-squat', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        migrated.query("SELECT finishedAt FROM session_exercises WHERE id = 'se1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("the existing exercise must survive", 1, cursor.count)
            // Unset means "not done": nothing is backfilled, and no past set moves.
            assertTrue("finishedAt must default to null", cursor.isNull(0))
        }

        migrated.close()
    }

    @Test
    fun migration7To8_addsTheFeelRatings_leavingThemUnset() {
        // A v7 database with a real session exercise in it (ROADMAP N8).
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES
                    ('back-squat', 'Back Squat', 'QUADS', '', 'BARBELL',
                     'SQUAT', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sessions
                    (id, startedAt, finishedAt, notes, restEndsAt, readinessNote,
                     createdAt, updatedAt, deletedAt)
                VALUES ('s1', 1, 2, NULL, NULL, NULL, 1, 2, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_exercises
                    (id, sessionId, exerciseId, position, finishedAt,
                     createdAt, updatedAt, deletedAt)
                VALUES ('se1', 's1', 'back-squat', 0, 5, 1, 5, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        migrated.query(
            "SELECT muscleFeel, jointPain, finishedAt FROM session_exercises WHERE id = 'se1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("the existing exercise must survive", 1, cursor.count)
            // Unrated, not zeroed: nothing is backfilled.
            assertTrue("muscleFeel must default to null", cursor.isNull(0))
            assertTrue("jointPain must default to null", cursor.isNull(1))
            assertEquals("the done state is untouched", 5L, cursor.getLong(2))
        }

        migrated.close()
    }

    @Test
    fun migration8To9_addsTheTemplateTables_leavingExistingWorkoutsAlone() {
        // A v8 database with a real workout in it: the upgrade must add tables
        // without touching what the previous version already wrote (ROADMAP N3).
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES
                    ('back-squat', 'Back Squat', 'QUADS', '', 'BARBELL',
                     'SQUAT', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sessions
                    (id, startedAt, finishedAt, notes, restEndsAt, readinessNote,
                     createdAt, updatedAt, deletedAt)
                VALUES ('s1', 1, NULL, NULL, NULL, NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)

        // The new tables exist and are empty...
        migrated.query("SELECT COUNT(*) FROM templates").use { cursor ->
            cursor.moveToFirst()
            assertEquals("no template is invented by the upgrade", 0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM template_exercises").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }

        // ...and the workout that was already there is untouched.
        migrated.query("SELECT id, finishedAt FROM workout_sessions").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.count)
            assertEquals("s1", cursor.getString(0))
            assertTrue("the open session must stay open", cursor.isNull(1))
        }

        migrated.close()
    }

    @Test
    fun migration9To10_addsTheJointPainLocation_leavingItUnset() {
        // A v9 database with a rating already recorded (ROADMAP N9).
        helper.createDatabase(TEST_DB, 9).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES
                    ('back-squat', 'Back Squat', 'QUADS', '', 'BARBELL',
                     'SQUAT', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sessions
                    (id, startedAt, finishedAt, notes, restEndsAt, readinessNote,
                     createdAt, updatedAt, deletedAt)
                VALUES ('s1', 1, 2, NULL, NULL, NULL, 1, 2, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_exercises
                    (id, sessionId, exerciseId, position, finishedAt, muscleFeel,
                     jointPain, createdAt, updatedAt, deletedAt)
                VALUES ('se1', 's1', 'back-squat', 0, NULL, 7, 4, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        migrated.query(
            "SELECT jointPainNote, muscleFeel, jointPain FROM session_exercises WHERE id = 'se1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.count)
            assertTrue("no location is invented by the upgrade", cursor.isNull(0))
            // The ratings that were already there are untouched.
            assertEquals(7, cursor.getInt(1))
            assertEquals(4, cursor.getInt(2))
        }

        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @Test
    fun migration10To11_addsThePlannedSets_leavingAtemplateAlone() {
        // A v10 database with a real template in it: a plan is the user's work, and
        // the upgrade must not disturb one that was already written (ROADMAP N14).
        helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES
                    ('back-squat', 'Back Squat', 'QUADS', '', 'BARBELL',
                     'SQUAT', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO templates (id, name, createdAt, updatedAt, deletedAt)
                VALUES ('t1', 'Heavy lower', 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO template_exercises
                    (id, templateId, exerciseId, position, createdAt, updatedAt, deletedAt)
                VALUES ('te1', 't1', 'back-squat', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)

        // The new table exists and is empty...
        migrated.query("SELECT COUNT(*) FROM template_sets").use { cursor ->
            cursor.moveToFirst()
            assertEquals("no planned set is invented by the upgrade", 0, cursor.getInt(0))
        }

        // ...the plan's two new columns are null rather than zeroed, so the exercise
        // still falls back to the library's rest and cue (N5)...
        migrated.query(
            "SELECT restSeconds, techniqueNote FROM template_exercises WHERE id = 'te1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertTrue("an unset rest must stay unset", cursor.isNull(0))
            assertTrue("an unset cue must stay unset", cursor.isNull(1))
        }

        // ...and the template that was already there is untouched.
        migrated.query("SELECT id, name FROM templates").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.count)
            assertEquals("Heavy lower", cursor.getString(1))
        }

        migrated.close()
    }

    @Test
    fun migration11To12_addsThePlansRestAndCue_leavingThemLibraryOwned() {
        // A v11 database with a real session: the new columns are what a *plan*
        // prescribes, so they must arrive unset and let the library's show through
        // (ROADMAP N14).
        helper.createDatabase(TEST_DB, 11).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt,
                     restSeconds, techniqueNote)
                VALUES
                    ('back-squat', 'Back Squat', 'QUADS', '', 'BARBELL',
                     'SQUAT', 0, 1, 1, NULL, 90, 'Brace hard')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sessions
                    (id, startedAt, finishedAt, notes, restEndsAt, readinessNote,
                     createdAt, updatedAt, deletedAt)
                VALUES ('s1', 1, NULL, NULL, NULL, NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_exercises
                    (id, sessionId, exerciseId, position, finishedAt, muscleFeel,
                     jointPain, jointPainNote, createdAt, updatedAt, deletedAt)
                VALUES ('se1', 's1', 'back-squat', 0, NULL, NULL, NULL, NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12)

        migrated.query(
            "SELECT restSeconds, techniqueNote FROM session_exercises WHERE id = 'se1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertTrue("the plan prescribed nothing, so the column is unset", cursor.isNull(0))
            assertTrue(cursor.isNull(1))
        }
        // The library's own values are untouched, which is what the session falls back to.
        migrated.query(
            "SELECT restSeconds, techniqueNote FROM exercises WHERE id = 'back-squat'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(90, cursor.getInt(0))
            assertEquals("Brace hard", cursor.getString(1))
        }

        migrated.close()
    }

    @Test
    fun migration12To13_addsAssistance_defaultingToNone() {
        // A v12 database with a real set in it: every set already recorded took no
        // assistance, so the column must arrive as 0 rather than as null or a value
        // that would have to be guessed (ROADMAP N15).
        helper.createDatabase(TEST_DB, 12).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES
                    ('assisted-pull-up', 'Assisted Pull-Up', 'BACK', '', 'MACHINE',
                     'PULL', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sessions
                    (id, startedAt, finishedAt, notes, restEndsAt, readinessNote,
                     createdAt, updatedAt, deletedAt)
                VALUES ('s1', 1, NULL, NULL, NULL, NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_exercises
                    (id, sessionId, exerciseId, position, finishedAt, muscleFeel,
                     jointPain, jointPainNote, createdAt, updatedAt, deletedAt)
                VALUES ('se1', 's1', 'assisted-pull-up', 0, NULL, NULL, NULL, NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO set_entries
                    (id, sessionExerciseId, setIndex, reps, weightGrams, setType,
                     rpe, note, completedAt, createdAt, updatedAt, deletedAt)
                VALUES ('set1', 'se1', 0, 8, 0, 'NORMAL', NULL, NULL, 1, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        migrated.query("SELECT assistanceGrams, weightGrams FROM set_entries").use { cursor ->
            cursor.moveToFirst()
            assertEquals("an old set took no assistance", 0, cursor.getInt(0))
            assertEquals("and its weight is untouched", 0, cursor.getInt(1))
        }
        // The plan's target is nullable: a plan may say nothing about assistance.
        migrated.query("SELECT COUNT(*) FROM template_sets").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }

        migrated.close()
    }

    @Test
    fun migration13To14_carriesRpeIntoHalves() {
        // The unit changed, so the column was renamed rather than re-used: an `rpe`
        // holding 19 would read as nineteen points to anyone who did not know. Values
        // are doubled on the way across, so an 8 recorded before this is still 8.0
        // (ROADMAP N6, extended for 9.5).
        helper.createDatabase(TEST_DB, 13).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id, name, primaryMuscle, secondaryMuscles, equipment,
                     movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES
                    ('back-squat', 'Back Squat', 'QUADS', '', 'BARBELL',
                     'SQUAT', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sessions
                    (id, startedAt, finishedAt, notes, restEndsAt, readinessNote,
                     createdAt, updatedAt, deletedAt)
                VALUES ('s1', 1, NULL, NULL, NULL, NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_exercises
                    (id, sessionId, exerciseId, position, finishedAt, muscleFeel,
                     jointPain, jointPainNote, createdAt, updatedAt, deletedAt)
                VALUES ('se1', 's1', 'back-squat', 0, NULL, NULL, NULL, NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO set_entries
                    (id, sessionExerciseId, setIndex, reps, weightGrams, assistanceGrams,
                     setType, rpe, note, completedAt, createdAt, updatedAt, deletedAt)
                VALUES ('set1', 'se1', 0, 5, 100000, 0, 'NORMAL', 8, 'heavy', 1, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO set_entries
                    (id, sessionExerciseId, setIndex, reps, weightGrams, assistanceGrams,
                     setType, rpe, note, completedAt, createdAt, updatedAt, deletedAt)
                VALUES ('set2', 'se1', 1, 5, 100000, 0, 'NORMAL', NULL, NULL, 1, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO templates (id, name, createdAt, updatedAt, deletedAt)
                VALUES ('t1', 'Legs', 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO template_exercises
                    (id, templateId, exerciseId, position, createdAt, updatedAt, deletedAt)
                VALUES ('te1', 't1', 'back-squat', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO template_sets
                    (id, templateExerciseId, setIndex, role, targetWeightGrams,
                     targetAssistanceGrams, targetRepsMin, targetRepsMax, targetRpe,
                     note, createdAt, updatedAt, deletedAt)
                VALUES ('ts1', 'te1', 0, 'NORMAL', 100000, NULL, 3, 3, 7, NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // 8 points is 16 halves; the rest of the row came across untouched.
        migrated.query(
            "SELECT rpeHalves, note, weightGrams FROM set_entries WHERE id = 'set1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(16, cursor.getInt(0))
            assertEquals("heavy", cursor.getString(1))
            assertEquals(100_000L, cursor.getLong(2))
        }
        // A set with no RPE stays unrated rather than becoming 0 halves.
        migrated.query("SELECT rpeHalves FROM set_entries WHERE id = 'set2'").use { cursor ->
            cursor.moveToFirst()
            assertTrue("no RPE stays no RPE", cursor.isNull(0))
        }
        migrated.query("SELECT targetRpeHalves FROM template_sets").use { cursor ->
            cursor.moveToFirst()
            assertEquals(14, cursor.getInt(0))
        }

        migrated.close()
    }

    @Test
    fun migration14To15_addsTheWeekday_leavingPlansUncheduled() {
        // An existing plan has no day until it is given one, which is exactly what it
        // was before this column existed (ROADMAP N16).
        helper.createDatabase(TEST_DB, 14).apply {
            execSQL(
                """
                INSERT INTO templates (id, name, createdAt, updatedAt, deletedAt)
                VALUES ('t1', 'Legs', 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 15, true, MIGRATION_14_15)

        migrated.query("SELECT name, weekday FROM templates").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Legs", cursor.getString(0))
            assertTrue("a plan with no day stays unscheduled", cursor.isNull(1))
        }

        migrated.close()
    }

    @Test
    fun migration15To16_groupsSessionsAndPlans_withoutTouchingTheirRows() {
        // ROADMAP B16, and the reason this test exists at all: the migration was *amended* after
        // it had already run on development devices — a second column joined the same version —
        // so what needs proving is that an upgrade keeps the rows already in the tables AND
        // arrives with both columns. The 1→16 chain in MigrationsTest cannot check the first half.
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                """
                INSERT INTO workout_sessions (id, startedAt, createdAt, updatedAt, deletedAt)
                VALUES ('s1', 100, 100, 100, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO exercises (id, name, primaryMuscle, secondaryMuscles, equipment,
                    movementPattern, isCustom, createdAt, updatedAt, deletedAt)
                VALUES ('e1', 'Back Squat', 'QUADS', '', 'BARBELL', 'SQUAT', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO session_exercises (id, sessionId, exerciseId, position, createdAt,
                    updatedAt, deletedAt)
                VALUES ('se1', 's1', 'e1', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO templates (id, name, createdAt, updatedAt, deletedAt)
                VALUES ('t1', 'Legs', 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO template_exercises (id, templateId, exerciseId, position, createdAt,
                    updatedAt, deletedAt)
                VALUES ('te1', 't1', 'e1', 0, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16)

        // The rows are still there, and ungrouped — which is what every exercise was before N24.
        migrated.query("SELECT id, supersetGroup FROM session_exercises").use { cursor ->
            cursor.moveToFirst()
            assertEquals("se1", cursor.getString(0))
            assertTrue("an existing session exercise is ungrouped", cursor.isNull(1))
        }
        migrated.query("SELECT id, supersetGroup FROM template_exercises").use { cursor ->
            cursor.moveToFirst()
            assertEquals("te1", cursor.getString(0))
            assertTrue("and so is an existing planned exercise", cursor.isNull(1))
        }

        migrated.close()
    }

    @Test
    fun migration16To17_keepsTheRows_andLeavesTheirZoneUnknown() {
        // ROADMAP N25. Two things need proving, and the second is the one worth a test: an upgrade
        // keeps the sessions already in the table, and the new column arrives **null** rather than
        // backfilled. A zone that was never captured cannot be reconstructed, and stamping today's
        // onto old rows would look like knowledge the row does not have.
        helper.createDatabase(TEST_DB, 16).apply {
            execSQL(
                """
                INSERT INTO workout_sessions (id, startedAt, finishedAt, createdAt, updatedAt, deletedAt)
                VALUES ('s1', 100, 200, 100, 200, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sessions (id, startedAt, createdAt, updatedAt, deletedAt)
                VALUES ('s2', 300, 300, 300, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)

        migrated.query("SELECT id, zoneOffsetMinutes FROM workout_sessions ORDER BY id").use { cursor ->
            assertTrue("the finished session survived", cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertTrue("its zone is unknown, not invented", cursor.isNull(1))
            assertTrue("and so did the open one", cursor.moveToNext())
            assertEquals("s2", cursor.getString(0))
            assertTrue("with no zone either", cursor.isNull(1))
            assertFalse("exactly the two rows that were there", cursor.moveToNext())
        }

        migrated.close()
    }

    @Test
    fun migration17To18_addsMeasurements_withoutTouchingTheRowsAlreadyThere() {
        // ROADMAP N32: a new table is the easiest migration to get wrong in the quiet direction — an
        // upgrade that dropped what was there would look identical to one that worked, until someone
        // opened a workout.
        helper.createDatabase(TEST_DB, 17).apply {
            execSQL(
                """
                INSERT INTO workout_sessions (id, startedAt, finishedAt, createdAt, updatedAt, deletedAt)
                VALUES ('s1', 100, 200, 100, 200, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 18, true, MIGRATION_17_18)

        migrated.query("SELECT id FROM workout_sessions").use { cursor ->
            assertTrue("the workout already there survived", cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM measurements").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("and the new table is there, empty", 0, cursor.getInt(0))
        }

        migrated.close()
    }

    @Test
    fun migration18To19_addsTheStartTimeIndex_leavingTheRowsAlone() {
        // ROADMAP B46. An index is the migration with nothing to show for itself: it changes no column and no
        // row, so only a test can tell an upgrade that added it from one that did nothing. What matters is
        // that the rows survive and the index exists, because a query cannot report a missing index — it
        // just gets slower.
        helper.createDatabase(TEST_DB, 18).apply {
            execSQL(
                """
                INSERT INTO workout_sessions (id, startedAt, finishedAt, createdAt, updatedAt, deletedAt)
                VALUES ('s1', 100, 200, 100, 200, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 19, true, MIGRATION_18_19)

        migrated.query("SELECT id, startedAt FROM workout_sessions").use { cursor ->
            assertTrue("the workout already there survived", cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals(100L, cursor.getLong(1))
        }
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf("index_workout_sessions_startedAt"),
        ).use { cursor ->
            assertTrue("the index the range filter needs is there", cursor.moveToFirst())
        }

        migrated.close()
    }

    @Test
    fun migration19To20_addsPrograms_withoutInventingProvenance() {
        // ROADMAP P3.3. Three new tables and one additive column, and the column is the half
        // worth a test: a workout done before programs existed cannot be given a template it
        // was started from, and inventing one would make it settle an occurrence it never
        // touched.
        helper.createDatabase(TEST_DB, 19).apply {
            execSQL(
                """
                INSERT INTO workout_sessions (id, startedAt, finishedAt, zoneOffsetMinutes,
                    createdAt, updatedAt, deletedAt)
                VALUES ('s1', 100, 200, 540, 100, 200, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sessions (id, startedAt, createdAt, updatedAt, deletedAt)
                VALUES ('s2', 300, 300, 300, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20)

        // The sessions survive, and their provenance is unknown rather than manufactured.
        migrated.query(
            "SELECT id, zoneOffsetMinutes, templateId FROM workout_sessions ORDER BY id",
        ).use { cursor ->
            assertTrue("the finished session survived", cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals("its own zone is untouched", 540, cursor.getInt(1))
            assertTrue("and no template is invented for it", cursor.isNull(2))
            assertTrue("the open one survived too", cursor.moveToNext())
            assertEquals("s2", cursor.getString(0))
            assertTrue("with no provenance either", cursor.isNull(2))
            assertFalse("exactly the rows that were there", cursor.moveToNext())
        }

        // The three new tables exist and are empty.
        listOf("programs", "program_slots", "program_skips").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("no $table is invented by the upgrade", 0, cursor.getInt(0))
            }
        }

        migrated.close()
    }

    @Test
    fun migration20To21_givesProgramsAnOrder_keepingEveryActiveOne() {
        // ROADMAP P3.12. Two things need proving. The new column arrives with a usable order —
        // the rows there get their `rowid`, which is the only order they carry — and **both**
        // programs that were active stay active: the upgrade must not quietly deactivate one to
        // keep the old "at most one" rule true.
        helper.createDatabase(TEST_DB, 20).apply {
            execSQL(
                """
                INSERT INTO programs (id, name, isActive, createdAt, updatedAt, deletedAt)
                VALUES ('p1', 'Upper/Lower', 1, 100, 100, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO programs (id, name, isActive, createdAt, updatedAt, deletedAt)
                VALUES ('p2', 'Conditioning', 1, 200, 200, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 21, true, MIGRATION_20_21)

        migrated.query("SELECT id, isActive, position FROM programs ORDER BY position").use { cursor ->
            assertTrue("the first program survived", cursor.moveToFirst())
            assertEquals("p1", cursor.getString(0))
            assertEquals("its active flag is untouched", 1, cursor.getInt(1))
            assertEquals("and it keeps its place", 1L, cursor.getLong(2))
            assertTrue("and so did the second", cursor.moveToNext())
            assertEquals("p2", cursor.getString(0))
            assertEquals("which is still active too", 1, cursor.getInt(1))
            assertEquals(2L, cursor.getLong(2))
            assertFalse("exactly the two rows that were there", cursor.moveToNext())
        }

        migrated.close()
    }

    @Test
    fun migration21To22_addsSlotPrescriptions_withoutInventingOne() {
        // ROADMAP P3.8. Two new tables, and what matters is what they do *not* do: a slot that
        // prescribed nothing before the upgrade gets no rows, so the template's targets still
        // stand exactly as they did. The slot already there has to survive.
        helper.createDatabase(TEST_DB, 21).apply {
            execSQL(
                """
                INSERT INTO templates (id, name, createdAt, updatedAt, deletedAt)
                VALUES ('t1', 'Heavy lower', 100, 100, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO programs (id, name, isActive, position, createdAt, updatedAt, deletedAt)
                VALUES ('p1', 'Upper/Lower', 1, 1, 100, 100, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO program_slots
                    (id, programId, templateId, position, weekday, createdAt, updatedAt, deletedAt)
                VALUES ('slot1', 'p1', 't1', 0, 'MONDAY', 100, 100, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 22, true, MIGRATION_21_22)

        migrated.query("SELECT id, weekday FROM program_slots").use { cursor ->
            assertTrue("the slot already there survived", cursor.moveToFirst())
            assertEquals("slot1", cursor.getString(0))
            assertEquals("MONDAY", cursor.getString(1))
        }
        listOf("program_slot_exercises", "program_slot_sets").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("no $table is invented by the upgrade", 0, cursor.getInt(0))
            }
        }

        migrated.close()
    }

    @Test
    fun migration22To23_addsDeloads_withoutInventingOne() {
        // ROADMAP P3.10. A program with no deload gets no rows, which is exactly the state it was
        // in: every week counted, which is what an unmarked week means.
        helper.createDatabase(TEST_DB, 22).apply {
            execSQL(
                """
                INSERT INTO programs (id, name, isActive, position, createdAt, updatedAt, deletedAt)
                VALUES ('p1', 'Upper/Lower', 1, 1, 100, 100, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 23, true, MIGRATION_22_23)

        migrated.query("SELECT id FROM programs").use { cursor ->
            assertTrue("the program already there survived", cursor.moveToFirst())
            assertEquals("p1", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM program_deloads").use { cursor ->
            cursor.moveToFirst()
            assertEquals("no deload is invented by the upgrade", 0, cursor.getInt(0))
        }

        migrated.close()
    }
}
