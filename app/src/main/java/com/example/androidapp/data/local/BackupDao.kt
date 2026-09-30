package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Whole-table reads and inserts for backup and restore (ROADMAP P1.12).
 *
 * Deliberately unfiltered: unlike every other DAO here, these queries return
 * soft-deleted rows too. A backup that quietly dropped them would restore into a
 * database that differs from the original, which defeats the point of being the
 * escape hatch.
 *
 * `OnConflictStrategy.IGNORE` on the inserts is what makes an import additive and
 * idempotent: ids are stable, so importing the same file twice adds nothing the
 * second time, and importing cannot overwrite something already on the device.
 * It also sidesteps the DELETE+INSERT trap of `REPLACE`, which would trip the
 * `ON DELETE RESTRICT` foreign key on `session_exercises.exerciseId`.
 */
@Dao
interface BackupDao {

    @Query("SELECT * FROM exercises")
    suspend fun allExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM workout_sessions")
    suspend fun allSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM session_exercises")
    suspend fun allSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_entries")
    suspend fun allSets(): List<SetEntryEntity>

    /** Templates ride along too (ROADMAP N3): a plan is data like any other. */
    @Query("SELECT * FROM templates")
    suspend fun allTemplates(): List<TemplateEntity>

    @Query("SELECT * FROM template_exercises")
    suspend fun allTemplateExercises(): List<TemplateExerciseEntity>

    /** A plan's sets too (ROADMAP N14): a plan is the user's work like any other. */
    @Query("SELECT * FROM template_sets")
    suspend fun allTemplateSets(): List<TemplateSetEntity>

    /**
     * Ids of rows the user deleted, which are still present with `deletedAt` set.
     *
     * This is what an insert-only import gets wrong: a soft delete keeps the row
     * (and its id), so `INSERT OR IGNORE` skips precisely the rows a restore is
     * supposed to bring back. Knowing which ids are *hidden* rather than *absent*
     * is what separates the two cases.
     */
    @Query("SELECT id FROM exercises WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedExerciseIds(): List<String>

    @Query("SELECT id FROM workout_sessions WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedSessionIds(): List<String>

    @Query("SELECT id FROM session_exercises WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedSessionExerciseIds(): List<String>

    @Query("SELECT id FROM set_entries WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedSetIds(): List<String>

    @Query("SELECT id FROM templates WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedTemplateIds(): List<String>

    @Query("SELECT id FROM template_exercises WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedTemplateExerciseIds(): List<String>

    @Query("SELECT id FROM template_sets WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedTemplateSetIds(): List<String>

    /**
     * Rewrites rows by primary key, which restores a soft-deleted row *and* clears
     * its `deletedAt` because the file's value overwrites it. Only ever called for
     * ids that are already soft-deleted, so a live row is never clobbered.
     */
    @Update
    suspend fun restoreExercises(rows: List<ExerciseEntity>): Int

    @Update
    suspend fun restoreSessions(rows: List<WorkoutSessionEntity>): Int

    @Update
    suspend fun restoreSessionExercises(rows: List<SessionExerciseEntity>): Int

    @Update
    suspend fun restoreSets(rows: List<SetEntryEntity>): Int

    @Update
    suspend fun restoreTemplates(rows: List<TemplateEntity>): Int

    @Update
    suspend fun restoreTemplateExercises(rows: List<TemplateExerciseEntity>): Int

    @Update
    suspend fun restoreTemplateSets(rows: List<TemplateSetEntity>): Int

    /** Returns one rowid per input row, `-1` where a row was skipped as a duplicate. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(rows: List<ExerciseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessions(rows: List<WorkoutSessionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionExercises(rows: List<SessionExerciseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSets(rows: List<SetEntryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplates(rows: List<TemplateEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplateExercises(rows: List<TemplateExerciseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplateSets(rows: List<TemplateSetEntity>): List<Long>
}
