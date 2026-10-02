package com.example.androidapp.data.local

import com.example.androidapp.domain.model.WorkoutSummary
import java.time.Instant

/**
 * One row of the history list: a finished workout and its totals.
 *
 * A projection rather than an entity, because nothing here is a column — the
 * counts and the volume are computed by the query (see `WorkoutDao.observeHistory`).
 */
data class WorkoutSummaryRow(
    val id: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeGrams: Long,
    /** The zone the session was performed in, or null when it predates the column (ROADMAP N25). */
    val zoneOffsetMinutes: Int?,
    /** Exercises still in the library, which is what a repeat would copy (ROADMAP B43's tail). */
    val repeatableExerciseCount: Int,
)

internal fun WorkoutSummaryRow.toDomain(): WorkoutSummary = WorkoutSummary(
    id = id,
    startedAt = Instant.ofEpochMilli(startedAt),
    finishedAt = finishedAt?.let(Instant::ofEpochMilli),
    exerciseCount = exerciseCount,
    setCount = setCount,
    volumeGrams = volumeGrams,
    zoneOffsetMinutes = zoneOffsetMinutes,
    repeatableExerciseCount = repeatableExerciseCount,
)
