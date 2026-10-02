package com.example.androidapp.ui.statistics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Where a metric's value axis starts and ends (ROADMAP N38, N39).
 *
 * The property that matters is that `fromZero` is the metric's decision, not the data's: the same readings
 * produce two different axes, and the registry says which one applies. A target, when there is one, is part of
 * the axis too.
 */
class ChartAxisTest {

    @Test
    fun aQuantity_axisStartsAtZero_evenWhenTheReadingsAreAllLarge() {
        // A weight axis starting at the lightest set would exaggerate every change into a cliff.
        val axis = axisBounds(values = listOf(100_000.0, 105_000.0), fromZero = true)

        assertThat(axis.min).isEqualTo(0.0)
        assertThat(axis.max).isGreaterThan(105_000.0)
    }

    @Test
    fun aRatingWithoutAFixedScale_followsTheReadings() {
        // What a metric with no fixed scale does, and the reason a rating needs one: these two readings a
        // tenth apart would be drawn as a cliff filling the whole chart.
        val axis = axisBounds(values = listOf(7.0, 8.0), fromZero = false)

        assertThat(axis.min).isGreaterThan(0.0)
        assertThat(axis.min).isWithin(0.0001).of(6.9)
        assertThat(axis.max).isWithin(0.0001).of(8.1)
    }

    @Test
    fun aRatingWithAFixedScale_keepsTheScalesEnds_howeverTightTheReadingsCluster() {
        // The defect this guards: RPE 7.1, 7.2, 7.3 fitted to the data is a 0.24-wide axis, so a wobble of
        // 0.2 of a point filled the chart and read as a catastrophe. The scale is definitionally 1–10.
        val axis = axisBounds(
            values = listOf(7.1, 7.2, 7.3),
            fromZero = false,
            fixedRange = 1.0..10.0,
        )

        assertThat(axis.min).isEqualTo(1.0)
        assertThat(axis.max).isEqualTo(10.0)
    }

    @Test
    fun aFixedScale_widensForAReadingPastItsEnds() {
        // The scale is 1–10, but a reading outside it is still a reading and must not be clipped.
        val axis = axisBounds(
            values = listOf(4.0, 12.0),
            fromZero = false,
            fixedRange = 1.0..10.0,
        )

        assertThat(axis.min).isEqualTo(1.0)
        assertThat(axis.max).isGreaterThan(12.0)
    }

    @Test
    fun aFixedScale_widensForATargetPastItsEnds() {
        // A target of 12 is a claim the user made; the axis has to show it even where the scale says it
        // should not exist.
        val axis = axisBounds(values = listOf(8.0, 9.0), fromZero = false, goal = 12.0, fixedRange = 1.0..10.0)

        assertThat(axis.max).isGreaterThan(12.0)
    }

    @Test
    fun aFixedScale_stillHoldsWhenThereIsNothingToDraw() {
        // An empty rating chart should read 1–10 rather than the generic 0–1 used by a metric with no scale.
        val axis = axisBounds(emptyList(), fromZero = false, fixedRange = 1.0..10.0)

        assertThat(axis).isEqualTo(AxisBounds(1.0, 10.0))
    }

    @Test
    fun aNonFiniteTarget_isIgnoredRatherThanPoisoningTheAxis() {
        // A target of NaN reached the axis from a typed field, and `NaN.coerceIn(0f, 1f)` is still NaN, so
        // every coordinate on the canvas became NaN and the chart drew nothing while reporting no error.
        val withoutGoal = axisBounds(values = listOf(80_000.0, 81_000.0), fromZero = true)
        val withNaN = axisBounds(values = listOf(80_000.0, 81_000.0), fromZero = true, goal = Double.NaN)

        assertThat(withNaN).isEqualTo(withoutGoal)
        assertThat(withNaN.min.isFinite()).isTrue()
        assertThat(withNaN.max.isFinite()).isTrue()
    }

    @Test
    fun aNonFiniteReading_isDiscardedToo() {
        val axis = axisBounds(values = listOf(80_000.0, Double.POSITIVE_INFINITY, 81_000.0), fromZero = true)

        assertThat(axis.min).isEqualTo(0.0)
        assertThat(axis.max.isFinite()).isTrue()
        assertThat(axis.max).isGreaterThan(81_000.0)
    }

