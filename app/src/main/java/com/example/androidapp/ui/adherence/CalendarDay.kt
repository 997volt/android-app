package com.example.androidapp.ui.adherence

import com.example.androidapp.domain.model.MonthAdherence
import java.time.LocalDate
import java.time.YearMonth

/**
 * One cell of the month grid (ROADMAP P3.5).
 *
 * [trained] and [scheduled] are separate rather than one state because a day can be both — a
 * scheduled day that was trained — and because a rest day is not the same statement as a
 * scheduled day nobody trained. The ratio is not read off these: it counts occurrences, and two
 * slots can fall on one day.
 */
data class CalendarDay(
    val date: LocalDate,
    val trained: Boolean,
    val scheduled: Boolean,
)

/** Seven columns, Monday first — the same first day a skip row's week takes (P3.3). */
const val DAYS_IN_WEEK = 7

/**
 * The month as a Monday-first grid, padded with nulls so every row has seven cells.
 *
 * A leading blank is the days before the 1st; a trailing blank fills the last row. Pure, so the
 * padding arithmetic is asserted without rendering a calendar.
 */
fun calendarCells(month: YearMonth, adherence: MonthAdherence): List<CalendarDay?> {
    val leading = month.atDay(1).dayOfWeek.value - MONDAY_VALUE
    val cells = MutableList<CalendarDay?>(leading) { null }
    for (day in 1..month.lengthOfMonth()) {
        val date = month.atDay(day)
        cells += CalendarDay(
            date = date,
            trained = date in adherence.trainedDays,
            scheduled = date in adherence.scheduledDays,
        )
    }
    // A final short row would make the grid look like a data bug rather than a month's end.
    while (cells.size % DAYS_IN_WEEK != 0) cells += null
    return cells
}

/** `DayOfWeek.MONDAY.value` is 1, so a Monday-first grid starts by subtracting one. */
private const val MONDAY_VALUE = 1
