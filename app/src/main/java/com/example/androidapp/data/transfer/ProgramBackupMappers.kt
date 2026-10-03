package com.example.androidapp.data.transfer

import com.example.androidapp.data.local.ProgramEntity
import com.example.androidapp.data.local.ProgramSkipEntity
import com.example.androidapp.data.local.ProgramSlotEntity
import com.example.androidapp.data.local.ProgramSlotExerciseEntity
import com.example.androidapp.data.local.ProgramSlotSetEntity

/**
 * Entity <-> backup DTO for programs (ROADMAP P3.3).
 *
 * Beside [BackupMappers] rather than inside it: that file is a hand-written field-per-field
 * map and had reached the file ceiling this project enforces, so the newest three tables
 * moved here rather than the gate being raised. The rule is unchanged — field for field, in
 * both directions, so an export followed by an import is a no-op.
 */

internal fun ProgramEntity.toDto() = ProgramDto(
    id = id,
    name = name,
    isActive = isActive,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ProgramDto.toEntity() = ProgramEntity(
    id = id,
    name = name,
    isActive = isActive,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ProgramSlotEntity.toDto() = ProgramSlotDto(
    id = id,
    programId = programId,
    templateId = templateId,
    position = position,
    weekday = weekday,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ProgramSlotDto.toEntity() = ProgramSlotEntity(
    id = id,
    programId = programId,
    templateId = templateId,
    position = position,
    weekday = weekday,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ProgramSkipEntity.toDto() = ProgramSkipDto(
    id = id,
    slotId = slotId,
    weekStart = weekStart,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ProgramSkipDto.toEntity() = ProgramSkipEntity(
    id = id,
    slotId = slotId,
    weekStart = weekStart,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ProgramSlotExerciseEntity.toDto() = ProgramSlotExerciseDto(
    id = id,
    slotId = slotId,
    exerciseId = exerciseId,
    restSeconds = restSeconds,
    techniqueNote = techniqueNote,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ProgramSlotExerciseDto.toEntity() = ProgramSlotExerciseEntity(
    id = id,
    slotId = slotId,
    exerciseId = exerciseId,
    restSeconds = restSeconds,
    techniqueNote = techniqueNote,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ProgramSlotSetEntity.toDto() = ProgramSlotSetDto(
    id = id,
    slotExerciseId = slotExerciseId,
    setIndex = setIndex,
    role = role,
    targetWeightGrams = targetWeightGrams,
    targetAssistanceGrams = targetAssistanceGrams,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    targetRpeHalves = targetRpeHalves,
    targetPercentOf1Rm = targetPercentOf1Rm,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ProgramSlotSetDto.toEntity() = ProgramSlotSetEntity(
    id = id,
    slotExerciseId = slotExerciseId,
    setIndex = setIndex,
    role = role,
    targetWeightGrams = targetWeightGrams,
    targetAssistanceGrams = targetAssistanceGrams,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    targetRpeHalves = targetRpeHalves,
    targetPercentOf1Rm = targetPercentOf1Rm,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
