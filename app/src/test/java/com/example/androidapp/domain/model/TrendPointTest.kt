package com.example.androidapp.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the trends screen (ROADMAP N13).
 *
 * Worth its own JVM test because every claim the screen makes — "latest", "average
 * over N workouts", "not recorded" — is one of these three functions, and a chart is
 * the kind of thing that is believed rather than checked.
 */
class TrendPointTest {

    private fun point(
        rpeHalves: Double? = null,
        feel: Double? = null,
        pain: Double? = null,
    ) = TrendPoint(
        startedAt = Instant.parse("2026-09-28T08:00:00Z"),
        averageRpe = rpeHalves,
        averageMuscleFeel = feel,
        averageJointPain = pain,
    )

    @Test
    fun aMetric_takesItsOwnValueFromAPoint() {
        val only = point(rpeHalves = 7.5, feel = 6.0, pain = 2.0)

        assertEquals(7.5, TrendMetric.RPE.valueOf(only))
        assertEquals(6.0, TrendMetric.MUSCLE_FEEL.valueOf(only))
        assertEquals(2.0, TrendMetric.JOINT_PAIN.valueOf(only))
    }

    @Test
    fun notRecorded_isNull_ratherThanZero() {
        val only = point(rpeHalves = 7.5)

        // Zero would drag an average down and draw a line to the floor; null is a gap.
        assertNull(TrendMetric.MUSCLE_FEEL.valueOf(only))
        assertEquals(listOf<Double?>(null), listOf(only).valuesOf(TrendMetric.MUSCLE_FEEL))
    }

    @Test
    fun theLatest_isTheNewestRecordedValue_notTheLastWorkout() {
        // The newest workout may simply not carry this metric; reporting the gap as
        // "latest" would read as if the signal had been lost.
        val values = listOf<Double?>(7.0, 8.0, null)

        assertEquals(8.0, values.latestValue())
    }

    @Test
    fun withNothingRecorded_thereIsNoLatest() {
        assertNull(listOf<Double?>(null, null).latestValue())
    }

    @Test
    fun theAverage_ignoresWhatWasNotRecorded() {
        val values = listOf<Double?>(6.0, null, 8.0)

        // Over the two that were recorded, not over all three: dividing by the gaps
        // would make a sparsely-rated metric look systematically lower.
        assertEquals(7.0, values.averageValue())
        assertEquals(2, values.recordedCount())
    }

    @Test
    fun theAverage_ofNothing_isNull_ratherThanZero() {
        assertNull(listOf<Double?>(null).averageValue())
    }

    @Test
    fun aRating_isWrittenWithOneDecimal() {
        assertEquals("7.0", 7.0.asRating())
        assertEquals("7.3", 7.25.asRating())
        assertTrue("a rating must not be locale-shaped", 7.5.asRating().contains("."))
        assertFalse(7.5.asRating().contains(","))
    }
}
