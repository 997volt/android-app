package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.AdherenceReport
import com.example.androidapp.domain.model.DayOccurrence
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * How often the plan happened, and the corrections to it (ROADMAP P3.5, P3.13).
 *
 * Its own interface rather than more methods on [ProgramRepository], which is at the function
 * ceiling this project enforces and whose subject is the *schedule*: this one is the reading of
 * what happened against it, and every method here takes a window. `ProgramSchedule` holds the
 * arithmetic; this is only where the rows come from.
 */
interface AdherenceRepository {

    /**
     * One month of adherence: what the active programs scheduled, what happened, and the days
     * trained (ROADMAP P3.5, unioned by P3.12).
     *
     * [month] is the calendar month the grid shows; [today] and [zone] are the *device's* own day
     * and zone, which is what "elapsed" means when that month is the current one. A session's own
     * day and week still come from its own zone (N25), so a workout performed abroad marks the day
     * it happened.
     *
     * With **no active program** the days trained are still read — that needs no schedule — and
     * every count is zero, which the screen reports as "no ratio" rather than as a schedule
     * nobody followed.
     */
    suspend fun monthAdherence(
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
    ): DataResult<AdherenceReport>

    /**
     * Marks or unmarks a week as a deload for one program (ROADMAP P3.10).
     *
     * Keyed by program and week, the shape of a skip. Idempotent either way — marking a marked week
     * changes nothing, unmarking is a soft delete — and **only a week that has started is offered**
     * by the screen, because the app has no forward view and a deload is decided by how the block is
     * going.
     */
    suspend fun setDeloadWeek(programId: String, weekStart: LocalDate, marked: Boolean): DataResult<Unit>

    /**
     * What one day scheduled, per occurrence, with each one's state (ROADMAP P3.13).
     *
     * Empty when no program is active, for the ratio's reason: the pins carry no skip record, so
     * there is nothing to correct.
     */
    suspend fun occurrencesOn(
        date: LocalDate,
        today: LocalDate,
        zone: ZoneId,
    ): DataResult<List<DayOccurrence>>

    /**
     * Marks or unmarks one occurrence as skipped (ROADMAP P3.13).
     *
     * The correction is a second, explicit writer beside P3.3's prompt: the prompt records a whole
     * week in one tap, and this adds or removes one row. Unmarking is always allowed and
     * soft-deletes, returning the day to done, missed or pending by P3.5's same definitions; the
     * app never removes a skip itself.
     */
    suspend fun setOccurrenceSkipped(
        slotId: String,
        weekStart: LocalDate,
        skipped: Boolean,
    ): DataResult<Unit>
}
