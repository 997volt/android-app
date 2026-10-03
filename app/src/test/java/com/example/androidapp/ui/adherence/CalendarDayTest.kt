package com.example.androidapp.ui.adherence

import com.example.androidapp.domain.model.MonthAdherence
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

/**
 * The month as a Monday-first grid (ROADMAP P3.5).
 *
 * Pure, so the padding is asserted without rendering: a grid a row short or a day out of place
 * is invisible in a screenshot and wrong every time.
 */
class CalendarDayTest {

    private val october = YearMonth.of(2026, 10)

    @Test
    fun theGrid_isMondayFirst_andPaddedToWholeWeeks() {
        // The 1st of October 2026 is a Thursday, so three blanks precede it.
        val cells = calendarCells(october, MonthAdherence())

        assertThat(LocalDate.of(2026, 10, 1).dayOfWeek.value).isEqualTo(4)
        assertThat(cells.take(3)).containsExactly(null, null, null)
        assertThat(cells[3]?.date).isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(cells.size % DAYS_IN_WEEK).isEqualTo(0)
        assertThat(cells.filterNotNull()).hasSize(october.lengthOfMonth())
        assertThat(cells.filterNotNull().last().date).isEqualTo(LocalDate.of(2026, 10, 31))
    }

    @Test
    fun everyDayOfTheMonth_isPresentExactlyOnce_inOrder() {
        val cells = calendarCells(october, MonthAdherence()).filterNotNull()

        assertThat(cells.map { it.date }).isInOrder()
        assertThat(cells.map { it.date }.toSet()).hasSize(october.lengthOfMonth())
    }

    @Test
    fun aMonthStartingOnMonday_hasNoLeadingBlanks() {
        // February 2027 starts on a Monday and is 28 days, so it is exactly four rows.
        val february = YearMonth.of(2027, 2)

        val cells = calendarCells(february, MonthAdherence())

        assertThat(LocalDate.of(2027, 2, 1).dayOfWeek.value).isEqualTo(1)
        assertThat(cells.first()?.date).isEqualTo(LocalDate.of(2027, 2, 1))
        assertThat(cells).hasSize(28)
    }

    @Test
    fun trainedAndScheduled_areIndependentMarks() {
        // A day can be both, either, or neither — the grid must not collapse the two.
        val day = LocalDate.of(2026, 10, 6)
        val cells = calendarCells(
            october,
            MonthAdherence(
                trainedDays = setOf(day, LocalDate.of(2026, 10, 7)),
                scheduledDays = setOf(day),
            ),
        ).filterNotNull().associateBy { it.date }

        assertThat(cells.getValue(day).trained).isTrue()
        assertThat(cells.getValue(day).scheduled).isTrue()
        assertThat(cells.getValue(LocalDate.of(2026, 10, 7)).trained).isTrue()
        assertThat(cells.getValue(LocalDate.of(2026, 10, 7)).scheduled).isFalse()
        assertThat(cells.getValue(LocalDate.of(2026, 10, 8)).trained).isFalse()
        assertThat(cells.getValue(LocalDate.of(2026, 10, 8)).scheduled).isFalse()
    }
}
