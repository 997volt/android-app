package com.example.androidapp.ui.statistics

import com.google.common.truth.Truth.assertThat
import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.domain.model.RangeKind
import com.example.androidapp.domain.model.WorkoutSummary
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/** What the overview counts, and what it refuses to claim (ROADMAP N35). */
class StatisticsOverviewTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 2)

    @Test
    fun itCountsTheWorkoutsInsideTheRange_andAddsTheirVolume() {
        val overview = statisticsOverview(
            range = StatisticsRange(RangeKind.LAST_7_DAYS),
            today = today,
            sessions = listOf(
                session(LocalDate.of(2026, 9, 26), volume = 12_500_000L),
                session(LocalDate.of(2026, 10, 2), volume = 8_000_000L),
                // A month old: outside a seven-day window.
                session(LocalDate.of(2026, 9, 1), volume = 99_000_000L),
            ),
            zone = zone,
        )

        assertThat(overview.workouts).isEqualTo(2)
        assertThat(overview.volumeGrams).isEqualTo(20_500_000L)
    }

    @Test
    fun theWindowEdgesAreTheWindowsEdges() {
        // The first day is in and the day after the last is out, which is the half-open rule the range
        // itself is tested for — asserted here too because this is where a wrong count would show.
        val overview = statisticsOverview(
            range = StatisticsRange(RangeKind.LAST_7_DAYS),
            today = today,
            sessions = listOf(
                session(LocalDate.of(2026, 9, 26), hour = 0, volume = 1_000L),
                session(LocalDate.of(2026, 10, 3), hour = 0, volume = 1_000_000L),
            ),
            zone = zone,
        )

        assertThat(overview.workouts).isEqualTo(1)
        assertThat(overview.volumeGrams).isEqualTo(1_000L)
    }

    @Test
    fun allTime_addsEverything() {
        val overview = statisticsOverview(
            range = StatisticsRange(RangeKind.ALL),
            today = today,
            sessions = listOf(
                session(LocalDate.of(2020, 1, 1), volume = 5_000L),
                session(LocalDate.of(2026, 10, 2), volume = 7_000L),
            ),
            zone = zone,
        )

        assertThat(overview.workouts).isEqualTo(2)
        assertThat(overview.volumeGrams).isEqualTo(12_000L)
    }

    @Test
    fun anEmptyRange_isZerosRatherThanNothing() {
        val overview = statisticsOverview(StatisticsRange(RangeKind.LAST_7_DAYS), today, emptyList(), zone)

        assertThat(overview.workouts).isEqualTo(0)
        assertThat(overview.volumeGrams).isEqualTo(0L)
    }

    @Test
    fun recordsAreNotCountedYet_andSaySoRatherThanZero() {
        // A zero here would be a claim: "you set no records". Null is the truth, and the screen can say
        // "not counted yet" instead of printing a number it does not have.
        assertThat(statisticsOverview(StatisticsRange(RangeKind.ALL), today, emptyList(), zone).personalRecords)
            .isNull()
        assertThat(StatisticsOverview().personalRecords).isNull()
    }

    @Test
    fun theRecordCount_isCarriedThroughWhenItIsKnown() {
        // The count comes from its own query over the set history (StatisticsDao.countRecordsIn), because
        // nothing stores that a set *was* a record — only what it weighed.
        val overview = statisticsOverview(
            range = StatisticsRange(RangeKind.ALL),
            today = today,
            sessions = emptyList(),
            zone = zone,
            personalRecords = 4,
        )

        assertThat(overview.personalRecords).isEqualTo(4)
    }

    private fun session(date: LocalDate, hour: Int = 12, volume: Long) = WorkoutSummary(
        id = "s-$date-$hour",
        startedAt = date.atTime(hour, 0).atZone(zone).toInstant(),
        finishedAt = date.atTime(hour, 30).atZone(zone).toInstant(),
        exerciseCount = 1,
        setCount = 3,
        volumeGrams = volume,
        zoneOffsetMinutes = 0,
        repeatableExerciseCount = 1,
    )
}
