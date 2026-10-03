package com.example.androidapp.data.local

import java.time.DayOfWeek

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Programs, their slots, and the weeks their occurrences were passed over (ROADMAP P3.3).
 *
 * Its own DAO rather than more methods on `TemplateDao`: a program orders templates the
 * way a template orders exercises, but it is a different aggregate — editing a program
 * never edits a plan, and the two only meet when a slot names one.
 *
 * The one query here that reaches outside the program tables is
 * [sessionsStartedBetween], and it has to: an occurrence is settled by the session that
 * was started from its template, so matching is a join between a program's slots and the
 * week's sessions. It lives here rather than in `WorkoutDao` because the program is the
 * thing being resolved; the session is the evidence.
 */
@Dao
interface ProgramDao {

    /** Every live program, active first then name-ordered. */
    @Query(
        """
        SELECT p.id AS id,
               p.name AS name,
               p.isActive AS isActive,
               (
                   SELECT COUNT(*) FROM program_slots ps
                   WHERE ps.programId = p.id AND ps.deletedAt IS NULL
               ) AS slotCount
        FROM programs p
        WHERE p.deletedAt IS NULL
        ORDER BY p.isActive DESC, p.name ASC
        """,
    )
    fun observePrograms(): Flow<List<ProgramSummaryRow>>

    /** The one program the home screen follows, or null when none is active (P3.3). */
    @Query(
        """
        SELECT p.id AS id,
               p.name AS name,
               p.isActive AS isActive,
               (
                   SELECT COUNT(*) FROM program_slots ps
                   WHERE ps.programId = p.id AND ps.deletedAt IS NULL
               ) AS slotCount
        FROM programs p
        WHERE p.isActive = 1 AND p.deletedAt IS NULL
        LIMIT 1
        """,
    )
    fun observeActiveProgram(): Flow<ProgramSummaryRow?>

    @Query(
        """
        SELECT p.id AS id,
               p.name AS name,
               p.isActive AS isActive,
               (
                   SELECT COUNT(*) FROM program_slots ps
                   WHERE ps.programId = p.id AND ps.deletedAt IS NULL
               ) AS slotCount
        FROM programs p
        WHERE p.isActive = 1 AND p.deletedAt IS NULL
        LIMIT 1
        """,
    )
    suspend fun findActiveProgram(): ProgramSummaryRow?

    @Query(
        """
        SELECT p.id AS id,
               p.name AS name,
               p.isActive AS isActive,
               (
                   SELECT COUNT(*) FROM program_slots ps
                   WHERE ps.programId = p.id AND ps.deletedAt IS NULL
               ) AS slotCount
        FROM programs p
        WHERE p.id = :id AND p.deletedAt IS NULL
        """,
    )
    fun observeProgram(id: String): Flow<ProgramSummaryRow?>

    @Query("SELECT * FROM programs WHERE id = :id AND deletedAt IS NULL")
    suspend fun findProgram(id: String): ProgramEntity?

    /**
     * A program's slots with their templates, in the program's own order (P3.3).
     *
     * A slot whose template has been deleted drops out with it: the join is the rule that
     * a slot points at a living plan, and there is nothing to start otherwise.
     */
    @Query(
        """
        SELECT ps.id AS id,
               ps.programId AS programId,
               ps.templateId AS templateId,
               ps.position AS position,
               ps.weekday AS weekday,
               t.name AS templateName,
               (
                   SELECT COUNT(*) FROM template_exercises te
                   WHERE te.templateId = t.id AND te.deletedAt IS NULL
               ) AS exerciseCount
        FROM program_slots ps
        JOIN templates t ON t.id = ps.templateId
        WHERE ps.programId = :programId
          AND ps.deletedAt IS NULL
          AND t.deletedAt IS NULL
        ORDER BY ps.position ASC
        """,
    )
    fun observeSlotDetails(programId: String): Flow<List<ProgramSlotDetail>>

    /** The same list, one shot — what occurrence matching needs. */
    @Query(
        """
        SELECT ps.id AS id,
               ps.programId AS programId,
               ps.templateId AS templateId,
               ps.position AS position,
               ps.weekday AS weekday,
               t.name AS templateName,
               (
                   SELECT COUNT(*) FROM template_exercises te
                   WHERE te.templateId = t.id AND te.deletedAt IS NULL
               ) AS exerciseCount
        FROM program_slots ps
        JOIN templates t ON t.id = ps.templateId
        WHERE ps.programId = :programId
          AND ps.deletedAt IS NULL
          AND t.deletedAt IS NULL
        ORDER BY ps.position ASC
        """,
    )
    suspend fun findSlotDetails(programId: String): List<ProgramSlotDetail>

    @Query("SELECT * FROM program_slots WHERE programId = :programId")
    suspend fun findSlots(programId: String): List<ProgramSlotEntity>

    @Query("SELECT * FROM program_slots WHERE id = :id AND deletedAt IS NULL")
    suspend fun findSlot(id: String): ProgramSlotEntity?

    @Insert
    suspend fun insertProgram(row: ProgramEntity)

    @Insert
    suspend fun insertSlot(row: ProgramSlotEntity)

    @Insert
    suspend fun insertSkip(row: ProgramSkipEntity)

