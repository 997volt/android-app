package com.example.androidapp.data.local

import java.time.DayOfWeek

/**
 * A program with its slot count, for the list and for "which one is active" (ROADMAP P3.3).
 *
 * The count is carried rather than derived so the list screen can show "4 slots"
 * without loading the join table.
 */
data class ProgramSummaryRow(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val slotCount: Int,
)

/**
 * One slot with its template's name, which is what every slot-shaped screen shows.
 *
 * Joining here rather than in Kotlin keeps the editor to one subscription instead of a
 * templates read and a slots read that could disagree for a frame.
 */
data class ProgramSlotDetail(
    val id: String,
    val programId: String,
    val templateId: String,
    val position: Int,
    val weekday: DayOfWeek?,
    val templateName: String,
    val exerciseCount: Int,
)

/**
 * A session as occurrence matching reads it (ROADMAP P3.3): what it was started from,
 * when it began, and the zone it happened in.
 *
 * The zone is kept because a week is taken in the session's own zone (N25): a workout
 * near midnight after a flight belongs to the week where it was performed, which is the
 * same rule the history screen already follows.
 */
data class ProgramSessionRow(
    val sessionId: String,
    val templateId: String,
    val startedAt: Long,
    val zoneOffsetMinutes: Int?,
)

/**
 * A finished session as adherence reads it (ROADMAP P3.5).
 *
 * [templateId] is nullable here where [ProgramSessionRow]'s is not: a session started by hand
 * settles no occurrence but still marks a trained day, which is half of what the calendar draws.
 */
data class FinishedSessionRow(
    val sessionId: String,
    val templateId: String?,
    val startedAt: Long,
    val zoneOffsetMinutes: Int?,
)
