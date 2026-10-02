package com.example.androidapp.ui.statistics

/**
 * The ends of a chart's value axis (ROADMAP N38).
 *
 * A policy rather than arithmetic, which is why it is a function with a name: `fromZero` and `fixedRange` are
 * properties of the *metric*, not of the data. A load is a quantity, and a weight axis starting at the
 * lightest set would exaggerate every change; a rating is a position on a scale, and starting it at zero
 * would flatten the differences the ratings exist to show while fitting it to the data would turn a 0.2
 * wobble into a cliff.
 */
data class AxisBounds(val min: Double, val max: Double)

/**
 * Where an axis starts and ends for [values] (ROADMAP N38).
 *
 * The padding is a tenth of the span so a line at the top edge is not mistaken for a ceiling, and a series
 * where every reading is equal gets a range anyway rather than collapsing — except when the axis starts at
 * zero, where the top is the reading itself plus the same padding and the bottom is zero by definition.
 *
 * [fixedRange] is the span a metric never narrows below, in the metric's *display* units: a rating is
 * definitionally 1–10, so its axis keeps those ends however tightly the readings cluster. The axis still
 * widens past the range, because a target outside it is a claim the user made and it has to be visible.
 *
 * Non-finite input is discarded rather than propagated. One `NaN` — from a typed target, say — would
 * otherwise make both ends `NaN`, and `NaN.coerceIn(0f, 1f)` is still `NaN`, so every coordinate on the
 * canvas would become `NaN` and the chart would draw nothing while reporting no error at all.
 */
fun axisBounds(
    values: List<Double>,
    fromZero: Boolean,
    goal: Double? = null,
    fixedRange: ClosedRange<Double>? = null,
): AxisBounds {
    // The target is part of the axis, not something drawn outside it: a target you cannot see is not a target,
    // and pinning the line to the edge of the chart hides it exactly when it is furthest from the readings —
    // which is when it is most worth seeing. The cost is that a target far from the data compresses the
    // readings, and that is the right way round: the readings are still there, and are read against it.
    val plotted = (values + listOfNotNull(goal)).filter { it.isFinite() }
    val lowest = plotted.minOrNull()
    val highest = plotted.maxOrNull()

    val floor = fixedRange?.start
    val ceiling = fixedRange?.endInclusive
    if (lowest == null || highest == null) {
        // Nothing drawable: a fixed scale still holds, so an empty rating chart reads 1–10 rather than 0–1.
        return if (floor != null && ceiling != null) AxisBounds(floor, ceiling) else AxisBounds(0.0, 1.0)
    }

    val span = highest - lowest
    val pad = if (span == 0.0) 1.0 else span * AXIS_PAD_FRACTION

    return when {
        // A fixed range keeps its own ends, widened only if a reading or a target left them.
        floor != null && ceiling != null -> AxisBounds(
            min = minOf(floor, lowest),
            max = maxOf(ceiling, highest + pad),
        )

        fromZero -> AxisBounds(
            // Zero is the baseline *unless* something sits below it: a negative target stays inside the axis
            // rather than pinned to the bottom edge, which is the symptom N39 fixed for targets that point
            // upwards. The padding keeps it off the edge, and the baseline stays exactly zero when nothing
            // is below it, so a bar chart's lengths are unchanged.
            min = if (lowest < 0.0) lowest - pad else 0.0,
            max = highest + pad,
        )

        else -> AxisBounds(min = lowest - pad, max = highest + pad)
    }
}

/** Room above and below the line, so a reading at the edge is not mistaken for a ceiling. */
private const val AXIS_PAD_FRACTION = 0.1