    /** Rows updated: 0 means the program is gone or was already deleted. */
    @Query("UPDATE programs SET name = :name, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun renameProgram(id: String, name: String, at: Long): Int

    /**
     * Soft-deletes a program and takes it out of use in the same statement (P3.3).
     *
     * Clearing [ProgramEntity.isActive] here matters: a deleted program that stayed
     * active would make "no program is active" false while the home screen, which only
     * reads live rows, showed nothing.
     */
    @Query(
        """
        UPDATE programs
        SET deletedAt = :at, isActive = 0, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteProgram(id: String, at: Long): Int

    @Query("UPDATE programs SET isActive = 0, updatedAt = :at WHERE isActive = 1 AND deletedAt IS NULL")
    suspend fun clearActiveProgram(at: Long): Int

    @Query("UPDATE programs SET isActive = 1, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun markProgramActive(id: String, at: Long): Int

    /**
     * Makes [id] the one active program, in one transaction.
     *
     * Clearing everything first is what makes "one active program only" true by
     * construction rather than by every caller remembering to deactivate the last one.
     * The rows updated are the activated program's, so 0 means it is gone.
     */
    @Transaction
    suspend fun setActiveProgram(id: String, at: Long): Int {
        clearActiveProgram(at)
        return markProgramActive(id, at)
    }

    /** Next free slot position; -1 on an empty program, so callers add 1. */
    @Query("SELECT COALESCE(MAX(position), -1) FROM program_slots WHERE programId = :programId")
    suspend fun maxSlotPosition(programId: String): Int

    /** Pins a slot to a weekday, or makes it order-only (P3.3). */
    @Query(
        """
        UPDATE program_slots
        SET weekday = :weekday, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun setSlotWeekday(id: String, weekday: DayOfWeek?, at: Long): Int

    @Query(
        """
        UPDATE program_slots
        SET position = :position, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun setSlotPosition(id: String, position: Int, at: Long): Int

    @Query(
        """
        UPDATE program_slots
        SET deletedAt = :at, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteSlot(id: String, at: Long): Int

    /** Swaps two slots in one transaction, so a reorder cannot half-move (P3.3). */
    @Transaction
    suspend fun swapSlotPositions(
        firstId: String,
        firstPosition: Int,
        secondId: String,
        secondPosition: Int,
        at: Long,
    ) {
        setSlotPosition(id = firstId, position = firstPosition, at = at)
        setSlotPosition(id = secondId, position = secondPosition, at = at)
    }

    /**
     * Every session that was started from a template and began in [fromMillis, toMillis).
     *
     * The range is deliberately generous by a day at each end: a week is taken in the
     * session's own zone (N25), so a session performed near midnight in another zone can
     * belong to a week the device's own clock does not. The exact bucketing happens in
     * Kotlin, where the zone is known per row.
     */
    @Query(
        """
        SELECT id AS sessionId,
               templateId AS templateId,
               startedAt AS startedAt,
               zoneOffsetMinutes AS zoneOffsetMinutes
        FROM workout_sessions
        WHERE templateId IS NOT NULL
          AND deletedAt IS NULL
          AND startedAt >= :fromMillis
          AND startedAt < :toMillis
        ORDER BY startedAt ASC
        """,
    )
    suspend fun sessionsStartedBetween(fromMillis: Long, toMillis: Long): List<ProgramSessionRow>

    /**
     * Every **finished** session that began in [fromMillis, toMillis), template or not (P3.5).
     *
     * Unlike [sessionsStartedBetween] this does not filter on `templateId`: a session started by
     * hand still marks a trained day, and the calendar needs no schedule to draw it. It does
     * filter on `finishedAt`, because adherence asks whether the training happened — an
     * abandoned start is a miss, which is deliberately stricter than the prompt's "did you
     * start it". A null `templateId` resolves no occurrence; the caller decides that.
     */
    @Query(
        """
        SELECT id AS sessionId,
               templateId AS templateId,
               startedAt AS startedAt,
               zoneOffsetMinutes AS zoneOffsetMinutes
        FROM workout_sessions
        WHERE deletedAt IS NULL
          AND finishedAt IS NOT NULL
          AND startedAt >= :fromMillis
          AND startedAt < :toMillis
        ORDER BY startedAt ASC
        """,
    )
    suspend fun finishedSessionsBetween(fromMillis: Long, toMillis: Long): List<FinishedSessionRow>

    /** The skips recorded for any week in [from, to] inclusive, both epoch days (P3.5). */
    @Query("SELECT * FROM program_skips WHERE weekStart >= :from AND weekStart <= :to AND deletedAt IS NULL")
    suspend fun findSkipsBetween(from: Long, to: Long): List<ProgramSkipEntity>

    /** The skips recorded for one week, live rows only. */
    @Query("SELECT * FROM program_skips WHERE weekStart = :weekStart AND deletedAt IS NULL")
    suspend fun findSkipsForWeek(weekStart: Long): List<ProgramSkipEntity>

    @Query("SELECT COUNT(*) FROM program_skips WHERE slotId = :slotId AND weekStart = :weekStart AND deletedAt IS NULL")
    suspend fun countSkips(slotId: String, weekStart: Long): Int
}
