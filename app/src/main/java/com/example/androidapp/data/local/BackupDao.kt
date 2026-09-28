package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    /** Returns one rowid per input row, `-1` where a row was skipped as a duplicate. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(rows: List<ExerciseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessions(rows: List<WorkoutSessionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionExercises(rows: List<SessionExerciseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSets(rows: List<SetEntryEntity>): List<Long>
}
