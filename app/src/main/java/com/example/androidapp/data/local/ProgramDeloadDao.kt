package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The weeks a program was deliberately backed off (ROADMAP P3.10).
 *
 * A DAO of its own because [ProgramDao] is at the function ceiling this project enforces, and
 * because a deload is an *event* rather than a program's own row — the same reasoning that gave
 * the run its own reads (P3.9).
 */
@Dao
interface ProgramDeloadDao {

    /** Every live deload, for the adherence screen. */
    @Query("SELECT * FROM program_deloads WHERE deletedAt IS NULL")
    fun observeDeloads(): Flow<List<ProgramDeloadEntity>>

    /** The live deloads for any week in [from, to] inclusive, both epoch days (P3.10). */
    @Query(
        """
        SELECT * FROM program_deloads
        WHERE weekStart >= :from AND weekStart <= :to AND deletedAt IS NULL
        """,
    )
    suspend fun findDeloadsBetween(from: Long, to: Long): List<ProgramDeloadEntity>

    @Query(
        """
        SELECT COUNT(*) FROM program_deloads
        WHERE programId = :programId AND weekStart = :weekStart AND deletedAt IS NULL
        """,
    )
    suspend fun countDeload(programId: String, weekStart: Long): Int

    @Insert
    suspend fun insertDeload(row: ProgramDeloadEntity)

    /**
     * Soft-deletes one deload, returning the rows changed.
     *
     * Unmarking is always allowed and always a soft delete, like every other table here: an export
     * carries the row, and "this week was a deload and then was not" is history too.
     */
    @Query(
        """
        UPDATE program_deloads
        SET deletedAt = :at, updatedAt = :at
        WHERE programId = :programId AND weekStart = :weekStart AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteDeload(programId: String, weekStart: Long, at: Long): Int
}
