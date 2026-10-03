package com.example.androidapp.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/**
 * Occurrence matching and the missed-day question (ROADMAP P3.3).
 *
 * The part of programs with an answer worth arguing about: "was that a skip or a rest day"
 * is a joining of slots, sessions and skip rows, so it is tested where it can be stated
 * exactly — as a pure function, with a Monday-start week in the session's own zone.
 */
class ProgramScheduleTest {

    // A real Monday, so the week arithmetic is stated rather than computed by the test.
    private val monday = LocalDate.of(2026, 10, 5)

    private fun slot(
        id: String,
        weekday: DayOfWeek?,
        position: Int = 0,
        templateId: String = "t-$id",
    ) = ProgramSlot(
        id = id,
        programId = "p1",
        templateId = templateId,
        position = position,
        weekday = weekday,
        templateName = id,
    )

    private fun session(
        templateId: String,
        date: LocalDate,
        zone: ZoneId = ZoneOffset.UTC,
    ) = ProgramSession(
        templateId = templateId,
        startedAt = date.atStartOfDay(zone).toInstant(),
        zone = zone,
    )

    @Test
    fun weekStart_isTheMondayOfThatWeek() {
        assertThat(ProgramSchedule.weekStartOf(monday)).isEqualTo(monday)
        assertThat(ProgramSchedule.weekStartOf(monday.plusDays(6))).isEqualTo(monday)
    }

    @Test
    fun occurrenceDate_countsFromMonday() {
        assertThat(ProgramSchedule.occurrenceDate(monday, DayOfWeek.MONDAY)).isEqualTo(monday)
        assertThat(ProgramSchedule.occurrenceDate(monday, DayOfWeek.TUESDAY))
            .isEqualTo(monday.plusDays(1))
        assertThat(ProgramSchedule.occurrenceDate(monday, DayOfWeek.SUNDAY))
            .isEqualTo(monday.plusDays(6))
    }

    @Test
    fun aSessionOnTheSlotsOwnDay_resolvesIt() {
        val slots = listOf(slot("a", DayOfWeek.TUESDAY))
        val resolved = ProgramSchedule.resolvedOccurrences(slots, listOf(session("t-a", monday.plusDays(1))))

        assertThat(resolved).containsExactly(
            SlotOccurrence("a", monday, monday.plusDays(1)),
        )
    }

    @Test
    fun aSessionLaterInTheWeek_resolvesTheLatestEarlierSlot_itWasDoneLate() {
        // Tuesday's slot performed on Thursday: the latest scheduled day before it wins.
        val slots = listOf(
            slot("mon", DayOfWeek.MONDAY, position = 0, templateId = "t"),
            slot("tue", DayOfWeek.TUESDAY, position = 1, templateId = "t"),
        )

        val resolved = ProgramSchedule.resolvedOccurrences(
            slots,
            listOf(session("t", monday.plusDays(3))),
        )

        assertThat(resolved).containsExactly(
            SlotOccurrence("tue", monday, monday.plusDays(1)),
        )
    }

    @Test
    fun aSessionEarlierInTheWeek_resolvesTheEarliestLaterSlot_itWasDoneEarly() {
        // Friday's slot performed on Wednesday: the earliest day after it wins.
        val slots = listOf(
            slot("wed", DayOfWeek.WEDNESDAY, position = 0, templateId = "t"),
            slot("fri", DayOfWeek.FRIDAY, position = 1, templateId = "t"),
        )

        val resolved = ProgramSchedule.resolvedOccurrences(
            slots,
            listOf(session("t", monday.plusDays(2))),
        )

        assertThat(resolved).containsExactly(
            SlotOccurrence("wed", monday, monday.plusDays(2)),
        )
    }

    @Test
    fun twoSessions_onTwoScheduledDays_resolveBoth() {
        val slots = listOf(
            slot("mon", DayOfWeek.MONDAY, position = 0, templateId = "t"),
            slot("fri", DayOfWeek.FRIDAY, position = 1, templateId = "t"),
        )
        val sessions = listOf(
            session("t", monday),
            session("t", monday.plusDays(4)),
        )

        val resolved = ProgramSchedule.resolvedOccurrences(slots, sessions)

        // The Monday session takes Monday exactly; the Friday one is left Friday, because
        // the exact match beats "the latest earlier one" that Monday would otherwise be.
        assertThat(resolved).containsExactly(
            SlotOccurrence("mon", monday, monday),
            SlotOccurrence("fri", monday, monday.plusDays(4)),
        )
    }

    @Test
    fun twoSlotsOnTheSameDay_areResolvedOneEach_inProgramOrder() {
        val slots = listOf(
            slot("first", DayOfWeek.MONDAY, position = 0, templateId = "t"),
            slot("second", DayOfWeek.MONDAY, position = 1, templateId = "t"),
        )
        val sessions = listOf(
            session("t", monday),
            session("t", monday),
        )

        val resolved = ProgramSchedule.resolvedOccurrences(slots, sessions)

        assertThat(resolved).containsExactly(
            SlotOccurrence("first", monday, monday),
            SlotOccurrence("second", monday, monday),
        )
    }

    @Test
    fun oneSession_resolvesAtMostOneOccurrence() {
        val slots = listOf(
            slot("first", DayOfWeek.MONDAY, position = 0, templateId = "t"),
            slot("second", DayOfWeek.MONDAY, position = 1, templateId = "t"),
        )

        val resolved = ProgramSchedule.resolvedOccurrences(slots, listOf(session("t", monday)))

        assertThat(resolved).hasSize(1)
    }

