package com.example.androidapp.ui.statistics

/**
 * The ends of a chart's value axis (ROADMAP N38).
 *
 * A policy rather than arithmetic, which is why it is a function with a name: `fromZero` is a property of the
 * *metric*, not of the data. A load is a quantity, and a weight axis starting at the lightest set would
 * exaggerate every change; a rating is a position on a scale, and starting it at zero would flatten the
 * differences the ratings exist to show.
 */
data class AxisBounds(val min: Double, val max: Double)

/**
 * Where an axis starts and ends for [values] (ROADMAP N38).
 *
 * The padding is a tenth of the span so a line at the top edge is not mistaken for a ceiling, and a series
 * where every reading is equal gets a range anyway rather than collapsing — except when the axis starts at
 * zero, where the top is the reading itself plus the same padding and the bottom is zero by definition.
 */
fun axisBounds(values: List<Double>, fromZero: Boolean, goal: Double? = null): AxisBounds {
    // The target is part of the axis, not something drawn outside it: a target you cannot see is not a target,
    // and pinning the line to the edge of the chart hides it exactly when it is furthest from the readings —
    // which is when it is most worth seeing. The cost is that a target far from the data compresses the
    // readings, and that is the right way round: the readings are still there, and are read against it.
    val plotted = values + listOfNotNull(goal)
    val lowest = plotted.minOrNull()
    val highest = plotted.maxOrNull()
    if (lowest == null || highest == null) return AxisBounds(0.0, 1.0)

    val span = highest - lowest
    val pad = if (span == 0.0) 1.0 else span * AXIS_PAD_FRACTION

    return if (fromZero) {
        AxisBounds(min = 0.0, max = highest + pad)
    } else {
        AxisBounds(min = lowest - pad, max = highest + pad)
    }
}

/** Room above and below the line, so a reading at the edge is not mistaken for a ceiling. */
private const val AXIS_PAD_FRACTION = 0.1