    @Test
    fun allReadingsNonFinite_fallsBackToADefaultRange() {
        val axis = axisBounds(values = listOf(Double.NaN), fromZero = false)

        assertThat(axis).isEqualTo(AxisBounds(0.0, 1.0))
    }

    @Test
    fun aNegativeTargetOnAZeroAnchoredMetric_staysOnTheAxis() {
        // N39 fixed an invisible target above the data; the same clipping happened below zero.
        val axis = axisBounds(values = listOf(100_000.0, 105_000.0), fromZero = true, goal = -1_000.0)

        assertThat(axis.min).isLessThan(-1_000.0)
        assertThat(axis.max).isAtLeast(105_000.0)
    }

    @Test
    fun aNegativeTargetOnAZeroAnchoredMetric_withNoTargetBelowZero_keepsTheZero() {
        // The ordinary case must not regress: a bars metric keeps its zero when nothing sits under it.
        val axis = axisBounds(values = listOf(100_000.0, 105_000.0), fromZero = true, goal = 90_000.0)

        assertThat(axis.min).isEqualTo(0.0)
    }

    @Test
    fun aSeriesWhereEveryReadingIsEqual_stillHasARange() {
        assertThat(axisBounds(listOf(80.0), fromZero = true)).isEqualTo(AxisBounds(0.0, 81.0))
        assertThat(axisBounds(listOf(80.0), fromZero = false)).isEqualTo(AxisBounds(79.0, 81.0))
    }

    @Test
    fun nothingToDraw_isADefaultRangeRatherThanAnEmptyOne() {
        assertThat(axisBounds(emptyList(), fromZero = true)).isEqualTo(AxisBounds(0.0, 1.0))
    }

    @Test
    fun aTargetBelowTheReadings_isInsideTheAxis() {
        // Set 80 kg against a series running 81.75 to 84.75: pinned to the edge the line is invisible, which
        // is what the emulator showed before this change.
        val axis = axisBounds(
            values = listOf(81_750.0, 84_750.0),
            fromZero = false,
            goal = 80_000.0,
        )

        assertThat(axis.min).isLessThan(80_000.0)
        assertThat(axis.max).isAtLeast(84_750.0)
    }

    @Test
    fun aTargetAboveTheReadings_isInsideTheAxisToo() {
        val axis = axisBounds(
            values = listOf(81_750.0, 84_750.0),
            fromZero = false,
            goal = 90_000.0,
        )

        assertThat(axis.max).isGreaterThan(90_000.0)
    }

    @Test
    fun aTargetDoesNotLiftAZeroAnchoredBaseline() {
        // A bars metric keeps its zero, however far above the readings its target sits: the bar length is the
        // quantity, and the quantity starts at nothing.
        val axis = axisBounds(values = listOf(1_500_000.0, 1_575_000.0), fromZero = true, goal = 2_000_000.0)

        assertThat(axis.min).isEqualTo(0.0)
        assertThat(axis.max).isGreaterThan(2_000_000.0)
    }

    @Test
    fun withReadingsAndATarget_theAxisCoversBoth() {
        val axis = axisBounds(values = listOf(80_000.0), fromZero = false, goal = 70_000.0)

        assertThat(axis.min).isLessThan(70_000.0)
        assertThat(axis.max).isGreaterThan(80_000.0)
    }

    @Test
    fun theFittedLinesEndsArePartOfTheAxis() {
        // A series rising fast ends above its own last reading, and the axis was built from the readings
        // alone — so the line was clamped flat along the top edge for its last stretch, appearing to level off
        // exactly where it rose fastest. Its ends are drawn, so they count.
        val readings = listOf(0.0, 100.0, 100.0, 100.0)
        val fittedEnd = 120.0

        val fromReadingsOnly = axisBounds(readings, fromZero = false)
        val withTheLine = axisBounds(readings + fittedEnd, fromZero = false)

        assertThat(fromReadingsOnly.max).isLessThan(fittedEnd)
        assertThat(withTheLine.max).isGreaterThan(fittedEnd)
    }

    @Test
    fun andABelowTheReadingsFittedEndIsIncludedToo() {
        // The symmetric case, which clipping hid the same way.
        val withTheLine = axisBounds(listOf(100.0, 100.0, 0.0) + (-20.0), fromZero = false)

        assertThat(withTheLine.min).isLessThan(-20.0)
    }
}
