package com.example.androidapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * A line over time, hand-drawn (ROADMAP N13, shared with N17).
 *
 * Two things it deliberately does not do. It does not interpolate across a missing
 * value: a gap is "not recorded", and drawing through it would invent a measurement.
 * And it does not choose its own axis — the caller passes the bounds, because a rating
 * and a load need opposite treatment. A 1–10 rating is drawn on a *fixed* axis so a 0.2
 * change cannot look like a cliff and the three ratings can be read against each other;
 * a load is drawn from zero to the data's own maximum, because a kilogram is a quantity
 * and a shared axis with a rating would be meaningless.
 *
 * Marked as decorative: the caption next to it carries the numbers, so a screen reader
 * hears the sentence rather than a description of a picture it cannot see.
 */
@Composable
fun TrendChart(
    values: List<Double?>,
    minValue: Double,
    maxValue: Double,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val axis = TrendAxis(minValue, maxValue)
        val step = if (values.size > 1) size.width / (values.size - 1) else 0f

        fun y(value: Double): Float = size.height * (1f - axis.fractionOf(value).toFloat())

        // The top and bottom of the axis, so an empty stretch still reads as a scale.
        listOf(minValue, maxValue).forEach { value ->
            drawLine(
                color = gridColor,
                start = Offset(0f, y(value)),
                end = Offset(size.width, y(value)),
                strokeWidth = GRID_STROKE_PX,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON_PX, DASH_OFF_PX)),
            )
        }

        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val next = values.getOrNull(index + 1)
            if (next != null) {
                drawLine(
                    color = lineColor,
                    start = Offset(step * index, y(value)),
                    end = Offset(step * (index + 1), y(next)),
                    strokeWidth = LINE_STROKE_PX,
                )
            }
            drawCircle(
                color = dotColor,
                radius = DOT_RADIUS_PX,
                center = Offset(step * index, y(value)),
            )
        }
    }
}

/** The chart's own frame: a fixed height, with the caller deciding the width. */
@Composable
fun TrendChartFrame(
    values: List<Double?>,
    minValue: Double,
    maxValue: Double,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    TrendChart(
        values = values,
        minValue = minValue,
        maxValue = maxValue,
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp)
            .padding(top = 12.dp)
            .testTag(testTag),
    )
}

/** The middle of a flat axis: a series whose values are all equal draws down the centre. */
private const val MID_AXIS = 0.5

/** Where a value sits on the axis, as a 0..1 fraction from the bottom. */
private class TrendAxis(private val min: Double, private val max: Double) {
    fun fractionOf(value: Double): Double =
        if (max - min == 0.0) MID_AXIS else ((value - min) / (max - min)).coerceIn(0.0, 1.0)
}

/** The 1–10 rating axis: fixed, so the three ratings read against each other. */
const val RATING_AXIS_MIN = 1.0
const val RATING_AXIS_MAX = 10.0

/** The chart's ink, in pixels: it is a fixed-size glyph, not a responsive layout. */
private const val GRID_STROKE_PX = 1f
private const val LINE_STROKE_PX = 3f
private const val DOT_RADIUS_PX = 4f
private const val DASH_ON_PX = 6f
private const val DASH_OFF_PX = 6f
private const val CHART_HEIGHT_DP = 72
