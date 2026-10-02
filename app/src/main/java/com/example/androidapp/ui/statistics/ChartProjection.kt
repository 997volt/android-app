package com.example.androidapp.ui.statistics

import com.example.androidapp.ui.components.ChartPoint
import java.time.Instant

/**
 * Places a series horizontally by elapsed time (ROADMAP N37).
 *
 * The distortion this removes is real: spreading points by index drew two workouts a day apart and two a
 * month apart the same distance apart, so a month off looked like a day off and a hard week looked like a
 * hard quarter. Time is what the x-axis means.
 *
 * A moment that recorded nothing stays in the sequence with a null value: it is a place on the axis and a
 * break in the line, and dropping it would silently close the gap the chart exists to show.
 *
 * A single reading, or several that share an instant, sit at the middle — there is no span to place them
 * across, and putting the only point at the left edge would read as "the start of something".
 */
fun projectByTime(readings: List<MetricReading>): List<ChartPoint> {
    if (readings.isEmpty()) return emptyList()

    val first = readings.first().at
    val last = readings.last().at
    val span = last.toEpochMilli() - first.toEpochMilli()

    return readings.map { reading ->
        ChartPoint(
            x = if (span <= 0L) MIDDLE else fractionOf(reading.at, first, span),
            value = reading.value,
        )
    }
}

private fun fractionOf(at: Instant, first: Instant, span: Long): Float =
    ((at.toEpochMilli() - first.toEpochMilli()).toDouble() / span).toFloat().coerceIn(0f, 1f)

/** The middle of an axis with no span: one reading, or several at the same instant. */
private const val MIDDLE = 0.5f
