package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * What a program's run is derived from: its finished sessions and its recorded skips
 * (ROADMAP P3.9).
 *
 * A DAO of its own rather than more methods on [ProgramDao], which is at the function ceiling this
 * project enforces — and these two reads are a different question from the program's own rows: they
 * are the *evidence* a rotation is computed from, exactly as [ProgramDao.sessionsStartedBetween] is
 * the evidence the missed-day question is computed from.
 *
 * No new table, so nothing here changes the schema.
 */
@Dao
interface ProgramRunDao {

    /**
     * Every finished session started from one of [templateIds], oldest first (ROADMAP P3.9).
     *
     * Finished, because the run advances when a slot was *trained*: an abandoned start moves no
     * rotation. The templates are the program's slots' templates, so a session that names none of
     * them is not read at all.
     */
    @Query(
        """
        SELECT id AS sessionId,
               templateId AS templateId,
               startedAt AS startedAt,
               zoneOffsetMinutes AS zoneOffsetMinutes
        FROM workout_sessions
        WHERE templateId IN (:templateIds)
          AND finishedAt IS NOT NULL
          AND deletedAt IS NULL
        ORDER BY startedAt ASC
        """,
    )
    fun observeFinishedSessions(templateIds: List<String>): Flow<List<FinishedSessionRow>>

    /** Every recorded skip of one of [slotIds], oldest week first (ROADMAP P3.9). */
    @Query(
        """
        SELECT * FROM program_skips
        WHERE slotId IN (:slotIds) AND deletedAt IS NULL
        ORDER BY weekStart ASC
        """,
    )
    fun observeSkipsForSlots(slotIds: List<String>): Flow<List<ProgramSkipEntity>>
}
