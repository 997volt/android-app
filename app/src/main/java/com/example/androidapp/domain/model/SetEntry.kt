package com.example.androidapp.domain.model

import java.time.Instant

/**
 * A single logged set (ROADMAP P1.3).
 *
 * [weightGrams] is whole grams — see `Weight` for why that beats kilograms as a
 * `Double`. [setIndex] is the position within its exercise, so a set keeps its
 * place even if an earlier one is deleted (the display renumbers; the identity
 * does not).
 */
data class SetEntry(
    val id: String,
    val sessionExerciseId: String,
    val setIndex: Int,
    val reps: Int,
    val weightGrams: Long,
    val setType: SetType = SetType.NORMAL,
    /** Perceived effort, 1–10, or null when none was recorded (ROADMAP N6). */
    val rpe: Int? = null,
    /**
     * A short comment on the set, or null (ROADMAP N6).
     *
     * Separate from `workout_sessions.notes`: one is about this set, the other
     * about the workout.
     */
    val note: String? = null,
    val completedAt: Instant? = null,
)

/** What kind of set this was. Warm-ups must not count towards PRs (P2.2). */
enum class SetType(val label: String) {
    NORMAL("Working"),
    WARMUP("Warm-up"),

    /**
     * The heavy single the rest of the session is built around (ROADMAP N14).
     *
     * Added for *planned* sets and available to performed ones, because a plan that
     * says "top set" and a log that cannot is two vocabularies for one idea. Stored
     * by name like every other enum, so adding it touched no row already on disk.
     */
    TOP_SET("Top set"),
    DROP("Drop"),
    FAILURE("Failure"),
}

/**
 * What this exercise looked like the last time it was trained.
 *
 * Used to prefill a new set (P1.3): the single most useful thing the app can put
 * on screen mid-workout is "last time you did 60 kg × 8". Empty means this
 * exercise has no completed history yet.
 */
data class PreviousPerformance(
    val sets: List<SetEntry>,
) {
    val isEmpty: Boolean get() = sets.isEmpty()

    /** The value to prefill set [index] with, falling back to the final set. */
    fun at(index: Int): SetEntry? = sets.getOrNull(index) ?: sets.lastOrNull()
}
