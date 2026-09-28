package com.example.androidapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
