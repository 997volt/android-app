package com.example.androidapp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
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
    /** Bars for a count-like series, a line for a continuous one (ROADMAP N38). */
    bars: Boolean = false,
    /** A horizontal line at the average, and the fitted trend (ROADMAP N39). */
    average: Double? = null,
    trend: ChartLine? = null,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val referenceColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val axis = TrendAxis(minValue, maxValue)

        fun y(value: Double): Float = size.height * (1f - axis.fractionOf(value).toFloat())

        // The reference lines first, so the data is drawn over them: a trend line is a reading of the series,
        // not a thing to hide it behind.
        average?.let { value ->
            drawLine(
                color = referenceColor,
                start = Offset(0f, y(value)),
                end = Offset(size.width, y(value)),
                strokeWidth = REFERENCE_STROKE_PX,
            )
        }
        trend?.let { line ->
            drawLine(
                color = referenceColor,
                start = Offset(0f, y(line.start)),
                end = Offset(size.width, y(line.end)),
                strokeWidth = REFERENCE_STROKE_PX,
            )
        }

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

        if (bars) {
            drawBars(points = points, y = ::y, color = dotColor)
            return@Canvas
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
    bars: Boolean = false,
    average: Double? = null,
    trend: ChartLine? = null,
) {
    TrendChart(
        points = points,
        minValue = minValue,
        maxValue = maxValue,
        bars = bars,
        average = average,
        trend = trend,
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp)
            .padding(top = 12.dp)
            .testTag(testTag),
    )
}

/** A trend line drawn across the whole width: its value at the first reading and at the last. */
data class ChartLine(val start: Double, val end: Double)

/** Thinner than the data, because a fitted line is a summary rather than a measurement. */
private const val REFERENCE_STROKE_PX = 2f

/** A bar is a share of the width, not a sliver: two fifths of the space each bar has. */
private const val BAR_WIDTH_FRACTION = 2.5f

/** Below this a bar is invisible on a phone, however many readings there are. */
private const val MIN_BAR_WIDTH_PX = 2f

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

/**
 * Bars from the baseline up (ROADMAP N38).
 *
 * A separate function because it is the other way of drawing the same points, and because the chart was at its
 * length ceiling with both of them in it.
 */
private fun DrawScope.drawBars(points: List<ChartPoint>, y: (Double) -> Float, color: Color) {
    // A bar length is the quantity, so the bar has to start where the quantity starts — which is why a bars
    // metric's axis is anchored at zero by the registry.
    val width = (size.width / points.size).coerceAtLeast(MIN_BAR_WIDTH_PX) / BAR_WIDTH_FRACTION
    val baseline = y(0.0).coerceIn(0f, size.height)
    points.forEach { point ->
        val value = point.value ?: return@forEach
        drawRect(
            color = color,
            topLeft = Offset(point.x * size.width - width / 2f, minOf(y(value), baseline)),
            size = Size(width, kotlin.math.abs(baseline - y(value))),
        )
    }
}
