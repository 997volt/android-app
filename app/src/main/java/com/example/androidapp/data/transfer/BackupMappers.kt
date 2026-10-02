package com.example.androidapp.data.transfer

import com.example.androidapp.data.local.MeasurementEntity
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.TemplateEntity
import com.example.androidapp.data.local.TemplateExerciseEntity
import com.example.androidapp.domain.model.Rpe
import com.example.androidapp.data.local.TemplateSetEntity
import com.example.androidapp.data.local.WorkoutSessionEntity

/**
 * Entity <-> backup DTO (ROADMAP P1.12).
 *
 * Field-for-field and in both directions, so an export followed by an import is a
 * no-op on the data. Anything clever here would show up as a restore that is
 * subtly not the original.
 */

internal fun MeasurementEntity.toDto() = MeasurementDto(
    id = id,
    measuredAt = measuredAt,
    weightGrams = weightGrams,
    bodyFatTenths = bodyFatTenths,
    muscleTenths = muscleTenths,
    neckMm = neckMm,
    chestMm = chestMm,
    waistMm = waistMm,
    hipsMm = hipsMm,
    upperArmMm = upperArmMm,
    thighMm = thighMm,
    calfMm = calfMm,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun MeasurementDto.toEntity() = MeasurementEntity(
    id = id,
    measuredAt = measuredAt,
    weightGrams = weightGrams,
    bodyFatTenths = bodyFatTenths,
    muscleTenths = muscleTenths,
    neckMm = neckMm,
    chestMm = chestMm,
    waistMm = waistMm,
    hipsMm = hipsMm,
    upperArmMm = upperArmMm,
    thighMm = thighMm,
    calfMm = calfMm,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ExerciseEntity.toDto() = ExerciseDto(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles,
    equipment = equipment,
    movementPattern = movementPattern,
    isCustom = isCustom,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    restSeconds = restSeconds,
    techniqueNote = techniqueNote,
)

internal fun ExerciseDto.toEntity() = ExerciseEntity(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles,
    equipment = equipment,
    movementPattern = movementPattern,
    isCustom = isCustom,
    restSeconds = restSeconds,
    techniqueNote = techniqueNote,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun WorkoutSessionEntity.toDto() = SessionDto(
    id = id,
    startedAt = startedAt,
    finishedAt = finishedAt,
    notes = notes,
    restEndsAt = restEndsAt,
    readinessNote = readinessNote,
    // Carried, unlike the rest countdown: where a session happened is a fact about the training,
    // not about the device it was written on (ROADMAP N25).
    zoneOffsetMinutes = zoneOffsetMinutes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/**
 * A restored session must not still be "in progress": a session left open in the
 * file would hijack the app on next launch and look like a workout the user never
 * started.
 */
internal fun SessionDto.toEntity() = WorkoutSessionEntity(
    id = id,
    startedAt = startedAt,
    finishedAt = finishedAt,
    notes = notes,
    // Deliberate, not an oversight: a rest countdown is device-and-moment state rather than
    // training history, so it is carried in the file but never restored — see the note above.
    restEndsAt = null,
    readinessNote = readinessNote,
    zoneOffsetMinutes = zoneOffsetMinutes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun SessionExerciseEntity.toDto() = SessionExerciseDto(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    position = position,
    finishedAt = finishedAt,
    muscleFeel = muscleFeel,
    jointPain = jointPain,
    jointPainNote = jointPainNote,
    restSeconds = restSeconds,
    techniqueNote = techniqueNote,
    supersetGroup = supersetGroup,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun SessionExerciseDto.toEntity() = SessionExerciseEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    position = position,
    finishedAt = finishedAt,
    muscleFeel = muscleFeel,
    jointPain = jointPain,
    jointPainNote = jointPainNote,
    restSeconds = restSeconds,
    techniqueNote = techniqueNote,
    supersetGroup = supersetGroup,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun SetEntryEntity.toDto() = SetDto(
    id = id,
    sessionExerciseId = sessionExerciseId,
    setIndex = setIndex,
    reps = reps,
    weightGrams = weightGrams,
    assistanceGrams = assistanceGrams,
    setType = setType,
    rpeHalves = rpeHalves,
    note = note,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun SetDto.toEntity() = SetEntryEntity(
    id = id,
    sessionExerciseId = sessionExerciseId,
    setIndex = setIndex,
    reps = reps,
    weightGrams = weightGrams,
    assistanceGrams = assistanceGrams,
    setType = setType,
    // `rpe` is the pre-half-step field: a file written then carries 8 where this app
    // now means 8.0, which is 16 halves.
    rpeHalves = rpeHalves ?: rpe?.times(Rpe.HALVES_PER_POINT),
    note = note,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun TemplateEntity.toDto() = TemplateDto(
    id = id,
    name = name,
    weekday = weekday,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun TemplateDto.toEntity() = TemplateEntity(
    id = id,
    name = name,
    weekday = weekday,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun TemplateExerciseEntity.toDto() = TemplateExerciseDto(
    id = id,
    templateId = templateId,
    exerciseId = exerciseId,
    position = position,
    restSeconds = restSeconds,
    techniqueNote = techniqueNote,
    supersetGroup = supersetGroup,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun TemplateExerciseDto.toEntity() = TemplateExerciseEntity(
    id = id,
    templateId = templateId,
    exerciseId = exerciseId,
    position = position,
    restSeconds = restSeconds,
    techniqueNote = techniqueNote,
    supersetGroup = supersetGroup,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun TemplateSetEntity.toDto() = TemplateSetDto(
    id = id,
    templateExerciseId = templateExerciseId,
    setIndex = setIndex,
    role = role,
    targetWeightGrams = targetWeightGrams,
    targetAssistanceGrams = targetAssistanceGrams,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    targetRpeHalves = targetRpeHalves,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun TemplateSetDto.toEntity() = TemplateSetEntity(
    id = id,
    templateExerciseId = templateExerciseId,
    setIndex = setIndex,
    role = role,
    targetWeightGrams = targetWeightGrams,
    targetAssistanceGrams = targetAssistanceGrams,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    // A pre-half-step plan target carries whole numbers; 7 is 7.0, 14 halves.
    targetRpeHalves = targetRpeHalves ?: targetRpe?.times(Rpe.HALVES_PER_POINT),
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/** Half steps per RPE point (ROADMAP N6). */
