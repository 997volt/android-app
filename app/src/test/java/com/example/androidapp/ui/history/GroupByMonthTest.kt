package com.example.androidapp.ui.history

import com.example.androidapp.domain.model.WorkoutSummary
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for history grouping (ROADMAP P1.6).
 *
 * Pure, so the ordering rules are asserted rather than eyeballed — and the zone is
 * passed in, which is what makes the "which month is this?" boundary testable at
 * all.
 */
class GroupByMonthTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun groupsByMonth_newestMonthFirst() {
        val groups = groupByMonth(
            listOf(
                workout("july", "2026-07-04T10:00:00Z"),
                workout("sept", "2026-09-28T07:00:00Z"),
                workout("august", "2026-08-15T10:00:00Z"),
            ),
            zone,
        )

        assertEquals(
            listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8), YearMonth.of(2026, 7)),
            groups.map { it.month },
        )
    }

    @Test
    fun withinAMonth_newestWorkoutFirst() {
        val groups = groupByMonth(
            listOf(
                workout("early", "2026-09-02T10:00:00Z"),
                workout("late", "2026-09-28T07:00:00Z"),
                workout("middle", "2026-09-15T10:00:00Z"),
            ),
            zone,
        )

        assertEquals(listOf("late", "middle", "early"), groups.single().workouts.map { it.id })
    }

    @Test
    fun aWorkoutJustBeforeMidnight_groupsByWhereItStarted() {
        // The boundary that makes grouping on startedAt (not finishedAt) matter: a
        // 23:50 start that runs past midnight belongs to the day it began.
        val start = ZonedDateTime.of(2026, 9, 30, 23, 50, 0, 0, zone).toInstant()
        val finish = start.plusSeconds(40 * 60)

        val groups = groupByMonth(listOf(workoutAt("late", start, finish)), zone)

        assertEquals(YearMonth.of(2026, 9), groups.single().month)
    }

    @Test
    fun theZoneDecidesWhichMonthAWorkoutFallsIn() {
        // 22:00 UTC on the last of August is already September in Tokyo, which is
        // exactly why the zone is a parameter and not an assumption.
        val instant = ZonedDateTime.of(2026, 8, 31, 22, 0, 0, 0, ZoneId.of("UTC")).toInstant()

        assertEquals(
            YearMonth.of(2026, 8),
            groupByMonth(listOf(workoutAt("a", instant)), ZoneId.of("UTC")).single().month,
        )
        assertEquals(
            YearMonth.of(2026, 9),
            groupByMonth(listOf(workoutAt("a", instant)), ZoneId.of("Asia/Tokyo")).single().month,
        )
    }

    @Test
    fun noWorkouts_producesNoGroups() {
        assertTrue(groupByMonth(emptyList(), zone).isEmpty())
    }

    private fun workout(
        id: String,
        startedAt: String,
        finishedAt: String = startedAt,
    ) = workoutAt(id, Instant.parse(startedAt), Instant.parse(finishedAt))

    /** Distinct name, not an overload: two `workout` overloads differing only in
     *  parameter type made the two-argument call ambiguous. */
    private fun workoutAt(id: String, startedAt: Instant, finishedAt: Instant = startedAt) =
        WorkoutSummary(
            id = id,
            startedAt = startedAt,
            finishedAt = finishedAt,
            exerciseCount = 1,
            setCount = 1,
            volumeGrams = 1_000L,
        )
}
