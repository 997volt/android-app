package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

/**
 * Reading and writing workout sessions (ROADMAP P1.2, P1.8).
 *
 * Reads return plain flows; writes return [DataResult] so the UI can tell the
 * user when a write did *not* land (F7). Silently dropping a logged set is the
 * failure mode that loses a user's trust in the app, so it is modelled rather
 * than thrown.
 */
interface WorkoutRepository {

    /**
     * The in-progress session, or null. Observed rather than fetched once, so
     * every screen showing "workout in progress" stays in step automatically.
     */
    fun observeActiveSession(): Flow<WorkoutSession?>

    /** Exercises in [sessionId], in their stored order. */
    fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>>

    /**
     * Returns the in-progress session, creating one if there is none.
     *
     * Deliberately idempotent: two concurrent "start workout" taps must not
     * produce two open sessions, which would make "the active session"
     * ambiguous everywhere downstream.
     */
    suspend fun startOrResumeSession(): DataResult<String>

    /** Appends [exerciseId] to the end of the session. */
    suspend fun addExercise(sessionId: String, exerciseId: String): DataResult<Unit>

    suspend fun removeExercise(sessionExerciseId: String): DataResult<Unit>

    /** Marks the session complete. It stops being "active" and enters history. */
    suspend fun finishSession(sessionId: String): DataResult<Unit>

    /** Soft-deletes the session and everything in it. */
    suspend fun discardSession(sessionId: String): DataResult<Unit>
}
