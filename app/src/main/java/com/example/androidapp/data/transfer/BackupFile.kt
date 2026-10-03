package com.example.androidapp.data.transfer

import java.time.DayOfWeek
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.platform.CrashLog
import kotlinx.serialization.Serializable

/**
 * The on-disk backup format (ROADMAP P1.12).
 *
 * Because platform backup is off, this file is the **only** escape hatch: a lost
 * signing key, a signature change or a simple uninstall otherwise means permanent
 * loss. That shapes three decisions:
 *
 *  - **JSON, not CSV.** A CSV cannot represent this schema without flattening it,
 *    and a flattened export cannot be restored faithfully. The point of the file
 *    is a round trip, so it has to carry the real shape.
 *  - **Raw storage values, not domain shapes.** Enums are written by name and
 *    timestamps as epoch millis — exactly what the database holds. Re-deriving
 *    them through the domain layer on import would risk silently changing data.
 *  - **[schemaVersion] is checked.** A file written by a *newer* app is refused
 *    rather than partially read, because guessing at unknown fields is how a
 *    restore quietly loses a column.
 *
 * Soft-deleted rows are included on purpose: they are part of the data, and
 * dropping them would make the restored database differ from the original.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: Long,
    val exercises: List<ExerciseDto>,
    val sessions: List<SessionDto>,
    val sessionExercises: List<SessionExerciseDto>,
    val sets: List<SetDto>,
    /**
     * Templates and their exercises (ROADMAP N3).
     *
     * Defaulted so a file written before templates existed still decodes — the same
     * rule every added field follows, and the reason the schema version is not
     * bumped for an addition.
     */
    val templates: List<TemplateDto> = emptyList(),
    val templateExercises: List<TemplateExerciseDto> = emptyList(),
    /** A plan's sets (ROADMAP N14). Defaulted, like every added collection. */
    val templateSets: List<TemplateSetDto> = emptyList(),
    /**
     * Programs: an ordered list of templates, each with a weekday (ROADMAP P3.3).
     *
     * Defaulted like every added collection. A program is the user's authored training
     * setup, so it rides in the file with the templates it orders.
     */
    val programs: List<ProgramDto> = emptyList(),
    val programSlots: List<ProgramSlotDto> = emptyList(),
    /**
     * The weeks an occurrence was consciously passed over (ROADMAP P3.3).
     *
     * They are history, not configuration: without them a restored schedule cannot tell a
     * skip from a miss, which is exactly what P3.5 will read them for.
     */
    val programSkips: List<ProgramSkipDto> = emptyList(),
    /**
     * What each program slot prescribes, per exercise (ROADMAP P3.8).
     *
     * Defaulted like every added collection: a file written before a slot could prescribe still
     * decodes, and its slots simply leave the template's targets standing.
     */
    val programSlotExercises: List<ProgramSlotExerciseDto> = emptyList(),
    val programSlotSets: List<ProgramSlotSetDto> = emptyList(),
    /**
     * The weeks a program was deliberately backed off (ROADMAP P3.10).
     *
     * Authored setup rather than something the app can recompute: without these rows a restored
     * block cannot tell a deload week from a missed one, and every backed-off week would read as a
     * failure that never happened.
     */
    val programDeloads: List<ProgramDeloadDto> = emptyList(),
    /**
     * The workouts that stood in for a slot's own, one week at a time (ROADMAP P3.11).
     *
     * Authored choices the app cannot recompute: without them a restored week would be read as
     * missed even though it was trained, with something else.
     */
    val programSubstitutions: List<ProgramSubstitutionDto> = emptyList(),
    /**
     * Body measurements (ROADMAP N32).
     *
     * Defaulted, like every added collection — and it must be here in the same change as the table,
     * or an export would silently carry none of them.
     */
    val measurements: List<MeasurementDto> = emptyList(),
    /**
     * The user's metric targets, keyed by `MetricKey.id` in each metric's own units (ROADMAP N39).
     *
     * They live in settings rather than the database, which is why they needed naming here: a codec
     * that lists every field by hand drops what it is not told about — three columns were lost that
     * way (N9, N15, N16) — and restoring a backup was losing every target in silence. The rest of
     * settings (rest, cue, keep-screen-on, the statistics range) stays a device preference and is
     * deliberately not in this file (N21).
     *
     * Defaulted so a file written before this field existed still decodes.
     */
    val goals: Map<String, Double> = emptyMap(),
    /**
     * Diagnostics, not user data (ROADMAP F11). They ride along with an export
     * because a release build is not debuggable and this is the only way a crash log
     * reaches the user; import deliberately ignores them.
     *
     * Defaulted so a file written before this field existed still decodes.
     */
    val crashLogs: List<CrashLog> = emptyList(),
)

@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val equipment: Equipment,
    val movementPattern: MovementPattern,
    val isCustom: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /**
     * The exercise's own rest and cue (ROADMAP N5). Defaulted, not required: a file
     * written before these existed must still decode, and the codec's schema version
     * is deliberately not bumped for an added field (see `BackupCodecTest`).
     */
    val restSeconds: Int? = null,
    val techniqueNote: String? = null,
)

/**
 * One dated set of body measurements (ROADMAP N32).
 *
 * Every field but the weight is nullable, and null means "not taken" rather than zero — a waist
 * recorded without a scale reading is a real entry, and a zero would be indistinguishable from a
 * measurement of nothing.
 */
