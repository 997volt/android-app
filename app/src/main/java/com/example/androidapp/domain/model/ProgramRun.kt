package com.example.androidapp.domain.model

import java.time.LocalDate

/**
 * Where a program's run is (ROADMAP P3.9).
 *
 * The order P3.3 gave a program was real only in the editor: home asked each slot for its weekday,
 * so an order-only slot was never surfaced and "which one is next" had no answer on any screen.
 */
data class ProgramRun(
    /** The slot the run is up to. */
    val slot: ProgramSlot,
    /** True when nothing has been trained or skipped yet, so the run is at the first slot. */
    val isAtStart: Boolean,
)

/**
 * The slot a program is up to, derived from what was done rather than a stored cursor
 * (ROADMAP P3.9).
 *
 * The run advances when a slot is **trained or consciously skipped**, never when a day passes: a
 * day simply missed leaves the run where it is, which is what the missed-day question is for. It
 * is read from finished sessions and recorded skips, so editing the program re-derives its place
 * rather than leaving a cursor pointing at a slot that is gone.
 *
 * A session names only the template it was started from (P3.3), so which slot it belongs to is
 * [ProgramSchedule.sessionAssignments] where that can answer — the weekday matching P3.3 already
 * does — and otherwise the first slot at or after the run that names the template. That fallback is
 * what makes a program with **no weekdays** run A -> B -> C: nothing resolves it but the order.
 *
 * **Rotation is by what was done, never by load.** Choosing the next template from fatigue,
 * soreness or accumulated load was rejected: nothing the app records measures recovery, and a
 * rotation whose reason the lifter cannot see is a coach rather than a log (N22's rule).
 */
fun programRun(
    slots: List<ProgramSlot>,
    sessions: List<ProgramSession>,
    skips: List<RecordedSkip>,
): ProgramRun? {
    if (slots.isEmpty()) return null
    val ordered = slots.sortedBy { it.position }
    val resolvedBySession = ProgramSchedule.sessionAssignments(ordered, sessions)
        .associate { (session, occurrence) -> session.sessionId to occurrence.slotId }

    val events = buildList {
        sessions.forEach { session ->
            add(
                RunEvent(
                    at = session.date,
                    slotId = resolvedBySession[session.sessionId],
                    templateId = session.templateId,
                ),
            )
        }
        skips.forEach { skip ->
            add(RunEvent(at = skip.weekStart, slotId = skip.slotId, templateId = null))
        }
    }.sortedBy { it.at }

    var cursor = 0
    var moved = false
    events.forEach { event ->
        val index = event.slotId
            ?.let { id -> ordered.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: event.templateId?.let { template -> slotAtOrAfter(ordered, cursor, template) }
            ?: return@forEach
        cursor = (index + 1) % ordered.size
        moved = true
    }

    return ProgramRun(slot = ordered[cursor], isAtStart = !moved)
}

/** One thing that moved the run: a session's day, or a skip's week (P3.9). */
private data class RunEvent(val at: LocalDate, val slotId: String?, val templateId: String?)

/**
 * The first slot at or after [cursor] naming [templateId], wrapping to the start (P3.9).
 *
 * At or after the cursor rather than the first match overall: a cycle of order-only slots naming
 * one template has to advance through them rather than sticking on the earliest one forever.
 */
private fun slotAtOrAfter(
    ordered: List<ProgramSlot>,
    cursor: Int,
    templateId: String,
): Int? = (cursor until ordered.size).firstOrNull { ordered[it].templateId == templateId }
    ?: (0 until cursor).firstOrNull { ordered[it].templateId == templateId }
