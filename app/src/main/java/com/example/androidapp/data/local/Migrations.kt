// Schema version numbers are inherently literal — `Migration(2, 3)` reads as
// "from v2 to v3" and naming them would obscure exactly the thing being stated.
@file:Suppress("MagicNumber")

package com.example.androidapp.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The database's schema history. Every migration lives here and is validated by
 * `WorkoutDatabaseMigrationTest` against the committed JSON in `app/schemas/`.
 *
 * The SQL is copied from Room's own generated `createSql` rather than
 * hand-written to "look equivalent". Room compares the migrated database's
 * structure (columns, foreign keys, indices) against the expected schema, so a
 * typo'd column type or a missing index fails the migration test rather than
 * silently shipping — but there is no reason to make it guess.
 */
private const val CREATE_WORKOUT_SESSIONS =
    "CREATE TABLE IF NOT EXISTS `workout_sessions` (" +
        "`id` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, " +
        "`notes` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
        "`deletedAt` INTEGER, PRIMARY KEY(`id`))"

private const val CREATE_WORKOUT_SESSIONS_FINISHED_AT_INDEX =
    "CREATE INDEX IF NOT EXISTS `index_workout_sessions_finishedAt` " +
        "ON `workout_sessions` (`finishedAt`)"

private const val CREATE_SESSION_EXERCISES =
    "CREATE TABLE IF NOT EXISTS `session_exercises` (" +
        "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, " +
        "`position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
        "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
        "FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) " +
        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
        "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) " +
        "ON UPDATE NO ACTION ON DELETE RESTRICT )"

private const val CREATE_SESSION_EXERCISES_SESSION_ID_INDEX =
    "CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` " +
        "ON `session_exercises` (`sessionId`)"

private const val CREATE_SESSION_EXERCISES_EXERCISE_ID_INDEX =
    "CREATE INDEX IF NOT EXISTS `index_session_exercises_exerciseId` " +
        "ON `session_exercises` (`exerciseId`)"

/**
 * v1 -> v2: workout sessions and the exercises inside them (ROADMAP P1.2, P1.8).
 *
 * Purely additive: no existing table is touched, so an upgrade cannot lose the
 * exercise library that v1 already wrote.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_WORKOUT_SESSIONS)
        db.execSQL(CREATE_WORKOUT_SESSIONS_FINISHED_AT_INDEX)
        db.execSQL(CREATE_SESSION_EXERCISES)
        db.execSQL(CREATE_SESSION_EXERCISES_SESSION_ID_INDEX)
        db.execSQL(CREATE_SESSION_EXERCISES_EXERCISE_ID_INDEX)
    }
}

/**
 * v2 -> v3: logged sets and the persisted rest timer (ROADMAP P1.3, P1.4).
 *
 * [REST_ENDS_AT_COLUMN] is nullable and has no default, so the ALTER is valid on
 * a table that already has rows — existing sessions simply read as "not resting".
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_SET_ENTRIES)
        db.execSQL(CREATE_SET_ENTRIES_SESSION_EXERCISE_ID_INDEX)
        db.execSQL(ADD_REST_ENDS_AT)
    }
}

private const val CREATE_SET_ENTRIES =
    "CREATE TABLE IF NOT EXISTS `set_entries` (" +
        "`id` TEXT NOT NULL, `sessionExerciseId` TEXT NOT NULL, " +
        "`setIndex` INTEGER NOT NULL, `reps` INTEGER NOT NULL, " +
        "`weightGrams` INTEGER NOT NULL, `setType` TEXT NOT NULL, " +
        "`completedAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
        "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
        "FOREIGN KEY(`sessionExerciseId`) REFERENCES `session_exercises`(`id`) " +
        "ON UPDATE NO ACTION ON DELETE CASCADE )"

private const val CREATE_SET_ENTRIES_SESSION_EXERCISE_ID_INDEX =
    "CREATE INDEX IF NOT EXISTS `index_set_entries_sessionExerciseId` " +
        "ON `set_entries` (`sessionExerciseId`)"

private const val ADD_REST_ENDS_AT =
    "ALTER TABLE `workout_sessions` ADD COLUMN `restEndsAt` INTEGER"

/** Applied in order by the database builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
