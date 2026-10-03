package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.AdherenceReport
import com.example.androidapp.domain.model.PendingOccurrence
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.ProgramRun
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.SlotPrescription
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
     * Marks or unmarks a week as a deload for one program (ROADMAP P3.10).
     *
     * Keyed by program and week, the shape of a skip. Idempotent either way — marking a marked week
     * changes nothing, unmarking is a soft delete — and **only a week that has started is offered**
     * by the screen, because the app has no forward view and a deload is decided by how the block is
     * going.
     */
    suspend fun setDeloadWeek(programId: String, weekStart: LocalDate, marked: Boolean): DataResult<Unit>

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
     * What one slot prescribes for each exercise of its template (ROADMAP P3.8).
     *
     * Empty prescriptions are absent rather than empty rows, so "the slot says nothing" and "the
     * slot has no prescription" are one state. The template's targets stand wherever a slot does
     * not speak (N14).
     */
    fun observeSlotPrescriptions(slotId: String): Flow<List<SlotPrescription>>

    /**
     * Writes the rest and cue a slot prescribes for one exercise (P3.8), creating the row on the
     * first write. Clearing both when the exercise has no set left removes the row, so an empty
     * prescription does not linger as an empty row.
     */
    suspend fun setSlotExercisePlan(
        slotId: String,
        exerciseId: String,
        restSeconds: Int?,
        techniqueNote: String?,
    ): DataResult<Unit>

    /** Appends a prescribed set to a slot's exercise, creating its row on the first write (P3.8). */
    suspend fun addSlotSet(slotId: String, exerciseId: String, edit: SlotSetEdit): DataResult<Unit>

    /** Overwrites one prescribed set's targets (P3.8). */
    suspend fun updateSlotSet(slotSetId: String, edit: SlotSetEdit): DataResult<Unit>

    suspend fun removeSlotSet(slotSetId: String): DataResult<Unit>

    /**
     * The previous performance of [exerciseId] for **one slot**, before [currentSessionId] (P3.8).
     *
     * A slot's own history rather than the exercise's: two slots may name one template, and the
     * heavy Monday and the light Friday have to progress apart. The session is found the way an
     * occurrence is matched (P3.3) — by template, then attributed to a slot by its own week and
     * weekday — and [zone] is the device's, used only for a session recorded before N25.
     */
    suspend fun slotPreviousPerformance(
        slotId: String,
        exerciseId: String,
        currentSessionId: String,
        zone: ZoneId,
    ): DataResult<PreviousPerformance>

    /**
     * N17's estimated one-rep max for [exerciseId], or null when there is nothing estimable
     * (ROADMAP P3.8).
     *
     * A slot's percentage prescription resolves against this, which is why it lives beside the
     * prescription: the figure is the exercise's own trend's, from its heaviest working set and
     * refused beyond twelve reps, so an exercise with no working set that short has no number
     * rather than a borrowed one.
     */
    suspend fun estimatedOneRepMax(exerciseId: String): DataResult<Long?>

    /**
     * Where one program's run is, or null when it has no slots (ROADMAP P3.9).
     *
     * Derived from finished sessions and recorded skips, never a stored cursor, so editing the
     * program re-derives its place rather than leaving a pointer at a slot that is gone. It
     * re-emits as either source changes.
     */
    fun observeProgramRun(programId: String): Flow<ProgramRun?>

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

/**
 * The editable targets of one set a slot prescribes (ROADMAP P3.8).
 *
 * A parameter object for [TemplateSetEdit]'s reason: everything but the role is optional, and a
 * positional call site would be unreadable. It carries one field a template's planned set does
 * not — [targetPercentOf1Rm].
 */
data class SlotSetEdit(
    val role: SetType = SetType.NORMAL,
    val targetWeightGrams: Long? = null,
    /** The assistance the slot prescribes, as a magnitude (ROADMAP N15). */
    val targetAssistanceGrams: Long? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpeHalves: Int? = null,
    /** A percentage of the estimated one-rep max, or null (P3.8). */
    val targetPercentOf1Rm: Int? = null,
    val note: String? = null,
)