@Serializable
data class MeasurementDto(
    val id: String,
    val measuredAt: Long,
    /** Whole grams, the only required measurement. */
    val weightGrams: Long,
    /** Tenths of a percent, the unit RPE halves exist for. */
    val bodyFatTenths: Int? = null,
    val muscleTenths: Int? = null,
    /** Tape sites in millimetres, every one optional. */
    val neckMm: Long? = null,
    val chestMm: Long? = null,
    val waistMm: Long? = null,
    val hipsMm: Long? = null,
    val upperArmMm: Long? = null,
    val thighMm: Long? = null,
    val calfMm: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class SessionDto(
    val id: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val notes: String? = null,
    val restEndsAt: Long? = null,
    /** Defaulted for the same reason as [ExerciseDto.restSeconds] (ROADMAP N4). */
    val readinessNote: String? = null,
    /**
     * The zone the session was performed in, in minutes from UTC (ROADMAP N25).
     *
     * Null for sessions recorded before the column existed; a restored one keeps that null rather
     * than acquiring an offset it never had.
     */
    val zoneOffsetMinutes: Int? = null,
    /**
     * The template this session was started from, or null (ROADMAP P3.3).
     *
     * Defaulted for the same reason as every added field: a file written before programs
     * existed still decodes, and its sessions simply have no provenance.
     */
    val templateId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class SessionExerciseDto(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    /** Defaulted for the same reason as [ExerciseDto.restSeconds] (ROADMAP N7, N8). */
    val finishedAt: Long? = null,
    val muscleFeel: Int? = null,
    val jointPain: Int? = null,
    /** Which joints, or null (ROADMAP N9). Defaulted, like every added field. */
    val jointPainNote: String? = null,
    /** The rest and cue the plan prescribed for this exercise, if any (N14). */
    val restSeconds: Int? = null,
    val techniqueNote: String? = null,
    /**
     * Which superset or circuit this exercise belonged to (ROADMAP N24), or null.
     *
     * Defaulted like every field this file has gained, so a backup written before N24 still
     * reads: an ungrouped exercise is what every exercise used to be.
     */
    val supersetGroup: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class SetDto(
    val id: String,
    val sessionExerciseId: String,
    val setIndex: Int,
    val reps: Int,
    val weightGrams: Long,
    /** The machine's assistance in grams, 0 for none (ROADMAP N15). */
    val assistanceGrams: Long = 0,
    val setType: SetType,
    /** Defaulted for the same reason as [ExerciseDto.restSeconds] (ROADMAP N6). */
    val rpeHalves: Int? = null,
    /**
     * The pre-N6.5 whole-number RPE, read only (ROADMAP N6).
     *
     * Kept because renaming the field above would otherwise drop the RPE out of every
     * backup written before RPE took halves: an unknown field decodes to nothing, so
     * the value would vanish on restore without a word.
     */
    val rpe: Int? = null,
    val note: String? = null,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class TemplateDto(
    val id: String,
    val name: String,
    /** The weekday this plan is pinned to, or null (ROADMAP N16). */
    val weekday: DayOfWeek? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class TemplateExerciseDto(
    val id: String,
    val templateId: String,
    val exerciseId: String,
    val position: Int,
    /** The rest and cue the plan prescribes, or null to use the library's (N14). */
    val restSeconds: Int? = null,
    val techniqueNote: String? = null,
    /** The superset this exercise is planned in, or null (ROADMAP N24, B16). */
    val supersetGroup: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class TemplateSetDto(
    val id: String,
    val templateExerciseId: String,
    val setIndex: Int,
    val role: SetType,
    val targetWeightGrams: Long? = null,
    val targetAssistanceGrams: Long? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpeHalves: Int? = null,
    /** The plan target's pre-half-step whole-number RPE, read only (ROADMAP N6). */
    val targetRpe: Int? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A program (ROADMAP P3.3, P3.12): a name, whether home follows it, and its place in the order. */
@Serializable
data class ProgramDto(
    val id: String,
    val name: String,
    val isActive: Boolean = false,
    /**
     * The authored order, low first (ROADMAP P3.12).
     *
     * Defaulted so a file written before programs could be ordered still decodes; the rows it
     * carries then tie on 0 and fall back to name order, which is what they were.
     */
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One slot of a program: a template, a place in the order, and a weekday (ROADMAP P3.3). */
@Serializable
data class ProgramSlotDto(
    val id: String,
    val programId: String,
    val templateId: String,
    val position: Int,
    val weekday: DayOfWeek? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A recorded skip: a slot's occurrence in one week (ROADMAP P3.3). */
@Serializable
data class ProgramSkipDto(
    val id: String,
    val slotId: String,
    /** Monday of the week, as `LocalDate.toEpochDay()`. */
    val weekStart: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A week a program was deliberately backed off (ROADMAP P3.10). */
@Serializable
data class ProgramDeloadDto(
    val id: String,
    val programId: String,
    /** Monday of the deloaded week, as `LocalDate.toEpochDay()`. */
    val weekStart: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One occurrence trained with a different workout (ROADMAP P3.11). */
@Serializable
data class ProgramSubstitutionDto(
    val id: String,
    val slotId: String,
    /** Monday of the week, as `LocalDate.toEpochDay()`. */
    val weekStart: Long,
    val templateId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One exercise of one slot, and the rest and cue that slot prescribes for it (ROADMAP P3.8). */
@Serializable
data class ProgramSlotExerciseDto(
    val id: String,
    val slotId: String,
    val exerciseId: String,
    val restSeconds: Int? = null,
    val techniqueNote: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * One set a slot prescribes (ROADMAP P3.8).
 *
 * [targetPercentOf1Rm] is the one target a template's planned set cannot carry; every other field
 * is the plan's own vocabulary, written raw exactly as the database holds it.
 */
@Serializable
data class ProgramSlotSetDto(
    val id: String,
    val slotExerciseId: String,
    val setIndex: Int,
    val role: SetType,
    val targetWeightGrams: Long? = null,
    val targetAssistanceGrams: Long? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpeHalves: Int? = null,
    val targetPercentOf1Rm: Int? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
