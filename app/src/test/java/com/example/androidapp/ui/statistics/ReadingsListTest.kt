package com.example.androidapp.ui.statistics

import com.google.common.truth.Truth.assertThat
import java.time.Instant
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

        assertThat(list.map { it.at }).isEqualTo(listOf(october, september, august))
        assertThat(list.map { it.value }).isEqualTo(listOf(81_000.0, 82_000.0, 83_000.0))
    }

    @Test
    fun aMomentThatRecordedNothing_isNotAReading() {
        // The chart draws it as a gap; a list of numbers with holes in it would be a list of holes.
        val list = series(
            MetricReading(august, 83_000.0),
            MetricReading(september, null),
            MetricReading(october, 81_000.0),
        ).asReadings()

        assertThat(list.size).isEqualTo(2)
        assertThat(list.map { it.at }).isEqualTo(listOf(october, august))
    }

    @Test
    fun aSeriesWithNothingInIt_isAnEmptyListRatherThanAnError() {
        assertThat(series(MetricReading(august, null)).asReadings()).isEqualTo(emptyList<Reading>())
        assertThat(series().asReadings()).isEqualTo(emptyList<Reading>())
    }

    @Test
    fun theAverage_isOverTheReadingsThatExist() {
        // Not over the moments: a week nobody weighed in must not pull the average towards zero.
        val average = series(
            MetricReading(august, 80_000.0),
            MetricReading(september, null),
            MetricReading(october, 82_000.0),
        ).average()

        assertThat(average!!).isWithin(0.0001).of(81_000.0)
    }

    @Test
    fun anAverageOfNothing_isNullRatherThanZero() {
        assertThat(series().average()).isNull()
        assertThat(series(MetricReading(august, null)).average()).isNull()
    }
}
