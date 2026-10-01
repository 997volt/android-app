@file:Suppress("MatchingDeclarationName")

package com.example.androidapp.domain.model

/**
 * A ramp of warm-up sets computed from a working weight (ROADMAP N28).
 *
 * Warm-ups became *expressible* when N14 gave a set a role, and writable when a plan could hold them;
 * this is what makes writing them one action instead of five. It computes targets and nothing else —
 * the caller decides whether to add them to a plan, and the app never adds a set behind the user's
 * back.
 */

/** One warm-up: what to put on the bar and how many times. */
data class WarmUpTarget(
    /** The fraction of the working weight, as a person would say it: 0.4 is "40%". */
    val fraction: Double,
    val weightGrams: Long,
    val reps: Int,
)

/** The fraction of the working weight, and the reps to do at it. */
private typealias RampStep = Pair<Double, Int>

/**
 * The ramp for a working weight, lightest first.
 *
 * Four sets — 40%, 60%, 75% and 85% for descending reps — which is the shape most lifters warm up
 * with, and deliberately not more: a generator that adds eight warm-ups to every exercise makes a
 * plan that takes an hour to read.
 *
 * **Rounded to 2.5 kg**, the same step the progression rule uses, because a warm-up of 61.7 kg is not
 * a weight anybody can load. The rounding can push two steps onto the same number on a light working
 * weight, and identical consecutive warm-ups are dropped rather than shown twice: a bar loaded with
 * the same weight twice is one warm-up, not two.
 *
 * A bodyweight exercise has no working weight to take a fraction of — [workingWeightGrams] is zero
 * for one — so it gets **no ramp at all** rather than a list of zeroes to load. That is N15's rule
 * about what a bodyweight set carries, applied to generating one.
 */
fun warmUpRamp(
    workingWeightGrams: Long,
    stepGrams: Long = DEFAULT_PROGRESSION_STEP_GRAMS,
): List<WarmUpTarget> {
    if (workingWeightGrams <= 0L) return emptyList()

    return WARM_UP_SHAPE
        .map { (fraction, reps) ->
            WarmUpTarget(
                fraction = fraction,
                weightGrams = roundToStep(workingWeightGrams * fraction, stepGrams),
                reps = reps,
            )
        }
        .filter { it.weightGrams in 1 until workingWeightGrams }
        .distinctBy { it.weightGrams }
}

/** The ramp's shape: a fraction of the working weight, and the reps to do at it. */
private val WARM_UP_SHAPE: List<RampStep> = listOf(
    WARM_UP_FIRST to FIVE_REPS,
    WARM_UP_SECOND to THREE_REPS,
    WARM_UP_THIRD to TWO_REPS,
    WARM_UP_LAST to ONE_REP,
)

private const val WARM_UP_FIRST = 0.4
private const val WARM_UP_SECOND = 0.6
private const val WARM_UP_THIRD = 0.75
private const val WARM_UP_LAST = 0.85
private const val FIVE_REPS = 5
private const val THREE_REPS = 3
private const val TWO_REPS = 2
private const val ONE_REP = 1

/** The nearest loadable weight, never below one step: a zero would be an empty bar with a claim. */
private fun roundToStep(grams: Double, stepGrams: Long): Long {
    val steps = Math.round(grams / stepGrams)
    return (steps * stepGrams).coerceAtLeast(stepGrams)
}
