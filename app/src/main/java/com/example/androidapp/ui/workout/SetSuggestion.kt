package com.example.androidapp.ui.workout

import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.TemplateExercise

/** The values a new set will be logged with, before the user adjusts them. */
data class SetSuggestion(val reps: Int, val weightGrams: Long)

/**
 * What a plan prescribes for one set, or nulls where it prescribes nothing
 * (ROADMAP N14).
 *
 * Both fields nullable, because a plan may say "work up to a heavy single" and mean
 * it: there is no weight to prefill, and a zero would be a claim.
 */
data class PlannedTarget(val reps: Int?, val weightGrams: Long?)

/**
 * Chooses what to prefill the next set with (ROADMAP P1.3, N14).
 *
 * The ordering is the whole point of the feature. A plan's target for *this* set wins
 * where it says something — that is what a plan is for, and a ramp of 100/105/110 kg
 * only works if the second and third sets take their numbers from the plan rather than
 * from the set before them. Where the plan is silent, the older rule applies:
 * repeating what you *just* did is almost always right within a session, and what you
 * did last time is the right starting point for the first set. Only with no other
 * source does it fall back to a default.
 *
 * Pure, so the precedence is covered by fast JVM tests rather than by tapping.
 */
fun suggestionForNextSet(
    loggedSets: List<SetRow>,
    previous: PreviousPerformance?,
    nextIndex: Int,
    planned: PlannedTarget? = null,
): SetSuggestion {
    val withoutPlan = when {
        // Repeating what you just did is almost always right within a session.
        loggedSets.isNotEmpty() -> loggedSets.last().let { SetSuggestion(it.reps, it.weightGrams) }
        // Otherwise start from what you did last time you trained this.
        previous?.at(nextIndex) != null ->
            previous.at(nextIndex)!!.let { SetSuggestion(it.reps, it.weightGrams) }

        else -> SetSuggestion(reps = DEFAULT_REPS, weightGrams = Weight.DEFAULT_GRAMS)
    }
    if (planned == null) return withoutPlan

    return SetSuggestion(
        // The upper bound is the one that matters in a written plan (`max 2`).
        reps = planned.reps ?: withoutPlan.reps,
        weightGrams = planned.weightGrams ?: withoutPlan.weightGrams,
    )
}

/**
 * What the plan prescribes for the next set of the exercise at [position] (N14).
 *
 * Matched by position rather than by exercise id: a plan may contain the same movement
 * twice, and the order is what the session was seeded from. A plan that has nothing to
 * say about this set — or no plan at all — is null, which leaves the older rule below
 * to decide.
 */
fun plannedTargetFor(
    planned: List<TemplateExercise>,
    position: Int,
    nextIndex: Int,
): PlannedTarget? = planned.firstOrNull { it.position == position }
    ?.sets
    ?.firstOrNull { it.setIndex == nextIndex }
    ?.let { set ->
        // The upper bound is the one a written plan means (`max 2`).
        PlannedTarget(
            reps = set.targetRepsMax ?: set.targetRepsMin,
            weightGrams = set.targetWeightGrams,
        )
    }

/** Typical working-set reps when there is nothing to go on. */
const val DEFAULT_REPS = 8
