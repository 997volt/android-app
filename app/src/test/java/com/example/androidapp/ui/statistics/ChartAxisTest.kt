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
    fun aRating_axisFollowsTheReadings() {
        // Starting a 1–10 rating at zero would flatten the differences the ratings exist to show.
        val axis = axisBounds(values = listOf(7.0, 8.0), fromZero = false)

        assertThat(axis.min).isGreaterThan(0.0)
        assertThat(axis.min).isWithin(0.0001).of(6.9)
        assertThat(axis.max).isWithin(0.0001).of(8.1)
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
}
