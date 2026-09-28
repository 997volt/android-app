package com.example.androidapp.data.local

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
)
