package com.example.androidapp.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a metric's value axis starts and ends (ROADMAP N38).
 *
 * The property that matters is that `fromZero` is the metric's decision, not the data's: the same readings
 * produce two different axes, and the registry says which one applies.
 */
class ChartAxisTest {

    @Test
    fun aQuantity_axisStartsAtZero_evenWhenTheReadingsAreAllLarge() {
        // A weight axis starting at the lightest set would exaggerate every change into a cliff.
        val axis = axisBounds(values = listOf(100_000.0, 105_000.0), fromZero = true)

        assertEquals(0.0, axis.min, 0.0001)
        assertTrue("and the top still has room above it", axis.max > 105_000.0)
    }

    @Test
    fun aRating_axisFollowsTheReadings() {
        // Starting a 1–10 rating at zero would flatten the differences the ratings exist to show.
        val axis = axisBounds(values = listOf(7.0, 8.0), fromZero = false)

        assertTrue(axis.min > 0.0)
        assertEquals(6.9, axis.min, 0.0001)
        assertEquals(8.1, axis.max, 0.0001)
    }

    @Test
    fun aSeriesWhereEveryReadingIsEqual_stillHasARange() {
        assertEquals(AxisBounds(0.0, 81.0), axisBounds(listOf(80.0), fromZero = true))
        assertEquals(AxisBounds(79.0, 81.0), axisBounds(listOf(80.0), fromZero = false))
    }

    @Test
    fun nothingToDraw_isADefaultRangeRatherThanAnEmptyOne() {
        assertEquals(AxisBounds(0.0, 1.0), axisBounds(emptyList(), fromZero = true))
    }
}
