package com.example.androidapp.ui.statistics

import com.example.androidapp.domain.model.BodyMeasurement
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.ExerciseTrendPoint
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.domain.model.TrendMetric
import com.example.androidapp.domain.model.TrendPoint
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading a series out of whatever the metric is stored in (ROADMAP N35).
 *
 * No database and no repositories: the twenty-one series come from three queries, and the part worth
 * testing is which field each one reads and what it does with a moment that recorded nothing.
 */
class MetricSeriesTest {

    private val first = Instant.parse("2026-09-01T07:00:00Z")
    private val second = Instant.parse("2026-09-08T07:00:00Z")

    @Test
    fun aWorkoutMetricTakesItsValueFromThePoint() {
        val series = metricSeries(
            key = MetricKey.Workout(TrendMetric.RPE),
            workouts = listOf(
                TrendPoint(startedAt = first, averageRpe = 7.5),
                TrendPoint(startedAt = second, averageRpe = 8.0),
            ),
        )

        assertEquals(listOf(7.5, 8.0), series.readings.map { it.value })
        assertEquals(listOf(first, second), series.readings.map { it.at })
    }

    @Test
    fun aMomentThatRecordedNothing_isKeptAsAGap() {
        // The assertion that matters for N37: three sessions, one of which rated nothing, must not come
        // back as two readings a week apart with the gap quietly closed.
        val series = metricSeries(
            key = MetricKey.Workout(TrendMetric.RPE),
            workouts = listOf(
                TrendPoint(startedAt = first, averageRpe = 7.5),
                TrendPoint(startedAt = second),
            ),
        )

        assertEquals("both moments, not just the one with a value", 2, series.readings.size)
        assertNull("the second recorded nothing", series.readings[1].value)
        assertEquals("but only one of them is a reading", 1, series.recorded)
    }

    @Test
    fun theExerciseMetricsReadTheirOwnFields() {
        val point = ExerciseTrendPoint(
            startedAt = first,
            heaviestSetGrams = 102_500L,
            volumeGrams = 4_100_000L,
            totalReps = 40,
            leastAssistanceGrams = 20_000L,
        )

        assertEquals(
            102_500.0,
            metricSeries(MetricKey.Exercise(ExerciseTrendMetric.HEAVIEST_SET), exercises = listOf(point))
                .readings.single().value,
        )
        assertEquals(
            40.0,
            metricSeries(MetricKey.Exercise(ExerciseTrendMetric.TOTAL_REPS), exercises = listOf(point))
                .readings.single().value,
        )
        assertEquals(
            20_000.0,
            metricSeries(MetricKey.Exercise(ExerciseTrendMetric.ASSISTANCE), exercises = listOf(point))
                .readings.single().value,
        )
    }

    @Test
    fun theBodyMetricsReadTheirOwnFields() {
        val entry = measurement(weight = 82_400L, bodyFat = 183, muscle = 421)

        assertEquals(
            82_400.0,
            metricSeries(MetricKey.Body(BodyMetric.WEIGHT), measurements = listOf(entry))
                .readings.single().value,
        )
        assertEquals(
            183.0,
            metricSeries(MetricKey.Body(BodyMetric.BODY_FAT), measurements = listOf(entry))
                .readings.single().value,
        )
        assertEquals(
            421.0,
            metricSeries(MetricKey.Body(BodyMetric.MUSCLE), measurements = listOf(entry))
                .readings.single().value,
        )
    }

    @Test
    fun aTapeSiteThatWasNotMeasured_isAGapRatherThanAnAbsence() {
        // "I weighed myself but did not take a waist" is a real entry, and it has to read as a gap at that
        // moment rather than as an entry that does not exist (ROADMAP N32).
        val series = metricSeries(
            key = MetricKey.Tape(TapeSite.WAIST),
            measurements = listOf(measurement(weight = 82_400L, waist = null)),
        )

        assertEquals(1, series.readings.size)
        assertNull(series.readings.single().value)
    }

    @Test
    fun aTapeSiteReadsItsOwnValue_whenItWasMeasured() {
        val series = metricSeries(
            key = MetricKey.Tape(TapeSite.WAIST),
            measurements = listOf(measurement(weight = 82_400L, waist = 864L)),
        )

        assertEquals(864.0, series.readings.single().value)
    }

    @Test
    fun theSeriesIsOldestFirst_whateverOrderItArrivedIn() {
        // Measurements arrive newest-first and the training series oldest-first. One of those would have
        // been wrong to trust, so the series sorts rather than assuming.
        val series = metricSeries(
            key = MetricKey.Body(BodyMetric.WEIGHT),
            measurements = listOf(
                measurement(at = second, weight = 82_000L),
                measurement(at = first, weight = 83_000L),
            ),
        )

        assertEquals(listOf(first, second), series.readings.map { it.at })
        assertEquals(listOf(83_000.0, 82_000.0), series.readings.map { it.value })
    }

    @Test
    fun aMetricWithNoSource_hasNoReadings() {
        val series = metricSeries(MetricKey.Workout(TrendMetric.RPE))

        assertEquals(emptyList<MetricReading>(), series.readings)
        assertEquals(0, series.recorded)
    }

    private fun measurement(
        at: Instant = first,
        weight: Long,
        bodyFat: Int? = null,
        muscle: Int? = null,
        waist: Long? = null,
    ) = BodyMeasurement(
        id = "m",
        measuredAt = at,
        weightGrams = weight,
        bodyFatTenths = bodyFat,
        muscleTenths = muscle,
        tape = listOfNotNull(waist?.let { TapeSite.WAIST to it }).toMap(),
    )
}
