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
    indices = [
        Index(value = ["finishedAt"]),
        // The statistics range filter scans by start time, and the `finishedAt` index cannot serve it: one
        // asks `IS NOT NULL`, this one asks a range on another column. Without it the record count reads
        // every set ever logged (ROADMAP B46).
        Index(value = ["startedAt"]),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val notes: String?,
    /**
     * When the current rest ends, or null when not resting (ROADMAP P1.4).
     *
     * Stored as an absolute instant rather than a countdown so the timer stays
     * correct across a process death — and so no work is needed per tick.
     */
    val restEndsAt: Long?,
    /**
     * What was not recovered today — "shoulders still sore from Monday" (ROADMAP N4).
     *
     * Free text on purpose. Kept separate from [notes], which already exists for a
     * future per-workout note: a readiness note answers a narrower question and
     * must not collide with it.
     */
    val readinessNote: String? = null,
    /**
     * The zone the session was performed in, in minutes from UTC (ROADMAP N25).
     *
     * Captured once when the session opens and never updated: a session that spans a DST change keeps
     * its start offset, which is a simplification worth stating rather than discovering. Null on rows
     * written before this column existed, which is the honest answer — see the migration.
     */
    val zoneOffsetMinutes: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    /**
     * The template this session was started from, or null (ROADMAP P3.3).
     *
     * Written only when the session is **created** from a template, so a resumed session
     * never rewrites it: this is *provenance*, not prescription. It amends N16
     * deliberately — N16 rejected copying a plan's **targets** onto a session because that
     * freezes what the plan prescribes, while recording where a session came from freezes
     * nothing. The template stays living; this only answers which occurrence a session
     * settled, which a standing weekday pin carries no history for.
     */
    val templateId: String? = null,
)
