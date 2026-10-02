package com.example.androidapp.ui.statistics

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import org.junit.Test

/**
 * The trailing mean, and what its period counts (ROADMAP N40).
 *
 * The bodyweight case is the one this exists for: daily weight is noise, and the mean of the last week is the
 * signal under it.
 */
class MovingAverageTest {

    private val start = Instant.parse("2026-09-01T08:00:00Z")

    private fun series(vararg values: Double?) = MetricSeries(
        key = MetricKey.Body(BodyMetric.WEIGHT),
        readings = values.mapIndexed { index, value ->
            MetricReading(start.plusSeconds(index * 86_400L), value)
        },
    )

    @Test
    fun eachPointIsTheMeanOfThePeriodBehindIt() {
        // 1, 2, 3, 4 over a period of two: 1, 1.5, 2.5, 3.5.
        val average = series(1.0, 2.0, 3.0, 4.0).movingAverage(period = 2)

        assertThat(average.map { it.value }).isEqualTo(listOf(1.0, 1.5, 2.5, 3.5))
    }

    @Test
    fun itEmitsFromTheFirstReading_ratherThanWaitingForAFullPeriod() {
        // A chart that began a week in would hide the first week of the signal it was added to show.
        val average = series(80.0, 82.0).movingAverage(period = 7)

        assertThat(average.size).isEqualTo(2)
        assertThat(average.first().value!!).isWithin(0.0001).of(80.0)
        assertThat(average.last().value!!).isWithin(0.0001).of(81.0)
    }

    @Test
    fun aMomentThatRecordedNothing_neitherCountsNorDrags() {
        // Three readings with a gap between them: the fourth point is the mean of those three, not of four
        // moments one of which was nothing.
        val average = series(80.0, null, 82.0, 84.0).movingAverage(period = 3)

        assertThat(average.size).isEqualTo(3)
        assertThat(average.last().value!!).isWithin(0.0001).of(82.0)
        assertWithMessage("the gap is not a point on the average either").that(average.map { it.at })
            .isEqualTo(listOf(start, start.plusSeconds(2 * 86_400L), start.plusSeconds(3 * 86_400L)))
    }

    @Test
    fun aPeriodOfOne_isTheReadingsThemselves() {
        assertThat(series(80.0, 82.0).movingAverage(period = 1).map { it.value }).isEqualTo(listOf(80.0, 82.0))
    }

    @Test
    fun aPeriodBelowOne_isNothingRatherThanAWindowOfEverything() {
        assertThat(series(80.0, 82.0).movingAverage(period = 0)).isEqualTo(emptyList<MetricReading>())
        assertThat(series(80.0, 82.0).movingAverage(period = -3)).isEqualTo(emptyList<MetricReading>())
    }

    @Test
    fun theDefaultIsSeven() {
        assertThat(DAYS).isEqualTo(7)
        assertThat(MOVING_AVERAGE_PERIODS.first()).isEqualTo(DAYS)
    }
}
