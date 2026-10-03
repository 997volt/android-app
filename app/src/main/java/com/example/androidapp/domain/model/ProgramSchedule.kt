package com.example.androidapp.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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
    /**
     * The session's own id, carried so a matched session can be read back (ROADMAP P3.8).
     *
     * Matching itself never looks at it; "this slot's own history" does, because the sets of the
     * session a slot settled are what that slot progresses from.
     */
    val sessionId: String,
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
    ): Set<SlotOccurrence> = sessionAssignments(slots, sessions).mapTo(mutableSetOf()) { it.second }

    /**
     * Each session paired with the occurrence it settled, oldest session first (P3.3, P3.8).
     *
     * The same matching [resolvedOccurrences] is built on, read where the *session* matters: a
     * slot's own history is the last session that settled one of its occurrences, which is what
     * lets two slots pointing at one template progress apart. A session that settled nothing —
     * a template no slot names, or a week whose slots are all taken — is absent.
     */
    fun sessionAssignments(
        slots: List<ProgramSlot>,
        sessions: List<ProgramSession>,
    ): List<Pair<ProgramSession, SlotOccurrence>> {
        val dated = slots.filter { it.weekday != null }.sortedBy { it.position }
        val resolved = mutableSetOf<SlotOccurrence>()
        val assignments = mutableListOf<Pair<ProgramSession, SlotOccurrence>>()

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
            val occurrence = SlotOccurrence(chosen.id, weekStart, occurrenceDate(weekStart, chosen.weekday!!))
            resolved += occurrence
            assignments += session to occurrence
        }
        return assignments
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

    /**
     * One month of adherence: which scheduled occurrences happened, which were skipped, which
     * were missed, and which days were trained (ROADMAP P3.5).
     *
     * **Done, skipped and missed come from one set of definitions.** *Done* is an occurrence
     * settled by a session that was **finished** — [resolvedOccurrences] over the window's
     * finished sessions, which is deliberately stricter than the prompt, where a started
     * session silences a day P3.3 would otherwise nag about. *Skipped* is a [RecordedSkip] for
     * that slot and week. *Missed* is elapsed and scheduled and neither of the others.
     *
     * **Only the occurrence's own day is scored, and only a day strictly before [today] can
     * have been missed.** A Friday slot on a Wednesday has not been missed yet, and a done
     * occurrence counts whenever it fell — including early, which is how P3.3 matches it.
     *
     * A slot with no weekday is never scored: it has no day to miss and is order-only.
     */
    fun monthAdherence(
        slots: List<ProgramSlot>,
        sessions: List<AdherenceSession>,
        skips: List<RecordedSkip>,
        month: YearMonth,
        today: LocalDate,
    ): MonthAdherence {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()

        // A trained day is a finished session's own day, and needs no schedule: a workout
        // started by hand marks its day whether or not a program is active.
        val trained = sessions.map { it.date }.filter { it in first..last }.toSet()

        // Only a finished session settles an occurrence here. An abandoned start is a miss
        // (P3.5): what this asks is whether the training happened, not whether to nag.
        val finished = sessions.mapNotNull { session ->
            session.templateId?.let {
                ProgramSession(
                    sessionId = session.sessionId,
                    templateId = it,
                    startedAt = session.startedAt,
                    zone = session.zone,
                )
            }
        }
        val resolved = resolvedOccurrences(slots, finished)
        val skipped = skips.toSet()

        val scheduledDays = mutableSetOf<LocalDate>()
        var done = 0
        var skipCount = 0
        var missed = 0

        for (week in weeksOf(month)) {
            slots.forEach { slot ->
                val weekday = slot.weekday ?: return@forEach
                val date = occurrenceDate(week, weekday)
                if (date !in first..last) return@forEach
                scheduledDays += date

                when {
                    SlotOccurrence(slot.id, week, date) in resolved -> done++
                    RecordedSkip(slot.id, week) in skipped -> skipCount++
                    date.isBefore(today) -> missed++
                }
            }
        }

        return MonthAdherence(
            done = done,
            skipped = skipCount,
            missed = missed,
            trainedDays = trained,
            scheduledDays = scheduledDays,
        )
    }

    /**
     * The Monday-start weeks that contain at least one day of [month], in order.
     *
     * Every scored occurrence falls in one of them, and a skip row is keyed by exactly one of
     * these Mondays, so this is the range the repository has to read skips for.
     */
    private fun weeksOf(month: YearMonth): List<LocalDate> {
        val firstWeek = weekStartOf(month.atDay(1))
        val lastWeek = weekStartOf(month.atEndOfMonth())
        return generateSequence(firstWeek) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(lastWeek) }
            .toList()
    }
}
