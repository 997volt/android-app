package com.example.androidapp.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One scheduled day of a program: a slot's weekday in one particular week (ROADMAP P3.3).
 *
 * A weekday recurs, so an occurrence is never "the slot on Tuesday" — it is *that* slot
 * in *that* week, which is why both the slot and the week are part of its identity.
 */
data class SlotOccurrence(
    val slotId: String,
    val weekStart: LocalDate,
    val date: LocalDate,
)

/**
 * A session as occurrence matching reads it (ROADMAP P3.3).
 *
 * [zone] is the session's own zone (N25), because a week is taken where the workout
 * happened: a session near midnight after a flight belongs to the week it was performed
 * in, not to the device's current one.
 */
data class ProgramSession(
    val templateId: String,
    val startedAt: Instant,
    val zone: ZoneId,
) {
    val date: LocalDate get() = startedAt.atZone(zone).toLocalDate()
}

/** A recorded skip: this slot's occurrence in this week was consciously passed over. */
data class RecordedSkip(val slotId: String, val weekStart: LocalDate)

/** A missed occurrence the app asks about, carrying everything the prompt shows. */
data class PendingOccurrence(
    val slotId: String,
    val templateId: String,
    val templateName: String,
    val weekday: DayOfWeek,
    val date: LocalDate,
)

/**
 * Which scheduled occurrences happened, and which were missed (ROADMAP P3.3).
 *
 * Pure and on the JVM, because this is the part of programs with an answer worth
 * arguing about: "was that a skip or a rest day" is a joining of slots, sessions and
 * skip rows, and it should be testable without a database or a device.
 *
 * **Weeks are Monday-start and taken in the session's own zone** (P3.3, extending N25).
 * That is what makes "which day was this" answerable for a workout performed somewhere
 * the device no longer is.
 */
object ProgramSchedule {

    /** The Monday of [date]'s week: ISO weeks already start on Monday. */
    fun weekStartOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    /** The day a slot's weekday falls on in [weekStart]'s week. */
    fun occurrenceDate(weekStart: LocalDate, weekday: DayOfWeek): LocalDate =
        weekStart.plusDays((weekday.value - 1).toLong())

    /**
     * Every occurrence a session settled.
     *
     * Sessions are matched **by template and date** (P3.3), newest rule first: one
     * candidate slot resolves; with several, an exact weekday match wins, then the latest
     * slot earlier in the week (done late), then the earliest after it (done early), then
     * the earliest unresolved. A session resolves at most one occurrence and an occurrence
     * is resolved by at most one session — the first, so sessions are walked in the order
     * they happened.
     *
     * A slot with no weekday is **not** a candidate: it has no day to match against, and a
     * resolution would settle nothing anyone can observe.
     */
    fun resolvedOccurrences(
        slots: List<ProgramSlot>,
        sessions: List<ProgramSession>,
    ): Set<SlotOccurrence> {
        val dated = slots.filter { it.weekday != null }.sortedBy { it.position }
        val resolved = mutableSetOf<SlotOccurrence>()

        sessions.sortedBy { it.startedAt }.forEach { session ->
            val weekStart = weekStartOf(session.date)
            val unresolved = dated.filter { slot ->
                slot.templateId == session.templateId &&
                    SlotOccurrence(slot.id, weekStart, occurrenceDate(weekStart, slot.weekday!!)) !in resolved
            }
            if (unresolved.isEmpty()) return@forEach

            val weekday = session.date.dayOfWeek
            val exact = unresolved.filter { it.weekday == weekday }
            val earlier = unresolved.filter { it.weekday!!.value < weekday.value }
            val later = unresolved.filter { it.weekday!!.value > weekday.value }

            val chosen = when {
                exact.isNotEmpty() -> exact.first()
                earlier.isNotEmpty() ->
                    earlier.maxWith(compareBy({ it.weekday!!.value }, { it.position }))

                later.isNotEmpty() ->
                    later.minWith(compareBy({ it.weekday!!.value }, { it.position }))

                else -> unresolved.first()
            }
            resolved += SlotOccurrence(chosen.id, weekStart, occurrenceDate(weekStart, chosen.weekday!!))
        }
        return resolved
    }

    /**
     * The occurrences this week that were missed: scheduled before today, and neither
     * settled by a session that was started from their template nor consciously skipped.
     *
     * **Nothing later this week counts.** A Friday slot on a Wednesday has not been missed
     * yet, and recording a skip for it would be the app inventing a decision the user has
     * not taken. That is also why the skip prompt's *Continue* only ever silences the past.
     *
     * Earliest miss first, then program order, so the prompt names the oldest thing left
     * undone rather than the last one added.
     */
    fun pendingOccurrences(
        slots: List<ProgramSlot>,
        sessions: List<ProgramSession>,
        skips: List<RecordedSkip>,
        today: LocalDate,
    ): List<PendingOccurrence> {
        val weekStart = weekStartOf(today)
        val resolved = resolvedOccurrences(slots, sessions)
        val skipped = skips.filter { it.weekStart == weekStart }.map { it.slotId }.toSet()

        return slots
            .sortedBy { it.position }
            .mapNotNull { slot ->
                val weekday = slot.weekday ?: return@mapNotNull null
                val date = occurrenceDate(weekStart, weekday)
                if (!date.isBefore(today)) return@mapNotNull null
                if (SlotOccurrence(slot.id, weekStart, date) in resolved) return@mapNotNull null
                if (slot.id in skipped) return@mapNotNull null
                PendingOccurrence(
                    slotId = slot.id,
                    templateId = slot.templateId,
                    templateName = slot.templateName,
                    weekday = weekday,
                    date = date,
                )
            }
            // Stable, so slots sharing a day keep the program's order.
            .sortedBy { it.date }
    }
}
