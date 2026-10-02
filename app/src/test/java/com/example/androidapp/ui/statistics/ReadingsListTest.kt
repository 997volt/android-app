package com.example.androidapp.ui.statistics

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the readings list shows, and what it leaves out (ROADMAP N36). */
class ReadingsListTest {

    private val august = Instant.parse("2026-08-01T08:00:00Z")
    private val september = Instant.parse("2026-09-01T08:00:00Z")
    private val october = Instant.parse("2026-10-01T08:00:00Z")

    private fun series(vararg readings: MetricReading) =
        MetricSeries(key = MetricKey.Body(BodyMetric.WEIGHT), readings = readings.toList())

    @Test
    fun theListIsNewestFirst() {
        val list = series(
            MetricReading(august, 83_000.0),
            MetricReading(october, 81_000.0),
            MetricReading(september, 82_000.0),
        ).asReadings()

        assertEquals(listOf(october, september, august), list.map { it.at })
        assertEquals(listOf(81_000.0, 82_000.0, 83_000.0), list.map { it.value })
    }

    @Test
    fun aMomentThatRecordedNothing_isNotAReading() {
        // The chart draws it as a gap; a list of numbers with holes in it would be a list of holes.
        val list = series(
            MetricReading(august, 83_000.0),
            MetricReading(september, null),
            MetricReading(october, 81_000.0),
        ).asReadings()

        assertEquals(2, list.size)
        assertEquals(listOf(october, august), list.map { it.at })
    }

    @Test
    fun aSeriesWithNothingInIt_isAnEmptyListRatherThanAnError() {
        assertEquals(emptyList<Reading>(), series(MetricReading(august, null)).asReadings())
        assertEquals(emptyList<Reading>(), series().asReadings())
    }

    @Test
    fun theAverage_isOverTheReadingsThatExist() {
        // Not over the moments: a week nobody weighed in must not pull the average towards zero.
        val average = series(
            MetricReading(august, 80_000.0),
            MetricReading(september, null),
            MetricReading(october, 82_000.0),
        ).average()

        assertEquals(81_000.0, average!!, 0.0001)
    }

    @Test
    fun anAverageOfNothing_isNullRatherThanZero() {
        assertNull(series().average())
        assertNull(series(MetricReading(august, null)).average())
    }
}
