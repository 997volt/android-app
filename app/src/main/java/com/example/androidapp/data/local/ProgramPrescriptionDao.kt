package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * What each program slot prescribes: an exercise's rest and cue, and the sets (ROADMAP P3.8).
 *
 * A DAO of its own rather than more methods on [ProgramDao], which is at the function ceiling
 * this project enforces: programs, slots and skips are one aggregate, and a slot's prescription
 * is a second that only meets the first when a slot names an exercise. The same split
 * [ProgramBackupDao] made from [BackupDao], for the same reason.
 *
 * Rows are soft-deleted like every other table's, so an export can carry them.
 */
@Dao
interface ProgramPrescriptionDao {

    /** The live exercise rows of one slot, in no particular order — the caller orders by template. */
    @Query("SELECT * FROM program_slot_exercises WHERE slotId = :slotId AND deletedAt IS NULL")
    fun observeSlotExercises(slotId: String): Flow<List<ProgramSlotExerciseEntity>>

    /** The same rows, one shot — what seeding a session from a slot reads (P3.8). */
    @Query("SELECT * FROM program_slot_exercises WHERE slotId = :slotId AND deletedAt IS NULL")
    suspend fun findSlotExercises(slotId: String): List<ProgramSlotExerciseEntity>

    /** Every live prescribed set of a slot's live exercises, in set order (P3.8). */
    @Query(
        """
        SELECT s.* FROM program_slot_sets s
        JOIN program_slot_exercises e ON e.id = s.slotExerciseId
        WHERE e.slotId = :slotId
          AND e.deletedAt IS NULL
          AND s.deletedAt IS NULL
        ORDER BY s.setIndex ASC
        """,
    )
    fun observeSlotSets(slotId: String): Flow<List<ProgramSlotSetEntity>>

    @Query(
        """
        SELECT * FROM program_slot_exercises
        WHERE slotId = :slotId AND exerciseId = :exerciseId AND deletedAt IS NULL
        """,
    )
    suspend fun findSlotExercise(slotId: String, exerciseId: String): ProgramSlotExerciseEntity?

    @Query("SELECT * FROM program_slot_exercises WHERE id = :id AND deletedAt IS NULL")
    suspend fun findSlotExerciseById(id: String): ProgramSlotExerciseEntity?

    @Query("SELECT * FROM program_slot_sets WHERE id = :id AND deletedAt IS NULL")
    suspend fun findSlotSet(id: String): ProgramSlotSetEntity?

    /** How many live sets an exercise row still prescribes — what "empty" is measured by. */
    @Query(
        """
        SELECT COUNT(*) FROM program_slot_sets
        WHERE slotExerciseId = :slotExerciseId AND deletedAt IS NULL
        """,
    )
    suspend fun countSlotSets(slotExerciseId: String): Int

    /** Next free set index; -1 on an empty exercise, so callers add 1. */
    @Query(
        """
        SELECT COALESCE(MAX(setIndex), -1) FROM program_slot_sets
        WHERE slotExerciseId = :slotExerciseId AND deletedAt IS NULL
        """,
    )
    suspend fun maxSetIndex(slotExerciseId: String): Int

    @Insert
    suspend fun insertSlotExercise(row: ProgramSlotExerciseEntity)

    @Insert
    suspend fun insertSlotSet(row: ProgramSlotSetEntity)

    @Update
    suspend fun updateSlotExercise(row: ProgramSlotExerciseEntity): Int

    @Update
    suspend fun updateSlotSet(row: ProgramSlotSetEntity): Int

    @Query(
        """
        UPDATE program_slot_exercises
        SET deletedAt = :at, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteSlotExercise(id: String, at: Long): Int

    @Query(
        """
        UPDATE program_slot_sets
        SET deletedAt = :at, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteSlotSet(id: String, at: Long): Int
}
