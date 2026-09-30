package com.example.androidapp.domain.model

import java.time.Duration
import java.time.Instant

/**
 * A finished workout as the history list shows it (ROADMAP P1.6).
 *
 * The totals arrive pre-computed from SQL — see `WorkoutDao.observeHistory` — so
 * the list can render a year of training without loading a single set row.
 *
 * [volumeGrams] is gram-reps: the sum of `weight × reps` across live sets. It is
 * a rough proxy for work done, not a physical quantity, and is deliberately in
 * the same exact unit as the weights themselves rather than converted to a
 * `Double` tonne figure.
 */
data class WorkoutSummary(
    val id: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeGrams: Long,
) {
    /** Null while the workout is still open; history only holds finished ones. */
    val duration: Duration? get() = finishedAt?.let { Duration.between(startedAt, it) }

    /** Warm-up-only workouts have no weight to speak of, but they did happen. */}
