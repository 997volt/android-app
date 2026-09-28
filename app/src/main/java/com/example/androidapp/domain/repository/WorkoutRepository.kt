package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.WorkoutSession
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Reading and writing workout sessions (ROADMAP P1.2, P1.3, P1.4, P1.8).
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

    /** Every live set in [sessionId], ordered by exercise position then set. */
    fun observeSets(sessionId: String): Flow<List<SetEntry>>

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

    /** Logs a set at the end of [sessionExerciseId] (P1.3). */
    suspend fun logSet(
        sessionExerciseId: String,
        reps: Int,
        weightGrams: Long,
        setType: SetType = SetType.NORMAL,
    ): DataResult<Unit>

    suspend fun updateSet(setId: String, reps: Int, weightGrams: Long): DataResult<Unit>

    suspend fun deleteSet(setId: String): DataResult<Unit>

    /**
     * What [exerciseId] looked like the last time it was trained (P1.3 prefill).
     *
     * Returns an empty [PreviousPerformance] when there is no history — that is a
     * normal answer, not a failure, so only a storage error is a `Failure`.
     */
    suspend fun previousPerformance(
        exerciseId: String,
        currentSessionId: String,
    ): DataResult<PreviousPerformance>

    /**
     * Starts (or restarts) the rest timer on the active session (P1.4), returning
     * the instant it will end.
     *
     * Returning the end instant rather than making the caller re-read it means an
     * alert is scheduled against exactly what was written — no window in which the
     * database and the alarm disagree.
     */
    suspend fun startRest(seconds: Int = RestTimer.DEFAULT_SECONDS): DataResult<Instant>

    /** Moves the running rest timer by [deltaSeconds]; a finished rest restarts from now. */
    suspend fun adjustRest(deltaSeconds: Int): DataResult<Instant>

    /** Stops the rest timer. */
    suspend fun clearRest(): DataResult<Unit>
}
