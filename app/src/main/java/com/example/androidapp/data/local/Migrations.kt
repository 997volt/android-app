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

/**
 * v3 -> v4: an exercise's own rest and its technique cue (ROADMAP N5).
 *
 * Both columns are nullable and unset by default, so "no opinion" and the app's
 * 90 s default are the same state — which is what makes this a pair of plain
 * ALTERs rather than a backfill. The seeded library therefore keeps its rest and
 * its cues unset until a user edits them.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(ADD_EXERCISE_REST_SECONDS)
        db.execSQL(ADD_EXERCISE_TECHNIQUE_NOTE)
    }
}

private const val ADD_EXERCISE_REST_SECONDS =
    "ALTER TABLE `exercises` ADD COLUMN `restSeconds` INTEGER"

private const val ADD_EXERCISE_TECHNIQUE_NOTE =
    "ALTER TABLE `exercises` ADD COLUMN `techniqueNote` TEXT"

/**
 * v4 -> v5: the readiness note on a session (ROADMAP N4).
 *
 * Nullable and unset, so an existing or completed workout simply has no note and
 * nothing is backfilled. `notes` stays untouched: it is reserved for a per-workout
 * note, which is a different question from "what is not recovered today".
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(ADD_SESSION_READINESS_NOTE)
    }
}

private const val ADD_SESSION_READINESS_NOTE =
    "ALTER TABLE `workout_sessions` ADD COLUMN `readinessNote` TEXT"

/**
 * v5 -> v6: a set's RPE and its comment (ROADMAP N6).
 *
 * Both nullable and unset, so every set logged before this reads as "no RPE, no
 * comment" — which is exactly what the one-tap **Log set** path keeps writing.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(ADD_SET_RPE)
        db.execSQL(ADD_SET_NOTE)
    }
}

private const val ADD_SET_RPE =
    "ALTER TABLE `set_entries` ADD COLUMN `rpe` INTEGER"

private const val ADD_SET_NOTE =
    "ALTER TABLE `set_entries` ADD COLUMN `note` TEXT"

/**
 * v6 -> v7: when a session exercise was marked done (ROADMAP N7).
 *
 * Nullable and unset, so every exercise in an open or past workout reads as "not
 * done" and nothing is backfilled. Done is a session state, not a delete, so no
 * existing row moves.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(ADD_SESSION_EXERCISE_FINISHED_AT)
    }
}

private const val ADD_SESSION_EXERCISE_FINISHED_AT =
    "ALTER TABLE `session_exercises` ADD COLUMN `finishedAt` INTEGER"

/**
 * v7 -> v8: how an exercise felt — muscle feel and joint pain (ROADMAP N8).
 *
 * Both nullable and unset, so every exercise in an open or past workout reads as
 * "not rated" and nothing is backfilled. They sit on the session exercise rather
 * than the library entry, because the same movement differs day to day.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(ADD_SESSION_EXERCISE_MUSCLE_FEEL)
        db.execSQL(ADD_SESSION_EXERCISE_JOINT_PAIN)
    }
}

private const val ADD_SESSION_EXERCISE_MUSCLE_FEEL =
    "ALTER TABLE `session_exercises` ADD COLUMN `muscleFeel` INTEGER"

private const val ADD_SESSION_EXERCISE_JOINT_PAIN =
    "ALTER TABLE `session_exercises` ADD COLUMN `jointPain` INTEGER"

/** Applied in order by the database builder. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
)