    @Test
    fun anOccurrence_isResolvedByAtMostOneSession_theFirst() {
        val slots = listOf(slot("a", DayOfWeek.MONDAY))
        val sessions = listOf(
            session("t-a", monday),
            session("t-a", monday),
        )

        val resolved = ProgramSchedule.resolvedOccurrences(slots, sessions)

        assertThat(resolved).hasSize(1)
    }

    @Test
    fun aSlotWithNoDay_isNeverResolved_becauseItHasNoDayToMiss() {
        val slots = listOf(slot("any", weekday = null))

        val resolved = ProgramSchedule.resolvedOccurrences(slots, listOf(session("t-any", monday)))

        assertThat(resolved).isEmpty()
    }

    @Test
    fun aScheduledDayAlreadyPast_withNothingDone_isPending() {
        val slots = listOf(slot("tue", DayOfWeek.TUESDAY))

        val pending = ProgramSchedule.pendingOccurrences(
            slots = slots,
            sessions = emptyList(),
            skips = emptyList(),
            today = monday.plusDays(2),
        )

        assertThat(pending.map { it.slotId }).containsExactly("tue")
        assertThat(pending.single().weekday).isEqualTo(DayOfWeek.TUESDAY)
        assertThat(pending.single().date).isEqualTo(monday.plusDays(1))
        assertThat(pending.single().templateName).isEqualTo("tue")
    }

    @Test
    fun todaysOwnOccurrence_isNotPending() {
        // The workout being started right now has not been missed.
        val slots = listOf(slot("tue", DayOfWeek.TUESDAY))

        val pending = ProgramSchedule.pendingOccurrences(
            slots = slots,
            sessions = emptyList(),
            skips = emptyList(),
            today = monday.plusDays(1),
        )

        assertThat(pending).isEmpty()
    }

    @Test
    fun aDayLaterThisWeek_isNotPending_youCannotMissWhatHasNotHappened() {
        val slots = listOf(slot("fri", DayOfWeek.FRIDAY))

        val pending = ProgramSchedule.pendingOccurrences(
            slots = slots,
            sessions = emptyList(),
            skips = emptyList(),
            today = monday.plusDays(2),
        )

        assertThat(pending).isEmpty()
    }

    @Test
    fun aSessionStartedFromTheTemplate_settlesTheOccurrence() {
        val slots = listOf(slot("tue", DayOfWeek.TUESDAY))

        val pending = ProgramSchedule.pendingOccurrences(
            slots = slots,
            sessions = listOf(session("t-tue", monday.plusDays(2))),
            skips = emptyList(),
            today = monday.plusDays(2),
        )

        assertThat(pending).isEmpty()
    }

    @Test
    fun aRecordedSkip_settlesTheOccurrence() {
        val slots = listOf(slot("tue", DayOfWeek.TUESDAY))

        val pending = ProgramSchedule.pendingOccurrences(
            slots = slots,
            sessions = emptyList(),
            skips = listOf(RecordedSkip(slotId = "tue", weekStart = monday)),
            today = monday.plusDays(2),
        )

        assertThat(pending).isEmpty()
    }

    @Test
    fun aSkipForAnotherWeek_doesNotSettleThisOne() {
        val slots = listOf(slot("tue", DayOfWeek.TUESDAY))

        val pending = ProgramSchedule.pendingOccurrences(
            slots = slots,
            sessions = emptyList(),
            skips = listOf(RecordedSkip(slotId = "tue", weekStart = monday.minusWeeks(1))),
            today = monday.plusDays(2),
        )

        assertThat(pending).isNotEmpty()
    }

    @Test
    fun pendingOccurrences_comeEarliestFirst() {
        val slots = listOf(
            slot("fri", DayOfWeek.FRIDAY, position = 0),
            slot("mon", DayOfWeek.MONDAY, position = 1),
        )

        val pending = ProgramSchedule.pendingOccurrences(
            slots = slots,
            sessions = emptyList(),
            skips = emptyList(),
            today = monday.plusDays(6),
        )

        assertThat(pending.map { it.slotId }).containsExactly("mon", "fri").inOrder()
    }

    @Test
    fun aSessionInAnotherZone_belongsToTheWeekWhereItHappened() {
        // ROADMAP N25, extended to the schedule: an instant that is still Sunday in UTC is
        // Monday morning in Tokyo, and the week it settles is Tokyo's.
        val tokyo = ZoneOffset.ofHours(9)
        val slots = listOf(slot("mon", DayOfWeek.MONDAY))
        // Monday 00:00 UTC minus eight hours: Sunday 16:00 UTC, Monday 01:00 in Tokyo.
        val sundayInUtc = monday.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(8 * 3600L)

        val resolved = ProgramSchedule.resolvedOccurrences(
            slots,
            listOf(
                ProgramSession(
                    templateId = "t-mon",
                    startedAt = sundayInUtc,
                    zone = tokyo,
                ),
            ),
        )

        // Read in UTC this session belongs to the *previous* week, so a resolution taken in
        // the device's zone would miss it entirely.
        assertThat(sundayInUtc.atZone(ZoneOffset.UTC).toLocalDate().dayOfWeek)
            .isEqualTo(DayOfWeek.SUNDAY)
        assertThat(sundayInUtc.atZone(tokyo).toLocalDate()).isEqualTo(monday)
        assertThat(resolved).containsExactly(SlotOccurrence("mon", monday, monday))
    }
}
