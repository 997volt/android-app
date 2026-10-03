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
    val sessionId: String,
    val templateId: String?,
    val startedAt: Instant,
    val zone: ZoneId,
) {
    val date: LocalDate get() = startedAt.atZone(zone).toLocalDate()
}

/**
 * What one scheduled occurrence reads as (ROADMAP P3.13), by P3.5's same definitions.
 *
 * [DELOAD] is not one of the ratio's outcomes: a deload week is exempt from the ratio (P3.10), so
 * the row says so rather than reading as a miss the lifter never earned.
 */
enum class OccurrenceState {
    DONE,
    SKIPPED,
    MISSED,
    PENDING,
    DELOAD,
}

/**
 * One occurrence scheduled on one day, as the correction dialog reads it (ROADMAP P3.13).
 *
 * [canCorrect] is the rule, computed where `today` is known: a skip is added for **today or
 * earlier** — a day passed over is behind you, and a future day cannot be skipped (P3.3) — and a
 * finished session is not something a skip row can argue with, because the session is the record.
 */
data class DayOccurrence(
    val slotId: String,
    val templateId: String,
    val templateName: String,
    val date: LocalDate,
    val weekStart: LocalDate,
    val state: OccurrenceState,
    val canCorrect: Boolean,
)

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
    /**
     * The same counts, one row per slot (ROADMAP P3.14).
     *
     * Accumulated in the same pass as the totals rather than recomputed, so **the parts sum to the
     * whole by construction**: there is no second definition of done, skipped or missed to drift.
     * A slot with nothing scored in the window is left out — a row of zeros answers nothing, and a
     * slot with no weekday is never scheduled at all — which does not change the sum.
     */
    val bySlot: List<SlotAdherence> = emptyList(),
    /**
     * The same counts, read per lift (ROADMAP P3.14).
     *
     * Rolled up over the slots that prescribe each exercise, so unlike [bySlot] these rows do not
     * sum to the month's total: a lift trained by two slots is counted in both on purpose.
     */
    val byExercise: List<ExerciseAdherence> = emptyList(),
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
 * One slot's share of the month (ROADMAP P3.14).
 *
 * Counts rather than a percentage: two of three is not 67% of anything worth printing, and the
 * counts are what a lifter reads to see *which* day keeps being skipped.
 */
data class SlotAdherence(
    val slotId: String,
    val templateId: String,
    val templateName: String,
    val done: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0,
) {
    val scored: Int get() = done + skipped + missed
}

/**
 * One lift's share of the month, rolled up over the slots that prescribe it (ROADMAP P3.14).
 *
 * The join N14 already has: a slot's template names the exercises it trains, so a lift's counts are
 * the occurrences of every slot whose template prescribes it. That is what answers "am I skipping
 * *this lift*, or this day" — the two have different fixes.
 *
 * A lift trained by two slots therefore gets both slots' occurrences, so these rows do **not** sum
 * to the month's total the way [MonthAdherence.bySlot] does; a lift appearing twice is counted
 * twice on purpose.
 */
data class ExerciseAdherence(
    val exerciseId: String,
    val exerciseName: String,
    val done: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0,
) {
    val scored: Int get() = done + skipped + missed
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
    /**
     * The active programs, in their authored order (ROADMAP P3.12).
     *
     * The screen needs them because a deload is keyed by program and week (P3.10): with more than
     * one program followed, the toggle has to say which program's week it is marking.
     */
    val programs: List<WorkoutProgram> = emptyList(),
    /**
     * Which weeks of the window each program marked as a deload, keyed by program id (P3.10).
     *
     * Only weeks the window touches, so a toggle can be drawn as on or off without a second read.
     */
    val deloadWeeks: Map<String, Set<LocalDate>> = emptyMap(),
)
