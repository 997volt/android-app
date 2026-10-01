package com.example.androidapp.data.local

import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.WorkoutSession
import java.time.Instant

/**
 * Entity to domain for sessions.
 *
 * The epoch-millis `Long` in storage becomes a `java.time.Instant` in the
 * domain — the conversion lives here so no ViewModel has to think about it.
 */
internal fun WorkoutSessionEntity.toDomain(): WorkoutSession = WorkoutSession(
    id = id,
    startedAt = Instant.ofEpochMilli(startedAt),
    finishedAt = finishedAt?.let(Instant::ofEpochMilli),
    restEndsAt = restEndsAt?.let(Instant::ofEpochMilli),
    readinessNote = readinessNote,
        zoneOffsetMinutes = zoneOffsetMinutes,
    notes = notes,
)

internal fun SetEntryEntity.toDomain(): SetEntry = SetEntry(
    id = id,
    sessionExerciseId = sessionExerciseId,
    setIndex = setIndex,
    reps = reps,
    weightGrams = weightGrams,
    assistanceGrams = assistanceGrams,
    setType = setType,
    rpeHalves = rpeHalves,
    note = note,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
)
