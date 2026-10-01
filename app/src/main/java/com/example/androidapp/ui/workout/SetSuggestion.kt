package com.example.androidapp.ui.workout

import com.example.androidapp.domain.model.ProgressionReason
import com.example.androidapp.domain.model.suggestProgression
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.PlannedSetSpec
import com.example.androidapp.domain.model.PerformedSetSpec
import com.example.androidapp.domain.Load
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.TemplateExercise

/** The values a new set will be logged with, before the user adjusts them. */
data class SetSuggestion(
    val reps: Int,
    val weightGrams: Long,
    /** The assistance to prefill, or 0 for none (ROADMAP N15). */
    val assistanceGrams: Long = 0,
    /**
     * Why this number, when it came from progression (ROADMAP N22), or null when it is
     * simply the plan or a repeat of the set just logged.
     *
     * Null is not "no reason" — it is "nothing to explain", which is the common case and
     * must not put a line on the screen.
     */
    val reason: ProgressionReason? = null,
)

/**
 * What a plan prescribes for one set, or nulls where it prescribes nothing
 * (ROADMAP N14).
 *
 * Both fields nullable, because a plan may say "work up to a heavy single" and mean
 * it: there is no weight to prefill, and a zero would be a claim.
 */
data class PlannedTarget(
    val reps: Int?,
    val weightGrams: Long?,
    /** The assistance the plan prescribes, or null (ROADMAP N15). */
    val assistanceGrams: Long? = null,
)

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
    planned: PlannedTarget? = null,
): SetSuggestion {
    val withoutPlan = prefillWithoutPlan(loggedSets, previous)

    if (planned == null) return withoutPlan

    // A plan's load is *one* number: `-20` is 20 kg of help and no added weight, and
    // `100` is 100 kg and no help. Taking the half it names and the other half from
    // the fallback would build a set that is both — which would count the default
    // 20 kg as volume on an assisted set, the exact corruption a signed weight was
    // rejected for (ROADMAP N15).
    val plannedLoad = plannedLoadFor(planned)

    // A plan that writes reps but no load leaves the load to this rule as well (N22): the
    // prescription is what to *aim* for, and whether that means one more rep or one more
    // step is exactly what the history answers.
    val proposedForPlan = if (plannedLoad == null && previous.hasSets()) {
        progressionFrom(
            previous = previous!!,
            target = PlannedSetSpec(role = SetType.NORMAL, minReps = null, maxReps = planned.reps),
        )
    } else {
        null
    }

    return SetSuggestion(
        // The upper bound is the one that matters in a written plan (`max 2`).
        reps = planned.reps ?: withoutPlan.reps,
        weightGrams = plannedLoad?.weightGrams ?: proposedForPlan?.weightGrams ?: withoutPlan.weightGrams,
        assistanceGrams = plannedLoad?.assistanceGrams
            ?: proposedForPlan?.assistanceGrams
            ?: withoutPlan.assistanceGrams,
        // The reason explains the *load* here, and only when it is about the load: the plan
        // already decides the reps, so "one more rep than last time" beside a set the plan sized
        // would be a sentence about the wrong number. Null is the established "nothing to
        // explain" — found by B24's test, which is how the plan branch came to be passing the
        // rule's value away.
        reason = proposedForPlan?.reason?.takeIf { it != ProgressionReason.MORE_REPS }
            ?: plannedLoad?.let { null }
            ?: withoutPlan.reason?.takeIf { planned.reps == null },
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
            assistanceGrams = set.targetAssistanceGrams,
        )
    }

/** Typical working-set reps when there is nothing to go on. */
const val DEFAULT_REPS = 8

/** True when there is a last time worth progressing from. */
private fun PreviousPerformance?.hasSets(): Boolean = this != null && sets.isNotEmpty()

/**
 * The next step from the last session, as a prefill (ROADMAP N22).
 *
 * One place, because the prefill asks the same question in two situations: with no plan at
 * all, and with a plan that writes reps but no load.
 */
private fun progressionFrom(
    previous: PreviousPerformance,
    target: PlannedSetSpec? = null,
): SetSuggestion {
    val proposed = suggestProgression(
        lastTime = previous.sets.map {
            PerformedSetSpec(
                role = it.setType,
                weightGrams = it.weightGrams,
                assistanceGrams = it.assistanceGrams,
                reps = it.reps,
            )
        },
        target = target,
    )
    return SetSuggestion(
        reps = proposed.reps,
        weightGrams = proposed.weightGrams,
        assistanceGrams = proposed.assistanceGrams,
        reason = proposed.reason,
    )
}

/** What to prefill when the plan has nothing to say (or there is none). */
private fun prefillWithoutPlan(
    loggedSets: List<SetRow>,
    previous: PreviousPerformance?,
): SetSuggestion = when {
    // Repeating what you just did is almost always right within a session.
    loggedSets.isNotEmpty() -> loggedSets.last().let {
        SetSuggestion(it.reps, it.weightGrams, it.assistanceGrams)
    }

    previous.hasSets() -> progressionFrom(previous!!)

    else -> SetSuggestion(reps = DEFAULT_REPS, weightGrams = Weight.DEFAULT_GRAMS)
}

/**
 * The load a plan names, as the split the app stores.
 *
 * A plan's load is *one* number: `-20` is 20 kg of help and no added weight, and `100` is
 * 100 kg and no help. Taking the half it names and the other half from the fallback would
 * build a set that is both — which would count the default 20 kg as volume on an assisted
 * set, the exact corruption a signed weight was rejected for (ROADMAP N15).
 */
private fun plannedLoadFor(planned: PlannedTarget): Load? = when {
    planned.assistanceGrams != null && planned.assistanceGrams > 0L ->
        Load(weightGrams = 0L, assistanceGrams = planned.assistanceGrams)

    planned.weightGrams != null -> Load(planned.weightGrams, assistanceGrams = 0L)

    else -> null
}
