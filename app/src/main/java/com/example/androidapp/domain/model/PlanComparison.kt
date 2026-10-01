package com.example.androidapp.domain.model

/**
 * A plan and what actually happened, side by side (ROADMAP N20).
 *
 * The app has written plans since N14 and logged performed sets since v1, and has never
 * compared them: targets only mattered *during* a workout, so the payoff for planning — the
 * review — was missing. This is that comparison, as values rather than sentences, because
 * the wording belongs to the screen and the arithmetic belongs here.
 *
 * **Warm-up sets are excluded from both sides**, for the reason N17 excluded them from a
 * load series: a warm-up is not the work the plan prescribes, and letting one stand in for
 * the top set would flatter every comparison.
 */

/** One planned set, reduced to what a comparison needs. */
data class PlannedSetSpec(
    val role: SetType,
    val weightGrams: Long? = null,
    val assistanceGrams: Long? = null,
    val minReps: Int? = null,
    val maxReps: Int? = null,
)

/** One performed set, reduced the same way. */
data class PerformedSetSpec(
    val role: SetType,
    val weightGrams: Long,
    val assistanceGrams: Long = 0,
    val reps: Int,
)

/** An exercise as the plan prescribed it. */
data class ExercisePlan(val name: String, val sets: List<PlannedSetSpec>)

/** An exercise as it was performed. */
data class ExerciseActual(val name: String, val sets: List<PerformedSetSpec>)

/**
 * One exercise's plan next to its performance.
 *
 * Every nullable field is honestly nullable: a plan may prescribe reps without a weight, a
 * session may log a lift the plan never mentioned, and [topSetDeltaGrams] is null when
 * there is nothing to subtract — rather than a zero that would read as "matched".
 */
data class PlanComparison(
    val name: String,
    val prescribedSets: Int,
    val performedSets: Int,
    val prescribedReps: Int?,
    val performedReps: Int,
    val prescribedTopWeightGrams: Long?,
    val performedTopWeightGrams: Long?,
    /** Performed minus prescribed on the heaviest working set, or null if either is unknown. */
    val topSetDeltaGrams: Long?,
) {
    /** True when the plan had nothing to compare — an exercise added mid-workout. */
    val wasPlanned: Boolean get() = prescribedSets > 0

    /** True when the top set landed exactly where the plan put it. */
    val matchedPlan: Boolean get() = topSetDeltaGrams == 0L
}

/**
 * Compares a plan to a performance, exercise by exercise, in the plan's order.
 *
 * Exercises that were performed but never planned come last, so the first thing read is the
 * plan being answered rather than the improvisation.
 */
fun comparePlanToActual(
    planned: List<ExercisePlan>,
    performed: List<ExerciseActual>,
): List<PlanComparison> {
    val actualByName = performed.associateBy { it.name }
    val plannedNames = planned.map { it.name }.toSet()

    val answered = planned.map { plan ->
        comparisonOf(plan.name, plan.sets, actualByName[plan.name]?.sets.orEmpty())
    }
    val improvised = performed
        .filterNot { it.name in plannedNames }
        .map { comparisonOf(it.name, emptyList(), it.sets) }

    return answered + improvised
}

private fun comparisonOf(
    name: String,
    plannedSets: List<PlannedSetSpec>,
    performedSets: List<PerformedSetSpec>,
): PlanComparison {
    val working = { role: SetType -> role != SetType.WARMUP }
    val plannedWork = plannedSets.filter { working(it.role) }
    val performedWork = performedSets.filter { working(it.role) }

    // The heaviest working set on each side: a light back-off set must not stand in for the
    // top set any more than a warm-up may.
    val prescribedTop = plannedWork.mapNotNull { it.weightGrams }.maxOrNull()
    val performedTop = performedWork.maxOfOrNull { it.weightGrams }

    return PlanComparison(
        name = name,
        prescribedSets = plannedSets.size,
        performedSets = performedSets.size,
        prescribedReps = plannedWork.mapNotNull { it.maxReps }.sum().takeIf { plannedWork.isNotEmpty() },
        performedReps = performedWork.sumOf { it.reps },
        prescribedTopWeightGrams = prescribedTop,
        performedTopWeightGrams = performedTop,
        topSetDeltaGrams = if (prescribedTop == null || performedTop == null) {
            null
        } else {
            performedTop - prescribedTop
        },
    )
}

