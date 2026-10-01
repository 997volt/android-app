package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The open session, and whether the call that produced it opened it.
 *
 * [created] is what lets the readiness prompt fire exactly once per workout
 * (ROADMAP N4): a session being resumed has already had its chance, and re-asking
 * after a process death would be nagging rather than prompting.
 */
data class SessionStart(val session: WorkoutSessionEntity, val created: Boolean)

@Dao
interface WorkoutDao {

    /**
     * The single open session, if any. `finishedAt IS NULL` is the definition of
     * "in progress", so this is also the crash-recovery lookup (P1.8).
     */
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE finishedAt IS NULL AND deletedAt IS NULL
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE finishedAt IS NULL AND deletedAt IS NULL
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findActiveSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id AND deletedAt IS NULL")
    suspend fun findSession(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id AND deletedAt IS NULL")
    fun observeSession(id: String): Flow<WorkoutSessionEntity?>

    /**
     * Finished workouts with their totals (ROADMAP P1.6).
     *
     * The counts and the volume are computed in SQL rather than by loading every
     * set and adding it up in Kotlin: a history screen may cover years, and the
     * aggregates are exactly what an index is for. `COALESCE` keeps an empty
     * workout at 0 rather than null.
     *
     * `weightGrams * reps` is gram-reps, summed as a Long — the same exactness
     * argument as storing grams in the first place.
     */
    @Query(
        """
        SELECT ws.id AS id,
               ws.startedAt AS startedAt,
               ws.finishedAt AS finishedAt,
               (
                   SELECT COUNT(*) FROM session_exercises se
                   WHERE se.sessionId = ws.id AND se.deletedAt IS NULL
               ) AS exerciseCount,
               (
                   SELECT COUNT(*) FROM set_entries s
                   JOIN session_exercises se ON se.id = s.sessionExerciseId
                   WHERE se.sessionId = ws.id AND se.deletedAt IS NULL AND s.deletedAt IS NULL
               ) AS setCount,
               (
                   SELECT COALESCE(SUM(s.weightGrams * s.reps), 0) FROM set_entries s
                   JOIN session_exercises se ON se.id = s.sessionExerciseId
                   WHERE se.sessionId = ws.id AND se.deletedAt IS NULL AND s.deletedAt IS NULL
               ) AS volumeGrams
        FROM workout_sessions ws
        WHERE ws.finishedAt IS NOT NULL AND ws.deletedAt IS NULL
        ORDER BY ws.finishedAt DESC
        """,
    )
    fun observeHistory(): Flow<List<WorkoutSummaryRow>>

    /** Session exercises with their library details, in stored order. */
    @Query(
        """
        SELECT se.id AS id,
               se.sessionId AS sessionId,
               se.exerciseId AS exerciseId,
               se.position AS position,
               e.name AS exerciseName,
               e.primaryMuscle AS primaryMuscle,
               e.equipment AS equipment,
               COALESCE(se.restSeconds, e.restSeconds) AS restSeconds,
               COALESCE(se.techniqueNote, e.techniqueNote) AS techniqueNote,
               se.finishedAt AS finishedAt,
               se.muscleFeel AS muscleFeel,
               se.jointPain AS jointPain,
               se.jointPainNote AS jointPainNote,
               se.supersetGroup AS supersetGroup
        FROM session_exercises se
        JOIN exercises e ON e.id = se.exerciseId
        WHERE se.sessionId = :sessionId
          AND se.deletedAt IS NULL
          AND e.deletedAt IS NULL
        ORDER BY se.position ASC
        """,
    )
    fun observeSessionExerciseDetails(sessionId: String): Flow<List<SessionExerciseDetail>>

    /**
     * Next free position; -1 on an empty session, so callers add 1.
     *
     * Deliberately counts soft-deleted rows too: reusing a deleted row's position
     * would make ordering ambiguous the moment anything un-deletes.
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun maxPosition(sessionId: String): Int

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity)

    @Insert
    suspend fun insertSessionExercise(row: SessionExerciseEntity)

    /**
     * Returns the open session, creating one with [id] if there is none.
     *
     * `@Transaction` makes find-then-insert atomic, so two rapid "start workout"
     * taps cannot both observe "no active session" and open two of them. Two open
     * sessions would make every downstream "the active session" ambiguous.
     */
    @Transaction
    suspend fun findOrCreateActiveSession(id: String, now: Long): SessionStart {
        findActiveSession()?.let { return SessionStart(session = it, created = false) }

        val session = WorkoutSessionEntity(
            id = id,
            startedAt = now,
            finishedAt = null,
            notes = null,
            restEndsAt = null,
            readinessNote = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        insertSession(session)
        return SessionStart(session = session, created = true)
    }

    /** Rows updated: 0 means the session does not exist. */
    @Query("UPDATE workout_sessions SET finishedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun markFinished(id: String, at: Long): Int

    /**
     * Sets or clears the readiness note (ROADMAP N4). A null [note] clears it.
     *
     * Rows updated: 0 means the session is gone, so a stale screen cannot write a
     * note into nothing.
     */
    @Query(
        """
        UPDATE workout_sessions
        SET readinessNote = :note, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun updateReadinessNote(id: String, note: String?, at: Long): Int

    @Query("UPDATE workout_sessions SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteSession(id: String, at: Long): Int

    @Query(
        """
        UPDATE session_exercises
        SET deletedAt = :at, updatedAt = :at
        WHERE sessionId = :sessionId AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteSessionExercises(sessionId: String, at: Long): Int

    /** Rows updated: 0 means the row does not exist or was already deleted. */

    @Query("UPDATE session_exercises SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteSessionExercise(id: String, at: Long): Int

    // ---------------------------------------------------------------- sets (P1.3)

    /** Every live set in the session, ordered by exercise position then set index. */
    @Query(
        """
        SELECT s.* FROM set_entries s
        JOIN session_exercises se ON se.id = s.sessionExerciseId
        WHERE se.sessionId = :sessionId AND s.deletedAt IS NULL AND se.deletedAt IS NULL
        ORDER BY se.position ASC, s.setIndex ASC
        """,
    )
    fun observeSetsForSession(sessionId: String): Flow<List<SetEntryEntity>>

    /** Next free set index; -1 on an exercise with no sets, so callers add 1. */
    @Query("SELECT COALESCE(MAX(setIndex), -1) FROM set_entries WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun maxSetIndex(sessionExerciseId: String): Int

    @Insert
    suspend fun insertSet(row: SetEntryEntity)

    /**
     * 1 when [sessionExerciseId] can still receive a set: the row is live, its
     * session is still open, and the exercise has not been marked done.
     *
     * Every half matters. A stale screen can hold an id whose exercise was removed,
     * or whose session was finished on another surface — attaching a set to either
     * would create history that belongs to no workout the user can see. The
     * `finishedAt` check is N7's rule enforced at the boundary: "Done" exists
     * precisely so no set can be added by accident, and a queued tap is exactly
     * that accident.
     */
    @Query(
        """
        SELECT COUNT(*) FROM session_exercises se
        JOIN workout_sessions ws ON ws.id = se.sessionId
        WHERE se.id = :sessionExerciseId
          AND se.deletedAt IS NULL
          AND se.finishedAt IS NULL
          AND ws.finishedAt IS NULL
          AND ws.deletedAt IS NULL
        """,
    )
    suspend fun countLoggableSessionExercise(sessionExerciseId: String): Int

    /** Sets or clears the workout's own comment (ROADMAP N11). Rows updated: 0 = gone. */
    @Query(
        """
        UPDATE workout_sessions
        SET notes = :notes, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun setWorkoutNotes(id: String, notes: String?, at: Long): Int

    /**
     * Sets or clears a session exercise's done timestamp (ROADMAP N7). A null
     * [finishedAt] reopens it.
     *
     * Rows updated: 0 means the exercise is gone.
     */
    @Query(
        """
        UPDATE session_exercises
        SET finishedAt = :finishedAt, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun setSessionExerciseFinished(id: String, finishedAt: Long?, at: Long): Int

    /** The session an exercise belongs to, so ending it can also stop the rest. */
    @Query("SELECT sessionId FROM session_exercises WHERE id = :id AND deletedAt IS NULL")
    suspend fun findSessionIdForSessionExercise(id: String): String?

    /**
     * Writes an exercise's feel ratings (ROADMAP N8). Nulls clear them, because
     * from the workout detail's editor the fields are the whole state.
     *
     * Rows updated: 0 means the exercise is gone.
     */
    @Query(
        """
        UPDATE session_exercises
        SET muscleFeel = :muscleFeel,
            jointPain = :jointPain,
            jointPainNote = :jointPainNote,
            updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun setSessionExerciseRating(
        id: String,
        muscleFeel: Int?,
        jointPain: Int?,
        jointPainNote: String?,
        at: Long,
    ): Int

    /** Rows updated: 0 means the set does not exist or was deleted. */
    @Update
    suspend fun updateSet(set: SetEntryEntity): Int

    @Query("SELECT * FROM set_entries WHERE id = :id AND deletedAt IS NULL")
    suspend fun findSetById(id: String): SetEntryEntity?

    @Query("UPDATE set_entries SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteSet(id: String, at: Long): Int

    /**
     * The most recent *completed* session containing [exerciseId], excluding the
     * one in progress — the source of the "last time" prefill (P1.3).
     *
     * Ordered by `finishedAt` rather than `startedAt`: a workout finished later is
     * the more recent performance even if it was begun earlier.
     */
    @Query(
        """
        SELECT ws.id FROM workout_sessions ws
        JOIN session_exercises se ON se.sessionId = ws.id
        WHERE se.exerciseId = :exerciseId
          AND ws.id != :currentSessionId
          AND ws.finishedAt IS NOT NULL
          AND ws.deletedAt IS NULL
          AND se.deletedAt IS NULL
        ORDER BY ws.finishedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findPreviousSessionIdFor(exerciseId: String, currentSessionId: String): String?

    @Query(
        """
        SELECT s.* FROM set_entries s
        JOIN session_exercises se ON se.id = s.sessionExerciseId
        WHERE se.sessionId = :sessionId
          AND se.exerciseId = :exerciseId
          AND s.deletedAt IS NULL
          AND se.deletedAt IS NULL
        ORDER BY s.setIndex ASC
        """,
    )
    suspend fun findSetsFor(sessionId: String, exerciseId: String): List<SetEntryEntity>

    // ------------------------------------------------------- rest timer (P1.4)

    /** [restEndsAt] null stops the rest timer. */
    @Query(
        """
        UPDATE workout_sessions
        SET restEndsAt = :restEndsAt, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun updateRestTimer(id: String, restEndsAt: Long?, at: Long): Int
}
