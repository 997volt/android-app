package com.example.androidapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
