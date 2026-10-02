package com.example.androidapp.ui.statistics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        assertEquals(RangeKind.entries.size, labels.distinct().size)
        assertTrue("every kind is labelled", labels.none { it == 0 })
    }

    private fun at(date: LocalDate, hour: Int = 12) =
        date.atTime(hour, 0).atZone(zone).toInstant()

    @Test
    fun sevenDays_includesTodayAndTheSixBeforeIt() {
        val window = StatisticsRange(RangeKind.LAST_7_DAYS).window(today, zone)!!

        assertEquals(at(LocalDate.of(2026, 9, 26), hour = 0), window.from)
        assertTrue(at(LocalDate.of(2026, 9, 26)) in window)
        assertTrue(at(today, hour = 23) in window)
        // Exclusive at the end: tomorrow belongs to tomorrow's window, not today's.
        assertFalse(at(today.plusDays(1), hour = 0) in window)
    }

    @Test
    fun theLongerRanges_goBackTheMonthsTheySay() {
        assertEquals(
            at(LocalDate.of(2026, 9, 2), hour = 0),
            StatisticsRange(RangeKind.LAST_MONTH).window(today, zone)!!.from,
        )
        assertEquals(
            at(LocalDate.of(2026, 7, 2), hour = 0),
            StatisticsRange(RangeKind.LAST_3_MONTHS).window(today, zone)!!.from,
        )
        assertEquals(
            at(LocalDate.of(2026, 4, 2), hour = 0),
            StatisticsRange(RangeKind.LAST_6_MONTHS).window(today, zone)!!.from,
        )
        assertEquals(
            at(LocalDate.of(2025, 10, 2), hour = 0),
            StatisticsRange(RangeKind.LAST_YEAR).window(today, zone)!!.from,
        )
    }

    @Test
    fun aRangeEndingToday_stillEndsTodayTomorrow() {
        // The rule the type exists for. Resolved a week later, the same saved range covers the new week
        // rather than the one the user happened to choose it in.
        val saved = StatisticsRange(RangeKind.LAST_7_DAYS)

        val now = saved.window(today, zone)!!
        val nextWeek = saved.window(today.plusDays(7), zone)!!

        assertTrue("today moves with the calendar", nextWeek.toExclusive > now.toExclusive)
        assertEquals(at(today.plusDays(8), hour = 0), nextWeek.toExclusive)
        assertFalse("and the old window's end is now inside it", at(today, hour = 23) in nextWeek)
    }

    @Test
    fun allTime_excludesNothing() {
        assertNull(StatisticsRange(RangeKind.ALL).window(today, zone))

        val readings = listOf(MetricReading(at(LocalDate.of(2020, 1, 1), hour = 12), 80_000.0))
        assertEquals(readings, StatisticsRange(RangeKind.ALL).inWindow(readings, today, zone))
    }

    @Test
    fun custom_usesItsOwnDates() {
        val range = StatisticsRange(
            kind = RangeKind.CUSTOM,
            from = LocalDate.of(2026, 8, 1),
            to = LocalDate.of(2026, 8, 31),
        )

        val window = range.window(today, zone)!!
        assertEquals(at(LocalDate.of(2026, 8, 1), hour = 0), window.from)
        assertTrue(at(LocalDate.of(2026, 8, 31), hour = 23) in window)
        assertFalse(at(LocalDate.of(2026, 9, 1), hour = 0) in window)
    }

    @Test
    fun customWithTheEndsTheWrongWayRound_isStillTheSameWindow() {
        val forwards = StatisticsRange(RangeKind.CUSTOM, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        val backwards = StatisticsRange(RangeKind.CUSTOM, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1))

        assertEquals(forwards.window(today, zone), backwards.window(today, zone))
    }

    @Test
    fun customWithNoDatesYet_isEverythingRatherThanNothing() {
        // An empty form excludes nothing. An empty chart would look like a failure to load.
        assertNull(StatisticsRange(RangeKind.CUSTOM).window(today, zone))
        assertNull(StatisticsRange(RangeKind.CUSTOM, from = today, to = null).window(today, zone))
    }

    @Test
    fun theWindowKeepsGapsInsideIt() {
        // A moment inside the window with no value is part of the window: N37 needs it to break the line.
        val readings = listOf(
            MetricReading(at(LocalDate.of(2026, 9, 1)), 80_000.0),
            MetricReading(at(LocalDate.of(2026, 10, 1)), null),
        )

        val kept = StatisticsRange(RangeKind.LAST_7_DAYS).inWindow(readings, today, zone)

        assertEquals("the September reading is out of range", 1, kept.size)
        assertNull("and the October one is a gap that stays", kept.single().value)
    }

    @Test
    fun aWindowIsHalfOpen_atTheStartOfTheDay() {
        // The same rule the measurements screen uses to decide which day an entry belongs to.
        val midnight = LocalDate.of(2026, 9, 26).atStartOfDay(zone).toInstant()
        val justBefore = Instant.ofEpochSecond(midnight.epochSecond - 1)

        val window = StatisticsRange(RangeKind.LAST_7_DAYS).window(today, zone)!!

        assertTrue(midnight in window)
        assertFalse("the instant before the first day is not in it", justBefore in window)
    }
}
