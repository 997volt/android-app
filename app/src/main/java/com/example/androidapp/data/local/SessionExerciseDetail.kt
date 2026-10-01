package com.example.androidapp.data.local

import com.example.androidapp.domain.model.SessionExercise
import java.time.Instant

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
    /** The library exercise's own rest, or null for the app default (ROADMAP N5). */
    val restSeconds: Int?,
    /** Shown under the exercise name while lifting (ROADMAP N5). */
    val techniqueNote: String?,
    /** When this exercise was marked done, or null (ROADMAP N7). */
    val finishedAt: Long?,
    /** How well the target muscle was worked, 1–10, or null (ROADMAP N8). */
    val muscleFeel: Int?,
    /** Joint or connective-tissue discomfort, 1–10, or null (ROADMAP N8). */
    val jointPain: Int?,
    /** Which joints, or null (ROADMAP N9). */
    val jointPainNote: String?,
    val supersetGroup: Int?,
)

internal fun SessionExerciseDetail.toDomain(): SessionExercise = SessionExercise(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    position = position,
    exerciseName = exerciseName,
    primaryMuscle = primaryMuscle,
    equipment = equipment,
    restSeconds = restSeconds,
    techniqueNote = techniqueNote,
    finishedAt = finishedAt?.let(Instant::ofEpochMilli),
    muscleFeel = muscleFeel,
    jointPain = jointPain,
    jointPainNote = jointPainNote,
    supersetGroup = supersetGroup,
)
