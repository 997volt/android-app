package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.AdherenceReport
import com.example.androidapp.domain.model.PendingOccurrence
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.WorkoutProgram
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Programs, their slots and their missed days (ROADMAP P3.3).
 *
 * Writes return [DataResult] for the same reason every other repository's do (F7): a
 * slot that silently failed to save is a schedule the user thinks they have.
 */
interface ProgramRepository {

    /** Every live program, in the authored order, with its slot count. */
    fun observePrograms(): Flow<List<WorkoutProgram>>

    /** One program, re-emitting when it is renamed, reordered or activated. */
    fun observeProgram(programId: String): Flow<WorkoutProgram?>

    /**
     * Every program home follows, in the authored order (ROADMAP P3.12).
     *
     * A list because more than one may be active. Empty is the state home falls back to the
     * template pins from, and the state in which adherence has no schedule to score.
     */
    fun observeActivePrograms(): Flow<List<WorkoutProgram>>

    /** A program's slots in its own order, each carrying its template's name. */
    fun observeSlots(programId: String): Flow<List<ProgramSlot>>

    /**
     * Stores a new program named [name] and returns its id.
     *
     * Creation asks for the name only, exactly as a template does (N3): the slots are
     * added afterwards. A new program is **not** made active — following one is a
     * deliberate choice, and the editor offers it as one.
     */
    suspend fun createProgram(name: String): DataResult<String>

    suspend fun renameProgram(programId: String, name: String): DataResult<Unit>

    /** Soft-deletes the program; its rows stay for an export to carry. */
    suspend fun deleteProgram(programId: String): DataResult<Unit>

    /**
     * Starts following this program, **without** stopping any other (ROADMAP P3.12).
     *
     * P3.3's "one active program only" is deliberately amended: a lifting block and a
     * conditioning one are two schedules at once, and the union is what every reader takes.
     */
    suspend fun activateProgram(programId: String): DataResult<Unit>

    /** Stops following this program, leaving the others active (P3.12). */
    suspend fun deactivateProgram(programId: String): DataResult<Unit>

    /** Moves a program one place: [delta] -1 for up, +1 for down. Past either end is a no-op. */
    suspend fun moveProgram(programId: String, delta: Int): DataResult<Unit>

    /**
     * Appends a slot for [templateId], on [weekday] or order-only when it is null.
     *
     * The template must be a live one: a slot pointing at nothing would be invisible on
     * every screen the moment it was written.
     */
    suspend fun addSlot(
        programId: String,
        templateId: String,
        weekday: DayOfWeek? = null,
    ): DataResult<Unit>

    /** Pins a slot to a weekday, or makes it order-only with null (P3.3). */
    suspend fun setSlotWeekday(slotId: String, weekday: DayOfWeek?): DataResult<Unit>

    /** Moves a slot one place: [delta] -1 for up, +1 for down. Past either end is a no-op. */
    suspend fun moveSlot(slotId: String, delta: Int): DataResult<Unit>

    suspend fun removeSlot(slotId: String): DataResult<Unit>

    /**
     * The occurrences of every active program's current week that were missed (P3.3, unioned
     * by P3.12).
     *
     * [today] and [zone] are the *device's* day and zone, which is what "this week" means
     * when the question is asked now; each session's own week still comes from its own
     * zone (N25). Empty when no program is active — there is then nothing to be late for.
     * With several active, the misses are merged and ordered earliest first.
     */
    suspend fun pendingOccurrences(today: LocalDate, zone: ZoneId): DataResult<List<PendingOccurrence>>

    /**
     * Records a skip for each of [slotIds] in [weekStart]'s week (P3.3).
     *
     * One call for the whole week, because *Continue* settles every pending occurrence at
     * once: asking again for the next one would turn two misses into two interrogations.
     * Idempotent, so a repeated continue cannot double-record.
     */
    suspend fun skipOccurrences(slotIds: List<String>, weekStart: LocalDate): DataResult<Unit>

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
}
