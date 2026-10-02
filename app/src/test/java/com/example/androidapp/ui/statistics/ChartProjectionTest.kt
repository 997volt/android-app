package com.example.androidapp.ui.statistics

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.example.androidapp.ui.components.ChartPoint
import java.time.Instant
import org.junit.Test

/**
 * Where the readings sit on the x-axis (ROADMAP N37).
 *
 * The property worth holding is that *distance means time*: the same numbers spread by index would pass any
 * test about their order and fail this one.
 */
class ChartProjectionTest {

    private val day = Instant.parse("2026-09-01T08:00:00Z")
    private val tenDays = Instant.parse("2026-09-11T08:00:00Z")
    private val twentyDays = Instant.parse("2026-09-21T08:00:00Z")

    @Test
    fun pointsAreSpreadByElapsedTime_notByIndex() {
        // Ten days, then ten more: the middle point belongs halfway, not at a third.
        val points = projectByTime(
            listOf(
                MetricReading(day, 80_000.0),
                MetricReading(tenDays, 81_000.0),
                MetricReading(twentyDays, 82_000.0),
            ),
        )

        assertThat(points[0].x).isWithin(0.0001f).of(0f)
        assertThat(points[1].x).isWithin(0.0001f).of(0.5f)
        assertThat(points[2].x).isWithin(0.0001f).of(1f)
    }

    @Test
    fun anUnevenGap_isAnUnevenDistance() {
        // A month between the last two points against a day between the first two: the whole point of the
        // change is that this no longer looks like the same interval.
        val points = projectByTime(
            listOf(
                MetricReading(day, 80_000.0),
                MetricReading(Instant.parse("2026-09-02T08:00:00Z"), 80_500.0),
                MetricReading(Instant.parse("2026-10-02T08:00:00Z"), 82_000.0),
            ),
        )

        assertThat(points[0].x).isWithin(0.0001f).of(0f)
        assertWithMessage("a day out of a month is a small step").that(points[1].x).isWithin(0.005f).of(0.032f)
        assertThat(points[2].x).isWithin(0.0001f).of(1f)
    }

    @Test
    fun aMomentThatRecordedNothing_keepsItsPlace() {
        val points = projectByTime(
            listOf(
                MetricReading(day, 80_000.0),
                MetricReading(tenDays, null),
                MetricReading(twentyDays, 82_000.0),
            ),
        )

        assertWithMessage("it is still a place on the axis").that(points[1].x).isWithin(0.0001f).of(0.5f)
        assertWithMessage("and a break in the line").that(points[1].value).isNull()
    }

    @Test
    fun oneReading_sitsInTheMiddleRatherThanAtTheStart() {
        // There is no span to place it across, and the left edge would read as "the start of something".
        val points = projectByTime(listOf(MetricReading(day, 80_000.0)))

        assertThat(points.size).isEqualTo(1)
        assertThat(points.single().x).isWithin(0.0001f).of(0.5f)
    }

    @Test
    fun readingsAtTheSameInstant_doNotDivideByZero() {
        val points = projectByTime(
            listOf(
                MetricReading(day, 80_000.0),
                MetricReading(day, null),
            ),
        )

        assertThat(points.map { it.x }).isEqualTo(listOf(0.5f, 0.5f))
    }

    @Test
    fun nothingAtAll_isNoPoints() {
        assertThat(projectByTime(emptyList())).isEqualTo(emptyList<ChartPoint>())
    }
}
