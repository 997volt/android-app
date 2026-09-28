package com.example.androidapp.data.local

import com.example.androidapp.domain.model.Exercise

/**
 * Translation between the room entity and the domain model.
 *
 * The domain type stays free of persistence metadata ([ExerciseEntity.createdAt]
 * and friends): screens have no business knowing about `deletedAt`, and keeping
 * it out means a storage change cannot ripple into the UI.
 */
internal fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles,
    equipment = equipment,
    movementPattern = movementPattern,
    isCustom = isCustom,
)

/**
 * Builds a fresh row. [now] is passed in rather than read here so the caller owns
 * the clock — which is what makes this testable.
 */
internal fun Exercise.toEntity(now: Long): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles,
    equipment = equipment,
    movementPattern = movementPattern,
    isCustom = isCustom,
    createdAt = now,
    updatedAt = now,
    deletedAt = null,
)
