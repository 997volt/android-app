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
        TemplateEntity::class,
        TemplateExerciseEntity::class,
        TemplateSetEntity::class,
        MeasurementEntity::class,
        ProgramEntity::class,
        ProgramSlotEntity::class,
        ProgramSkipEntity::class,
    ],
    version = 20,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WorkoutDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    abstract fun measurementDao(): MeasurementDao

    abstract fun statisticsDao(): StatisticsDao

    abstract fun sessionExerciseDao(): SessionExerciseDao

    abstract fun workoutDao(): WorkoutDao

    /** Templates and their exercises (ROADMAP N3). */
    abstract fun templateDao(): TemplateDao

    /** Programs, their slots and their recorded skips (ROADMAP P3.3). */
    abstract fun programDao(): ProgramDao

    /** Whole-table reads and additive inserts for backup/restore (P1.12). */
    abstract fun backupDao(): BackupDao

    /** The newest tables' half of that (ROADMAP P3.3), split to keep both under the ceiling. */
    abstract fun programBackupDao(): ProgramBackupDao

    /** Read-only per-workout aggregates for the trends screen (ROADMAP N13). */
    abstract fun trendsDao(): TrendsDao

    companion object {
        const val NAME = "workout.db"
    }
}
