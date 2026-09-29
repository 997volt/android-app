package com.example.androidapp.data.transfer

import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.WorkoutSessionEntity

/**
 * Entity <-> backup DTO (ROADMAP P1.12).
 *
 * Field-for-field and in both directions, so an export followed by an import is a
 * no-op on the data. Anything clever here would show up as a restore that is
 * subtly not the original.
 */

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
    restEndsAt = null,
    readinessNote = readinessNote,
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
    setType = setType,
    rpe = rpe,
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
    setType = setType,
    rpe = rpe,
    note = note,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
