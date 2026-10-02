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
 * A line over time, hand-drawn (ROADMAP N13, N37).
 *
 * **Points arrive already placed horizontally**, as a fraction of the width, because where a reading sits is
 * a question about time and this component draws pictures (N37). Spreading them by index is what made two
 * workouts a day apart and two a month apart the same distance apart — a real distortion rather than a
 * detail, and one the chart could not fix from the inside because it never saw the dates.
 *
 * Two things it deliberately does not do. It does not interpolate across a missing
 * value: a gap is "not recorded", and drawing through it would invent a measurement. That matters more on a
 * time axis, not less: the gap is now visible as *distance*, and a straight segment drawn across three weeks
 * would be a claim about weeks nobody measured. And it does not choose its own axis — the caller passes the
 * bounds, because a rating and a load need opposite treatment: a 1–10 rating is drawn on a *fixed* axis so a
 * 0.2 change cannot look like a cliff, and a load from zero, because a kilogram is a quantity.
 *
 * Marked as decorative: the labels and the caption carry the numbers, so a screen reader hears the sentence
 * rather than a description of a picture it cannot see.
 */
@Composable
fun TrendChart(
    points: List<ChartPoint>,
    minValue: Double,
    maxValue: Double,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val axis = TrendAxis(minValue, maxValue)

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

        points.forEachIndexed { index, point ->
            val value = point.value ?: return@forEachIndexed
            val x = point.x * size.width
            // A segment needs both ends recorded, so a gap is a break rather than a straight line through
            // whatever nobody measured.
            val next = points.getOrNull(index + 1)?.value
            if (next != null) {
                drawLine(
                    color = lineColor,
                    start = Offset(x, y(value)),
                    end = Offset(points[index + 1].x * size.width, y(next)),
                    strokeWidth = LINE_STROKE_PX,
                )
            }
            drawCircle(
                color = dotColor,
                radius = DOT_RADIUS_PX,
                center = Offset(x, y(value)),
            )
        }
    }
}

/** The chart's own frame: a fixed height, with the caller deciding the width. */
@Composable
fun TrendChartFrame(
    points: List<ChartPoint>,
    minValue: Double,
    maxValue: Double,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    TrendChart(
        points = points,
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

/**
 * One reading's place in the chart (ROADMAP N37).
 *
 * [x] is a fraction of the width and [value] is what to draw there, or null for a moment that recorded
 * nothing — which is a point on the axis and a break in the line. Placement is the caller's, because it is a
 * question about time and this component never sees a date.
 */
data class ChartPoint(val x: Float, val value: Double?)
