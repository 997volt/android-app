package com.example.androidapp.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A finished session as adherence reads it (ROADMAP P3.5).
 *
 * [templateId] is null for a session started by hand. Such a session marks a trained day —
 * "a month of days trained" needs no schedule — but it resolves no occurrence, which is
 * P3.3's inherited limit rather than a new one.
 *
 * [zone] is the session's own (N25) because both the day it marks and the week it settles are
 * taken where the workout happened, so one performed after a flight marks the day it happened
 * even when the device's clock has moved on.
 */
data class AdherenceSession(
    val templateId: String?,
    val startedAt: Instant,
    val zone: ZoneId,
) {
    val date: LocalDate get() = startedAt.atZone(zone).toLocalDate()
}

/**
 * One month's adherence (ROADMAP P3.5).
 *
 * **The unit is the occurrence, not the day.** Two slots may fall on one Tuesday and P3.3
 * settles and skips them one at a time, so [done] + [skipped] + [missed] counts two while the
 * calendar marks the one day.
 *
 * [trainedDays] and [scheduledDays] are what the grid draws: a trained day is a finished
 * session's own day, and a scheduled day is a weekday slot of the active program. They are
 * sets rather than the ratio's inputs because the grid is a second question — "which days" —
 * asked over the same month.
 */
data class MonthAdherence(
    val done: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0,
    /** A finished session's own day, in the zone it was performed in (N25, B45). */
    val trainedDays: Set<LocalDate> = emptySet(),
    /** A day a weekday slot of the active program falls on; a rest day is not one of these. */
    val scheduledDays: Set<LocalDate> = emptySet(),
) {
    /** Every scored occurrence: what the ratio is taken over. */
    val scored: Int get() = done + skipped + missed

    /**
     * Done over done + skipped + missed, or null when nothing elapsed was scheduled.
     *
     * Null rather than a number because 0% and 100% are both claims about a schedule that was
     * not there: a month with no elapsed scheduled day says so instead (ROADMAP P3.5).
     */
    val ratio: Double? get() = if (scored == 0) null else done.toDouble() / scored
}

/**
 * A month's adherence, and whether a program was there to be adhered to (ROADMAP P3.5).
 *
 * [hasActiveProgram] is not part of the schedule arithmetic: it is why the ratio is absent when
 * it is, and the screen words that absence differently from a month the program simply did not
 * schedule. The pins home falls back to carry no skip record, so scoring them would turn every
 * deliberate rest on a pinned day into a failure.
 */
data class AdherenceReport(
    val hasActiveProgram: Boolean,
    val adherence: MonthAdherence,
)
