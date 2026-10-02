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

/**
 * The values a new set will be logged with, before the user adjusts them.
 *
 * These are what one tap of **Log set** commits, so the rule for choosing them is "what did I do, or
 * what does the plan say" — never "what does the app think I should do next" (ROADMAP N33). The app's
 * proposal travels in [offer], shown and applied only when it is accepted.
 */
data class SetSuggestion(
    val reps: Int,
    val weightGrams: Long,
    /** The assistance to prefill, or 0 for none (ROADMAP N15). */
    val assistanceGrams: Long = 0,
    /**
     * The progression the app proposes, or null (ROADMAP N33).
     *
     * Separate from the values above rather than folded into them, which is what this field exists to
     * stop: a proposal that *is* the prefill is a suggestion only until the user notices it, because the
     * next tap commits it. Null means there is nothing to propose — the plan already said, or there is
     * no last time — and never "no reason", which is [SetOffer.reason]'s job.
     */
    val offer: SetOffer? = null,
)

/**
 * A progression the app proposes, with why (ROADMAP N22, N33).
 *
 * Shown beside the set and applied only when accepted, so a lifter who progresses by hand is no longer
 * undoing the app's step on every first set.
 */
data class SetOffer(
    val reps: Int,
    val weightGrams: Long,
    val assistanceGrams: Long,
    val reason: ProgressionReason?,
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
 * Chooses what to prefill the next set with, and what to propose beside it (ROADMAP P1.3, N14, N33).
 *
 * **The prefill is history, not a proposal.** A plan's target for *this* set wins where it says
 * something — a ramp of 100/105/110 kg only works if the second and third sets take their numbers from
 * the plan — then what you just did in this session, then **what you did last time, unchanged**, and only
 * then a default. The app's own idea of the next step travels in [SetSuggestion.offer] and is applied
 * only when it is accepted; folding it in here is what made a suggestion into a decision, because one tap
 * committed it.
 *
 * Pure, so the precedence is covered by fast JVM tests rather than by tapping.
 */
fun suggestionForNextSet(
    loggedSets: List<SetRow>,
    previous: PreviousPerformance?,
    planned: PlannedTarget? = null,
): SetSuggestion {
    // "What you just did" beats "what you did last time": within a session the set before is the best
    // evidence there is.
    val logged = loggedSets.lastOrNull()
    val lastTime = previous?.sets?.lastOrNull()
    val prefill = when {
        logged != null -> SetSuggestion(logged.reps, logged.weightGrams, logged.assistanceGrams)
        lastTime != null -> SetSuggestion(
            reps = lastTime.reps,
            weightGrams = lastTime.weightGrams,
            assistanceGrams = lastTime.assistanceGrams,
        )

        else -> SetSuggestion(reps = DEFAULT_REPS, weightGrams = Weight.DEFAULT_GRAMS)
    }

    // A plan's load is *one* number: `-20` is 20 kg of help and no added weight, and `100` is 100 kg and
    // no help. Taking the half it names and the other half from the fallback would build a set that is
    // both — which would count the default 20 kg as volume on an assisted set, the exact corruption a
    // signed weight was rejected for (ROADMAP N15).
    val plannedLoad = planned?.let { plannedLoadFor(it) }

    // What to propose. A plan that names the load has already decided, so there is nothing to offer; a
    // plan that writes reps but no load leaves the load open, and the history is what answers.
    val proposal = when {
        !previous.hasSets() -> null
        planned != null && plannedLoad == null -> progressionFrom(
            previous = previous!!,
            target = PlannedSetSpec(role = SetType.NORMAL, minReps = null, maxReps = planned.reps),
        )

        planned == null -> progressionFrom(previous!!)
        else -> null
    }

    return SetSuggestion(
        // The upper bound is the one that matters in a written plan (`max 2`).
        reps = planned?.reps ?: prefill.reps,
        weightGrams = plannedLoad?.weightGrams ?: prefill.weightGrams,
        assistanceGrams = plannedLoad?.assistanceGrams ?: prefill.assistanceGrams,
        offer = proposal,
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
 * The next step from the last session, as an offer (ROADMAP N22, N33).
 *
 * One place, because the offer is the same question in two situations: with no plan at all, and with a
 * plan that writes reps but no load.
 */
private fun progressionFrom(
    previous: PreviousPerformance,
    target: PlannedSetSpec? = null,
): SetOffer {
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
    return SetOffer(
        reps = proposed.reps,
        weightGrams = proposed.weightGrams,
        assistanceGrams = proposed.assistanceGrams,
        reason = proposed.reason,
    )
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
