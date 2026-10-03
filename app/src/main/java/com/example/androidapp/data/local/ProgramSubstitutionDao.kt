package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The workouts that stood in for a slot's own in one week (ROADMAP P3.11).
 *
 * A DAO of its own because [ProgramDao] is at the function ceiling this project enforces, and
 * because a substitution is an *event* like a skip or a deload rather than a program's own row.
 */
@Dao
interface ProgramSubstitutionDao {

    /** The live substitutions of one or more slots, for a run's reads (P3.11). */
    @Query(
        """
        SELECT * FROM program_substitutions
        WHERE slotId IN (:slotIds) AND deletedAt IS NULL
        ORDER BY weekStart ASC
        """,
    )
    fun observeSubstitutionsForSlots(slotIds: List<String>): Flow<List<ProgramSubstitutionEntity>>

    /** The live substitutions for any week in [from, to] inclusive, both epoch days (P3.11). */
    @Query(
        """
        SELECT * FROM program_substitutions
        WHERE weekStart >= :from AND weekStart <= :to AND deletedAt IS NULL
        """,
    )
    suspend fun findSubstitutionsBetween(from: Long, to: Long): List<ProgramSubstitutionEntity>

    @Query(
        """
        SELECT * FROM program_substitutions
        WHERE slotId = :slotId AND weekStart = :weekStart AND deletedAt IS NULL
        """,
    )
    suspend fun findSubstitution(slotId: String, weekStart: Long): ProgramSubstitutionEntity?

    @Insert
    suspend fun insertSubstitution(row: ProgramSubstitutionEntity)

    @Query(
        """
        UPDATE program_substitutions
        SET templateId = :templateId, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun setSubstitutionTemplate(id: String, templateId: String, at: Long): Int

    /**
     * Soft-deletes one substitution, returning the rows changed.
     *
     * Clearing is a soft delete like every other table here: an export carries the row, and "this
     * week was done with the dumbbells and then was not" is history too.
     */
    @Query(
        """
        UPDATE program_substitutions
        SET deletedAt = :at, updatedAt = :at
        WHERE slotId = :slotId AND weekStart = :weekStart AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteSubstitution(slotId: String, weekStart: Long, at: Long): Int
}
