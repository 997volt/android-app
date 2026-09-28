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
               e.movementPattern AS movementPattern
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
}
