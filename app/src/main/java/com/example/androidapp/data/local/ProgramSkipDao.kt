package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * The occurrences a lifter consciously passed over (ROADMAP P3.3, P3.13).
 *
 * A DAO of its own because [ProgramDao] is at the function ceiling this project enforces, and
 * because a skip is an *event* like a deload or a substitution rather than a program's own row.
 * It also gives P3.13's correction a writer that is not the missed-day prompt: the prompt records a
 * whole week in one tap, and the correction adds or removes one row at a time.
 */
@Dao
interface ProgramSkipDao {

    @Query("SELECT * FROM program_skips WHERE weekStart >= :from AND weekStart <= :to AND deletedAt IS NULL")
    suspend fun findSkipsBetween(from: Long, to: Long): List<ProgramSkipEntity>

    /** Every live skip in one week — what the missed-day question settles against. */
    @Query("SELECT * FROM program_skips WHERE weekStart = :weekStart AND deletedAt IS NULL")
    suspend fun findSkipsForWeek(weekStart: Long): List<ProgramSkipEntity>

    @Query(
        """
        SELECT COUNT(*) FROM program_skips
        WHERE slotId = :slotId AND weekStart = :weekStart AND deletedAt IS NULL
        """,
    )
    suspend fun countSkips(slotId: String, weekStart: Long): Int

    @Insert
    suspend fun insertSkip(row: ProgramSkipEntity)

    /**
     * Soft-deletes one recorded skip, returning the rows changed (ROADMAP P3.13).
     *
     * A soft delete like every other table here: an export carries the row, and "this week was
     * skipped and then was not" is history too. Removing is always allowed, and the app never does
     * it itself — the correction is a second, explicit writer rather than a second opinion.
     */
    @Query(
        """
        UPDATE program_skips
        SET deletedAt = :at, updatedAt = :at
        WHERE slotId = :slotId AND weekStart = :weekStart AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteSkip(slotId: String, weekStart: Long, at: Long): Int
}
