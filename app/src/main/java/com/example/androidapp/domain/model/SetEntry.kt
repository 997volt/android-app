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
    val completedAt: Instant? = null,
)

/** What kind of set this was. Warm-ups must not count towards PRs (P2.2). */
enum class SetType(val label: String) {
    NORMAL("Working"),
    WARMUP("Warm-up"),
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
