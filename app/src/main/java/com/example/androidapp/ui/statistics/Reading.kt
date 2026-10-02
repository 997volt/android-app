package com.example.androidapp.ui.statistics

import java.time.Instant

/** One reading as the list shows it: when it was taken, and what it was. */
data class Reading(val at: Instant, val value: Double)

/**
 * The readings a list can show, newest first (ROADMAP N36).
 *
 * A moment that recorded nothing is not a reading: the chart draws it as a gap, and a list of numbers with
 * holes in it would be a list of holes. So this drops the nulls the series keeps — which is why the series
 * keeps them and this does not.
 */
fun MetricSeries.asReadings(): List<Reading> = readings
    .mapNotNull { reading -> reading.value?.let { Reading(reading.at, it) } }
    .sortedByDescending { it.at }

/**
 * The mean of the recorded readings, or null when there are none (ROADMAP N36).
 *
 * Null rather than zero for the same reason the overview's record count is: an average of nothing is not
 * zero, and a screen showing 0.0 for a lift nobody has performed would be inventing a measurement.
 */
fun MetricSeries.average(): Double? {
    val recorded = readings.mapNotNull { it.value }
    return if (recorded.isEmpty()) null else recorded.average()
}
