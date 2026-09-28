package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A training session (ROADMAP P1.2, F5).
 *
 * [finishedAt] null means the session is still open. Because this row is written
 * the instant a workout starts and is never held only in memory, crash recovery
 * (P1.8) needs no separate mechanism: a process death simply leaves the session
 * open, and the next launch finds it.
 *
 * The index on [finishedAt] is what keeps "find the open session" cheap even
 * once there are years of history.
 */
@Entity(
    tableName = "workout_sessions",
    indices = [Index(value = ["finishedAt"])],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
