package com.example.androidapp.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migration array itself (ROADMAP B11).
 *
 * Every migration has a good test that upgrades a real database holding real rows — but
 * each of those tests passes its own migration object explicitly, and `ALL_MIGRATIONS`
 * is consumed only by the database builder. So a migration written, tested, and then
 * forgotten in the array left the whole suite green while crashing every install that
 * had data. These tests cannot be satisfied by a migration that is correct but
 * unregistered.
 *
 * The declared version is read from a database rather than from the `@Database`
 * annotation, which has binary retention and so is invisible to reflection — and the
 * runtime version is the one that matters anyway.
 */
@RunWith(AndroidJUnit4::class)
class MigrationsTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkoutDatabase::class.java,
    )

    private fun declaredVersion(): Int {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        val version = database.openHelper.readableDatabase.version
        database.close()
        return version
    }

    @Test
    fun everyVersion_isCoveredExactlyOnce() {
        val version = declaredVersion()
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }

        assertEquals(
            "each migration must move exactly one version on",
            emptyList<Pair<Int, Int>>(),
            steps.filter { (from, to) -> to != from + 1 },
        )
        assertEquals(
            "two migrations must not claim the same step",
            steps.map { it.first }.distinct().sorted(),
            steps.map { it.first }.sorted(),
        )
        assertEquals(
            "the chain must cover every version from 1 to the declared one",
            (1 until version).toList(),
            steps.map { it.first }.sorted(),
        )
    }

    @Test
    fun theArray_endsAtTheDeclaredVersion() {
        // The failure this prevents: a version bump with no migration behind it, which
        // Room reports on a device rather than in a test.
        val version = declaredVersion()

        assertEquals(version, ALL_MIGRATIONS.maxOf { it.endVersion })
    }

    @Test
    fun theWholeChain_upgradesTheFirstSchema() {
        // One run from the oldest schema to now, which is the only way a collision that
        // appears in sequence — two migrations touching the same table, say — shows up.
        // The individual tests never run the chain.
        val version = declaredVersion()
        helper.createDatabase(TEST_DB, 1).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, version, true, *ALL_MIGRATIONS)

        // The interesting assertion is the one the helper already made: it validates the
        // migrated schema against the current entities, so reaching here means the chain
        // produced exactly today's tables and columns.
        migrated.query("SELECT COUNT(*) FROM exercises").use { cursor ->
            cursor.moveToFirst()
            assertEquals("the oldest schema's table survived the chain", 0, cursor.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migrations-chain.db"
    }
}
