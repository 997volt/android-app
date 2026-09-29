package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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
               e.equipment AS equipment
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

    @Query("SELECT COUNT(*) FROM workout_sessions")
    suspend fun sessionCount(): Int

    @Query("SELECT COUNT(*) FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun sessionExerciseCount(sessionId: String): Int

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
    suspend fun findOrCreateActiveSession(id: String, now: Long): WorkoutSessionEntity {
        findActiveSession()?.let { return it }

        val session = WorkoutSessionEntity(
            id = id,
            startedAt = now,
            finishedAt = null,
            notes = null,
            restEndsAt = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        insertSession(session)
        return session
    }

    /** Rows updated: 0 means the session does not exist. */
    @Query("UPDATE workout_sessions SET finishedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun markFinished(id: String, at: Long): Int

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
     * 1 when [sessionExerciseId] can still receive a set: the row is live *and* its
     * session is still open.
     *
     * Both halves matter. A stale screen can hold an id whose exercise was removed,
     * or whose session was finished on another surface — attaching a set to either
     * would create history that belongs to no workout the user can see.
     */
    @Query(
        """
        SELECT COUNT(*) FROM session_exercises se
        JOIN workout_sessions ws ON ws.id = se.sessionId
        WHERE se.id = :sessionExerciseId
          AND se.deletedAt IS NULL
          AND ws.finishedAt IS NULL
          AND ws.deletedAt IS NULL
        """,
    )
    suspend fun countLoggableSessionExercise(sessionExerciseId: String): Int

    /** Rows updated: 0 means the set does not exist or was deleted. */
    @Query(
        """
        UPDATE set_entries
        SET reps = :reps, weightGrams = :weightGrams, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun updateSet(id: String, reps: Int, weightGrams: Long, at: Long): Int

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
