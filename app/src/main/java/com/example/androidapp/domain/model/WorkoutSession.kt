package com.example.androidapp.domain.model

import java.time.Instant

/**
 * A training session (ROADMAP P1.2).
 *
 * `java.time.Instant` rather than an epoch `Long` in the domain: `minSdk` 26 was
 * chosen partly so `java.time` is available natively (ROADMAP F9), and the richer
 * type makes duration arithmetic obvious. Only the *entity* stores a Long.
 *
 * [finishedAt] being null is what makes a session "active" — and is the whole
 * basis of crash recovery (P1.8): the row is written the moment a workout starts,
 * so a process death leaves it open rather than losing it.
 */
data class WorkoutSession(
    val id: String,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    /** When the current rest ends, or null when not resting (P1.4). */
    val restEndsAt: Instant? = null,
    /**
     * What was not recovered today (ROADMAP N4). Free text, prompted once when the
     * session opens and editable afterwards from the workout header.
     */
    val readinessNote: String? = null,
) {
    val isActive: Boolean get() = finishedAt == null
}

/**
 * An exercise as it appears *within* a session, at an explicit [position].
 *
 * Carries the library attributes ([exerciseName], [primaryMuscle], [equipment])
 * because that is exactly what the workout screen displays, and fetching them in
 * the same query avoids an N+1 walk over the library while the user is mid-set.
 *
 * [position] is stored rather than inferred from insertion order, so reordering a
 * workout never depends on row ids sorting usefully.
 */
data class SessionExercise(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val exerciseName: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    /** The library exercise's own rest, or null for the app default (ROADMAP N5). */
    val restSeconds: Int? = null,
    /** A cue to show under the name while lifting (ROADMAP N5). */
    val techniqueNote: String? = null,
    /**
     * When this exercise was marked done (ROADMAP N7), or null while it is open.
     *
     * Deliberately not a delete: [finishedAt] set means no more sets are added and
     * the existing ones cannot be edited until it is reopened.
     */
    val finishedAt: Instant? = null,
    /**
     * How well the target muscle was worked, 1–10, or null (ROADMAP N8).
     *
     * Stored per session exercise rather than per library exercise, so the same
     * movement is measured differently on different days. Skippable.
     */
    val muscleFeel: Int? = null,
    /** Discomfort in joints or connective tissue, 1–10, or null (ROADMAP N8). */
    val jointPain: Int? = null,
) {
    val isFinished: Boolean get() = finishedAt != null
}
