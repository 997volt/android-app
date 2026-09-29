package com.example.androidapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The app's local database (ROADMAP F5).
 *
 * `exportSchema = true` writes the schema to `app/schemas/` on every build; those
 * JSON files are committed and are what migration tests validate against.
 *
 * There is deliberately **no** `fallbackToDestructiveMigration()`: it would turn
 * a forgotten migration into silent, total data loss for a user's training
 * history. A missing migration must fail loudly in development instead.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetEntryEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WorkoutDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    abstract fun workoutDao(): WorkoutDao

    /** Whole-table reads and additive inserts for backup/restore (P1.12). */
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "workout.db"
    }
}
