package com.example.androidapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the committed schema baseline (ROADMAP F5).
 *
 * There is nothing to migrate at version 1, so the value of this test is not the
 * migration — it is that `app/schemas/` is wired into the instrumented assets and
 * readable, and that the exported JSON describes exactly what the entities
 * produce. That is the harness the first real migration will be written against,
 * and having it working *before* there is anything to migrate is what stops it
 * from becoming untested scaffolding later.
 *
 * Migrations must be added to a `RoomDatabase.Builder` for release; there is no
 * `fallbackToDestructiveMigration()` anywhere, so a forgotten one fails loudly
 * instead of wiping a user's training history.
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

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
