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
fun axisBounds(values: List<Double>, fromZero: Boolean): AxisBounds {
    if (values.isEmpty()) return AxisBounds(0.0, 1.0)

    val lowest = values.min()
    val highest = values.max()
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
