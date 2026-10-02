package com.example.androidapp.ui.statistics

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import org.junit.Test

/**
 * The least-squares line, and what it says per week (ROADMAP N39).
 *
 * The unit is the point: a slope per reading would report the same number for a change over a day and the same
 * change over a month.
 */
class TrendLineTest {

    private val week = 7 * 24 * 60 * 60 * 1000L
    private val start = Instant.parse("2026-09-01T08:00:00Z")

    private fun series(vararg pairs: Pair<Long, Double?>) = MetricSeries(
        key = MetricKey.Body(BodyMetric.WEIGHT),
        readings = pairs.map { (offset, value) -> MetricReading(start.plusMillis(offset * week), value) },
    )

    @Test
    fun aKiloAWeek_down_isMinusAKiloAWeek() {
        // Four weekly weigh-ins, each 0.5 kg under the last: half a kilo a week, not two kilos a month.
        val trend = series(
            0L to 83_000.0,
            1L to 82_500.0,
            2L to 82_000.0,
            3L to 81_500.0,
        ).trend()!!

        assertWithMessage("half a kilo a week").that(trend.perWeek).isWithin(0.001).of(-500.0)
        assertThat(trend.points).isEqualTo(4)
    }

    @Test
    fun aFlatSeries_hasNoSlope() {
        val trend = series(0L to 82_000.0, 1L to 82_000.0, 2L to 82_000.0).trend()!!

        assertThat(trend.perWeek).isWithin(0.0001).of(0.0)
    }

    @Test
    fun anUnmeasuredWeek_isNotAReadingAtZero() {
        // A month nobody weighed in must not pull the line down: only the readings that exist are fitted.
        val trend = series(
            0L to 82_000.0,
            1L to null,
            2L to 82_000.0,
            3L to 82_000.0,
        ).trend()!!

        assertThat(trend.perWeek).isWithin(0.0001).of(0.0)
        assertWithMessage("three readings, not four moments").that(trend.points).isEqualTo(3)
    }

    @Test
    fun oneReading_isANumberRatherThanATrend() {
        assertThat(series(0L to 82_000.0).trend()).isNull()
        assertThat(series(0L to 82_000.0, 1L to null).trend()).isNull()
    }

    @Test
    fun readingsAtTheSameInstant_haveNoSlopeToSpeakOf() {
        assertThat(series(0L to 82_000.0, 0L to 83_000.0).trend()).isNull()
    }

    @Test
    fun theSlopeText_carriesItsSign() {
        // Signed because a slope with no sign is half the information: the same number is progress on a
        // bodyweight and a warning on joint pain.
        assertThat(slopeText(-300.0) { String.format(java.util.Locale.ROOT, "%.1f", it / 1000) }).isEqualTo("−0.3")
        assertThat(slopeText(300.0) { String.format(java.util.Locale.ROOT, "%.1f", it / 1000) }).isEqualTo("+0.3")
        assertThat(slopeText(0.0) { String.format(java.util.Locale.ROOT, "%.1f", it / 1000) }).isEqualTo("+0.0")
    }
}
