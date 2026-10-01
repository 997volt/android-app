package com.example.androidapp.domain.model

/**
 * What to do next time, worked out from what was done last time (ROADMAP N22).
 *
 * **Double progression**, which is the rule most programmes actually use and the only one
 * this app's data can support honestly: keep the load and add a rep until the top of the plan's
 * rep range is reached, then add the smallest loadable step and start the range again.
 *
 * It *suggests*; it never writes. Nothing here changes a plan or a set — a suggestion the app
 * silently applied would be a programme decision taken without the lifter, and this app is a
 * log, not a coach. The rule is stated once, in [suggestProgression], so the number on a
 * screen can be argued with rather than guessed at.
 */

/** The smallest step worth adding to a load, in grams: a pair of 1.25 kg plates. */
const val DEFAULT_PROGRESSION_STEP_GRAMS = 2_500L

/** Why the suggestion says what it says, so a screen can explain itself. */
enum class ProgressionReason {
    /** Nothing recorded for this exercise yet — there is nothing to progress from. */
    NO_HISTORY,

    /** The last top set left room in the rep range: same load, one more rep. */
    MORE_REPS,

    /** The top of the range was reached: a step heavier, back to the bottom of the range. */
    MORE_WEIGHT,

    /** Assisted work: less help is the progress, so the machine's share comes down. */
    LESS_ASSISTANCE,
}

/**
 * A proposed next set.
 *
 * [weightGrams] and [assistanceGrams] are the same split the rest of the app stores, and
 * [reps] is what to aim for. [reason] is what to tell the user, because a suggestion without
 * its reason is an instruction.
 */
data class ProgressionSuggestion(
    val weightGrams: Long,
    val assistanceGrams: Long,
    val reps: Int,
    val reason: ProgressionReason,
) {
    /** False when there was no history and no target — the screen then says nothing. */
    val isUsable: Boolean
        get() = reason != ProgressionReason.NO_HISTORY || reps > 0 || weightGrams > 0 || assistanceGrams > 0
}

/**
 * The next set, from the last session's working sets and the plan's target.
 *
 * Warm-up sets are ignored on both sides, for the reason N17 and N20 ignore them: a warm-up
 * is not what progress is measured against, and letting one set the next target would have
 * the app asking for a step on top of a bar it was only ever warming up with.
 */
fun suggestProgression(
    lastTime: List<PerformedSetSpec>,
    target: PlannedSetSpec? = null,
    stepGrams: Long = DEFAULT_PROGRESSION_STEP_GRAMS,
): ProgressionSuggestion {
    val performed = lastTime
        .filter { it.role != SetType.WARMUP }
        .maxByOrNull { it.weightGrams }

    val ceiling = target?.maxReps
    return when {
        // Nothing to go on: echo the plan if there is one, so the first session is not blank.
        performed == null -> ProgressionSuggestion(
            weightGrams = target?.weightGrams ?: 0L,
            assistanceGrams = target?.assistanceGrams ?: 0L,
            reps = target?.maxReps ?: target?.minReps ?: 0,
            reason = ProgressionReason.NO_HISTORY,
        )

        // The ceiling is tested *first*, which is what double progression means (ROADMAP B26).
        // The assisted branch used to come before it, so an assisted lifter at the *bottom* of a
        // range was told to take help off and keep the reps — "add a rep first" was unreachable
        // for assisted work, contradicting the rule this file states.
        ceiling != null && performed.reps >= ceiling && performed.assistanceGrams > 0L ->
            ProgressionSuggestion(
                weightGrams = performed.weightGrams,
                // Assisted work inverts the load direction: the machine doing less is the
                // progress, so a reached ceiling comes off the assistance, not onto a bar.
                assistanceGrams = (performed.assistanceGrams - stepGrams).coerceAtLeast(0L),
                reps = target?.minReps ?: performed.reps,
                reason = ProgressionReason.LESS_ASSISTANCE,
            )

        ceiling != null && performed.reps >= ceiling -> ProgressionSuggestion(
            weightGrams = performed.weightGrams + stepGrams,
            assistanceGrams = 0L,
            // Back to the floor the plan wrote, or the reps just done when it wrote only a
            // ceiling: inventing a number would be the app programming.
            reps = target?.minReps ?: performed.reps,
            reason = ProgressionReason.MORE_WEIGHT,
        )

        else -> ProgressionSuggestion(
            weightGrams = performed.weightGrams,
            // The assistance is carried, not dropped: below the ceiling this is a rep to add, and
            // zeroing the help here would silently turn an assisted set into a bodyweight one.
            assistanceGrams = performed.assistanceGrams,
            reps = performed.reps + 1,
            reason = ProgressionReason.MORE_REPS,
        )
    }
}
