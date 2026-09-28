package com.example.androidapp.ui.workout

import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.PreviousPerformance

/** The values a new set will be logged with, before the user adjusts them. */
data class SetSuggestion(val reps: Int, val weightGrams: Long)

/**
 * Chooses what to prefill the next set with (ROADMAP P1.3).
 *
 * The ordering is the whole point of the feature: repeating what you *just* did
 * is almost always right within a session, and what you did last time is the
 * right starting point for the first set. Only when there is no history at all
 * does it fall back to a default.
 *
 * Pure, so the precedence is covered by fast JVM tests rather than by tapping.
 */
fun suggestionForNextSet(
    loggedSets: List<SetRow>,
    previous: PreviousPerformance?,
    nextIndex: Int,
): SetSuggestion = when {
    // Repeating what you just did is almost always right within a session.
    loggedSets.isNotEmpty() -> loggedSets.last().let { SetSuggestion(it.reps, it.weightGrams) }
    // Otherwise start from what you did last time you trained this.
    previous?.at(nextIndex) != null -> previous.at(nextIndex)!!.let { SetSuggestion(it.reps, it.weightGrams) }
    else -> SetSuggestion(reps = DEFAULT_REPS, weightGrams = Weight.DEFAULT_GRAMS)
}

/** Typical working-set reps when there is nothing to go on. */
const val DEFAULT_REPS = 8
