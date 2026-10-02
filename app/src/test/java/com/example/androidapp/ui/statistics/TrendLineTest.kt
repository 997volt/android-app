package com.example.androidapp.ui.statistics

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

        assertEquals("half a kilo a week", -500.0, trend.perWeek, 0.001)
        assertEquals(4, trend.points)
    }

    @Test
    fun aFlatSeries_hasNoSlope() {
        val trend = series(0L to 82_000.0, 1L to 82_000.0, 2L to 82_000.0).trend()!!

        assertEquals(0.0, trend.perWeek, 0.0001)
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

        assertEquals(0.0, trend.perWeek, 0.0001)
        assertEquals("three readings, not four moments", 3, trend.points)
    }

    @Test
    fun oneReading_isANumberRatherThanATrend() {
        assertNull(series(0L to 82_000.0).trend())
        assertNull(series(0L to 82_000.0, 1L to null).trend())
    }

    @Test
    fun readingsAtTheSameInstant_haveNoSlopeToSpeakOf() {
        assertNull(series(0L to 82_000.0, 0L to 83_000.0).trend())
    }

    @Test
    fun theSlopeText_carriesItsSign() {
        // Signed because a slope with no sign is half the information: the same number is progress on a
        // bodyweight and a warning on joint pain.
        assertEquals("−0.3", slopeText(-300.0) { String.format(java.util.Locale.ROOT, "%.1f", it / 1000) })
        assertEquals("+0.3", slopeText(300.0) { String.format(java.util.Locale.ROOT, "%.1f", it / 1000) })
        assertEquals("+0.0", slopeText(0.0) { String.format(java.util.Locale.ROOT, "%.1f", it / 1000) })
    }
}
