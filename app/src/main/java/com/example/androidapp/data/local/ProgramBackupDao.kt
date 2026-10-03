package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Programs, their slots and their recorded skips, for backup and restore (ROADMAP P3.3).
 *
 * A DAO of its own rather than three more tables on [BackupDao]: that interface is one
 * read, one insert and one update per table, so its size is the schema's and it had reached
 * the ceiling this project enforces. Splitting the newest three tables out keeps the gate
 * meaningful instead of raising it, and the shape here is exactly [BackupDao]'s — unfiltered
 * reads (soft-deleted rows are data an export must carry) and additive inserts.
 */
@Dao
interface ProgramBackupDao {

    @Query("SELECT * FROM programs")
    suspend fun allPrograms(): List<ProgramEntity>

    @Query("SELECT * FROM program_slots")
    suspend fun allProgramSlots(): List<ProgramSlotEntity>

    @Query("SELECT * FROM program_skips")
    suspend fun allProgramSkips(): List<ProgramSkipEntity>

    @Query("SELECT * FROM program_slot_exercises")
    suspend fun allProgramSlotExercises(): List<ProgramSlotExerciseEntity>

    @Query("SELECT * FROM program_slot_sets")
    suspend fun allProgramSlotSets(): List<ProgramSlotSetEntity>

    @Query("SELECT * FROM program_deloads")
    suspend fun allProgramDeloads(): List<ProgramDeloadEntity>

    @Query("SELECT * FROM program_substitutions")
    suspend fun allProgramSubstitutions(): List<ProgramSubstitutionEntity>

    @Query("SELECT id FROM programs WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedProgramIds(): List<String>

    @Query("SELECT id FROM program_slots WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedProgramSlotIds(): List<String>

    @Query("SELECT id FROM program_skips WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedProgramSkipIds(): List<String>

    @Query("SELECT id FROM program_slot_exercises WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedProgramSlotExerciseIds(): List<String>

    @Query("SELECT id FROM program_slot_sets WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedProgramSlotSetIds(): List<String>

    @Query("SELECT id FROM program_deloads WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedProgramDeloadIds(): List<String>

    @Query("SELECT id FROM program_substitutions WHERE deletedAt IS NOT NULL")
    suspend fun softDeletedProgramSubstitutionIds(): List<String>

    @Update
    suspend fun restorePrograms(rows: List<ProgramEntity>): Int

    @Update
    suspend fun restoreProgramSlots(rows: List<ProgramSlotEntity>): Int

    @Update
    suspend fun restoreProgramSkips(rows: List<ProgramSkipEntity>): Int

    @Update
    suspend fun restoreProgramSlotExercises(rows: List<ProgramSlotExerciseEntity>): Int

    @Update
    suspend fun restoreProgramSlotSets(rows: List<ProgramSlotSetEntity>): Int

    @Update
    suspend fun restoreProgramDeloads(rows: List<ProgramDeloadEntity>): Int

    @Update
    suspend fun restoreProgramSubstitutions(rows: List<ProgramSubstitutionEntity>): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPrograms(rows: List<ProgramEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgramSlots(rows: List<ProgramSlotEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgramSkips(rows: List<ProgramSkipEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgramSlotExercises(rows: List<ProgramSlotExerciseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgramSlotSets(rows: List<ProgramSlotSetEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgramDeloads(rows: List<ProgramDeloadEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgramSubstitutions(rows: List<ProgramSubstitutionEntity>): List<Long>
}
