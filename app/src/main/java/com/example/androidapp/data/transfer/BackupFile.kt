package com.example.androidapp.data.transfer

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

@Serializable
data class SessionDto(
    val id: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val notes: String? = null,
    val restEndsAt: Long? = null,
    /** Defaulted for the same reason as [ExerciseDto.restSeconds] (ROADMAP N4). */
    val readinessNote: String? = null,
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
    val setType: SetType,
    /** Defaulted for the same reason as [ExerciseDto.restSeconds] (ROADMAP N6). */
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
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpe: Int? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
