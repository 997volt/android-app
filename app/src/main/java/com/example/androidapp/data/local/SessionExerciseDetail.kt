package com.example.androidapp.data.local

import com.example.androidapp.domain.model.SessionExercise

/**
 * A session exercise joined with the exercise it refers to.
 *
 * Room projects the join straight into this type, so filling a workout screen is
 * one query instead of a session query plus an N+1 walk over the library. That
 * matters on the screen the user is staring at mid-set.
 */
data class SessionExerciseDetail(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val exerciseName: String,
    val primaryMuscle: com.example.androidapp.domain.model.MuscleGroup,
    val equipment: com.example.androidapp.domain.model.Equipment,
)

internal fun SessionExerciseDetail.toDomain(): SessionExercise = SessionExercise(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    position = position,
    exerciseName = exerciseName,
    primaryMuscle = primaryMuscle,
    equipment = equipment,
)
