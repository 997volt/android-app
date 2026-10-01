package com.example.androidapp.domain.repository

import com.example.androidapp.domain.model.PersonalRecords
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.WorkoutSummary
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The session a workout screen is now in, and whether this call opened it.
 *
 * [isNew] is what lets the readiness prompt fire exactly once per workout
 * (ROADMAP N4): a resumed session has already had its chance.
 */
data class StartedSession(val id: String, val isNew: Boolean)

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
     * Finished workouts, newest first, with their totals already aggregated
     * (ROADMAP P1.6).
     *
     * Only finished ones: an open session belongs on the workout screen, not in a
     * history you are meant to be reading.
     */
    fun observeHistory(): Flow<List<WorkoutSummary>>

    /** One session by id, so the detail screen observes rather than fetches. */
    fun observeSession(sessionId: String): Flow<WorkoutSession?>

    /**
     * Returns the in-progress session, creating one if there is none.
     *
     * Deliberately idempotent: two concurrent "start workout" taps must not
     * produce two open sessions, which would make "the active session"
     * ambiguous everywhere downstream. [StartedSession.isNew] distinguishes the
     * call that opened it from one that found it already open.
     *
     * When [templateId] is given *and this call opens the session*, that template's
     * exercises are appended in order (ROADMAP N3), through the same append path
     * [addExercise] uses. A resumed session is left alone: it already has the
     * exercises it was started with, and seeding it again would duplicate them.
     */
    suspend fun startOrResumeSession(templateId: String? = null): DataResult<StartedSession>

    /** Appends [exerciseId] to the end of the session. */
    suspend fun addExercise(sessionId: String, exerciseId: String): DataResult<Unit>

    suspend fun removeExercise(sessionExerciseId: String): DataResult<Unit>

    /**
     * Marks a session exercise done (ROADMAP N7): no more sets can be logged, its
     * existing sets stop being editable, and any running rest is cleared.
     *
     * Not a delete — the sets stay in history — and reversible through
     * [reopenExercise], because accident protection must not become its own trap.
     */
    suspend fun finishExercise(sessionExerciseId: String): DataResult<Unit>

    /**
     * Writes the workout's own comment (ROADMAP N11), or clears it when [note] is
     * null or blank. Separate from the readiness note, which is about how you felt
     * going *in*: this is about how it went.
     */
    suspend fun setWorkoutNotes(sessionId: String, note: String?): DataResult<Unit>

    /** Reopens a done exercise (ROADMAP N7), restoring logging and editing. */
    suspend fun reopenExercise(sessionExerciseId: String): DataResult<Unit>

    /**
     * Writes how an exercise felt — muscle feel and joint pain, each 1–10 or null
     * to clear it (ROADMAP N8).
     *
     * Separate from [finishExercise] rather than folded into it: the ratings are
     * captured *at* Done but are skippable, and they stay editable from the workout
     * detail afterwards, which is a different write on a possibly-finished row.
     */
    suspend fun rateExercise(
        sessionExerciseId: String,
        muscleFeel: Int?,
        jointPain: Int?,
        /** Which joints hurt, or null (ROADMAP N9). Blank is stored as null. */
        jointPainNote: String? = null,
    ): DataResult<Unit>

    /** Marks the session complete. It stops being "active" and enters history. */
    suspend fun finishSession(sessionId: String): DataResult<Unit>

    /**
     * Sets or clears the session's readiness note (ROADMAP N4). A blank note is
     * stored as null, so "nothing written" has one representation.
     */
    suspend fun setReadinessNote(sessionId: String, note: String?): DataResult<Unit>

    /** Soft-deletes the session and everything in it. */
    suspend fun deleteSession(sessionId: String): DataResult<Unit>

    /** Logs a set at the end of [sessionExerciseId] (P1.3). */
    suspend fun logSet(
        sessionExerciseId: String,
        reps: Int,
        weightGrams: Long,
        setType: SetType = SetType.NORMAL,
        /** The machine's assistance, as a magnitude (ROADMAP N15). */
        assistanceGrams: Long = 0,
    ): DataResult<Unit>

    /**
     * Rewrites a logged set, including its RPE and comment (ROADMAP N6).
     *
     * [rpeHalves] and [note] are required rather than defaulted: an edit states what the
     * set now says, so a caller cannot clear them by forgetting to pass them.
     */
    suspend fun updateSet(
        setId: String,
        reps: Int,
        weightGrams: Long,
        rpeHalves: Int? = null,
        note: String? = null,
        /** The role the set was performed as (ROADMAP N14). */
        setType: SetType = SetType.NORMAL,
        /** The machine's assistance, as a magnitude (ROADMAP N15). */
        assistanceGrams: Long = 0,
    ): DataResult<Unit>

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

    /**
     * Puts this exercise in a superset with another, or takes it out (ROADMAP N24).
     *
     * The group is an ordinal shared with the exercises it is performed with; null means
     * "on its own again", which is what every exercise was before N24.
     */
    suspend fun setSupersetGroup(
        sessionExerciseId: String,
        group: Int?,
    ): DataResult<Unit>

    /**
     * The heaviest working set at each rep count for this exercise (ROADMAP N23).
     *
     * [excludingSessionId] is the session in progress: a set must be compared against what
     * came *before* it, or the second set of a session would be checked against the first
     * one's record and every session would look like a breakthrough. `null` includes
     * everything, which is what a records view wants.
     */
    suspend fun personalRecords(
        exerciseId: String,
        excludingSessionId: String? = null,
    ): DataResult<PersonalRecords>

    /** Moves the running rest timer by [deltaSeconds]; a finished rest restarts from now. */
    suspend fun adjustRest(deltaSeconds: Int): DataResult<Instant>

    /** Stops the rest timer. */
    suspend fun clearRest(): DataResult<Unit>
}
