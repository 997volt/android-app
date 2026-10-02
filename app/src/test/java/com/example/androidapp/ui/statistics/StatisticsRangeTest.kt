package com.example.androidapp.ui.statistics

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.example.androidapp.domain.model.window
import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.domain.model.RangeKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/**
 * What each range covers, and the rule that made this a type (ROADMAP N35).
 *
 * UTC throughout, so the assertions are about the range logic rather than about the machine's clock.
 */
class StatisticsRangeTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 2)

    @Test
    fun everyKindHasALabel_ofItsOwn() {
        // A picker entry with no label is an invisible option, and two kinds sharing one is unreadable.
        val labels = RangeKind.entries.map { it.labelRes }

        assertThat(labels.distinct().size).isEqualTo(RangeKind.entries.size)
        assertWithMessage("every kind is labelled").that(labels.none { it == 0 }).isTrue()
    }

    private fun at(date: LocalDate, hour: Int = 12) =
        date.atTime(hour, 0).atZone(zone).toInstant()

    @Test
    fun sevenDays_includesTodayAndTheSixBeforeIt() {
        val window = StatisticsRange(RangeKind.LAST_7_DAYS).window(today, zone)!!

        assertThat(window.from).isEqualTo(at(LocalDate.of(2026, 9, 26), hour = 0))
        assertThat(at(LocalDate.of(2026, 9, 26)) in window).isTrue()
        assertThat(at(today, hour = 23) in window).isTrue()
        // Exclusive at the end: tomorrow belongs to tomorrow's window, not today's.
        assertThat(at(today.plusDays(1), hour = 0) in window).isFalse()
    }

    @Test
    fun theLongerRanges_goBackTheMonthsTheySay() {
        assertThat(StatisticsRange(RangeKind.LAST_MONTH).window(today, zone)!!.from)
            .isEqualTo(at(LocalDate.of(2026, 9, 2), hour = 0))
        assertThat(StatisticsRange(RangeKind.LAST_3_MONTHS).window(today, zone)!!.from)
            .isEqualTo(at(LocalDate.of(2026, 7, 2), hour = 0))
        assertThat(StatisticsRange(RangeKind.LAST_6_MONTHS).window(today, zone)!!.from)
            .isEqualTo(at(LocalDate.of(2026, 4, 2), hour = 0))
        assertThat(StatisticsRange(RangeKind.LAST_YEAR).window(today, zone)!!.from)
            .isEqualTo(at(LocalDate.of(2025, 10, 2), hour = 0))
    }

    @Test
    fun aRangeEndingToday_stillEndsTodayTomorrow() {
        // The rule the type exists for. Resolved a week later, the same saved range covers the new week
        // rather than the one the user happened to choose it in.
        val saved = StatisticsRange(RangeKind.LAST_7_DAYS)

        val now = saved.window(today, zone)!!
        val nextWeek = saved.window(today.plusDays(7), zone)!!

        assertWithMessage("today moves with the calendar").that(nextWeek.toExclusive > now.toExclusive).isTrue()
        assertThat(nextWeek.toExclusive).isEqualTo(at(today.plusDays(8), hour = 0))
        assertWithMessage("and the old window's end is now inside it").that(at(today, hour = 23) in nextWeek).isFalse()
    }

    @Test
    fun allTime_excludesNothing() {
        assertThat(StatisticsRange(RangeKind.ALL).window(today, zone)).isNull()

        val readings = listOf(MetricReading(at(LocalDate.of(2020, 1, 1), hour = 12), 80_000.0))
        assertThat(StatisticsRange(RangeKind.ALL).inWindow(readings, today, zone)).isEqualTo(readings)
    }

    @Test
    fun custom_usesItsOwnDates() {
        val range = StatisticsRange(
            kind = RangeKind.CUSTOM,
            from = LocalDate.of(2026, 8, 1),
            to = LocalDate.of(2026, 8, 31),
        )

        val window = range.window(today, zone)!!
        assertThat(window.from).isEqualTo(at(LocalDate.of(2026, 8, 1), hour = 0))
        assertThat(at(LocalDate.of(2026, 8, 31), hour = 23) in window).isTrue()
        assertThat(at(LocalDate.of(2026, 9, 1), hour = 0) in window).isFalse()
    }

    @Test
    fun customWithTheEndsTheWrongWayRound_isStillTheSameWindow() {
        val forwards = StatisticsRange(RangeKind.CUSTOM, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        val backwards = StatisticsRange(RangeKind.CUSTOM, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1))

        assertThat(backwards.window(today, zone)).isEqualTo(forwards.window(today, zone))
    }

    @Test
    fun customWithNoDatesYet_isEverythingRatherThanNothing() {
        // An empty form excludes nothing. An empty chart would look like a failure to load.
        assertThat(StatisticsRange(RangeKind.CUSTOM).window(today, zone)).isNull()
        assertThat(StatisticsRange(RangeKind.CUSTOM, from = today, to = null).window(today, zone)).isNull()
    }

    @Test
    fun theWindowKeepsGapsInsideIt() {
        // A moment inside the window with no value is part of the window: N37 needs it to break the line.
        val readings = listOf(
            MetricReading(at(LocalDate.of(2026, 9, 1)), 80_000.0),
            MetricReading(at(LocalDate.of(2026, 10, 1)), null),
        )

        val kept = StatisticsRange(RangeKind.LAST_7_DAYS).inWindow(readings, today, zone)

        assertWithMessage("the September reading is out of range").that(kept.size).isEqualTo(1)
        assertWithMessage("and the October one is a gap that stays").that(kept.single().value).isNull()
    }

    @Test
    fun aWindowIsHalfOpen_atTheStartOfTheDay() {
        // The same rule the measurements screen uses to decide which day an entry belongs to.
        val midnight = LocalDate.of(2026, 9, 26).atStartOfDay(zone).toInstant()
        val justBefore = Instant.ofEpochSecond(midnight.epochSecond - 1)

        val window = StatisticsRange(RangeKind.LAST_7_DAYS).window(today, zone)!!

        assertThat(midnight in window).isTrue()
        assertWithMessage("the instant before the first day is not in it").that(justBefore in window).isFalse()
    }
}
