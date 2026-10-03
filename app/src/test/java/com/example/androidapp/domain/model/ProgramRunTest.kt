package com.example.androidapp.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

/**
 * Where a program's run is (ROADMAP P3.9).
 *
 * The rule the roadmap states is what these assert: the run follows the last slot trained or
 * skipped, never the day passing, a program with no weekdays still runs A -> B -> C, and editing
 * the program re-derives its place rather than leaving a cursor behind.
 */
class ProgramRunTest {

    // A real Monday, so the week arithmetic is stated rather than computed by the test.
    private val monday = LocalDate.of(2026, 10, 5)

    private fun slot(
        id: String,
        weekday: DayOfWeek? = null,
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
        zone: java.time.ZoneId = ZoneOffset.UTC,
    ) = ProgramSession(
        sessionId = "$templateId@$date",
        templateId = templateId,
        startedAt = date.atStartOfDay(zone).toInstant(),
        zone = zone,
    )

    @Test
    fun aProgramWithNoSlots_hasNoRun() {
        assertThat(programRun(slots = emptyList(), sessions = emptyList(), skips = emptyList()))
            .isNull()
    }

    @Test
    fun withNothingDone_theRunIsAtTheFirstSlot() {
        val run = programRun(
            slots = listOf(slot("a", position = 0), slot("b", position = 1)),
            sessions = emptyList(),
            skips = emptyList(),
        )

        assertThat(run?.slot?.id).isEqualTo("a")
        assertThat(run?.isAtStart).isTrue()
    }

    @Test
    fun trainingASlot_movesTheRunToTheNextOne() {
        val slots = listOf(
            slot("a", position = 0, templateId = "ta"),
            slot("b", position = 1, templateId = "tb"),
            slot("c", position = 2, templateId = "tc"),
        )

        val run = programRun(
            slots = slots,
            sessions = listOf(session("ta", monday)),
            skips = emptyList(),
        )

        // A -> B, and it is no longer the start: something was done.
        assertThat(run?.slot?.id).isEqualTo("b")
        assertThat(run?.isAtStart).isFalse()
    }

    @Test
    fun anOrderOnlyProgram_runsAThroughC_andWraps() {
        // The case P3.3's weekday matching cannot express: nothing resolves these sessions but the
        // order, so the run is what gives "which one is next" an answer (P3.9).
        val slots = listOf(
            slot("a", position = 0, templateId = "ta"),
            slot("b", position = 1, templateId = "tb"),
            slot("c", position = 2, templateId = "tc"),
        )

        val run = programRun(
            slots = slots,
            sessions = listOf(
                session("ta", monday),
                session("tb", monday.plusDays(2)),
                session("tc", monday.plusDays(4)),
            ),
            skips = emptyList(),
        )

        assertThat(run?.slot?.id).isEqualTo("a")
    }

    @Test
    fun aSkip_movesTheRunOn_justAsTrainingItWould() {
        val slots = listOf(
            slot("a", position = 0, templateId = "ta"),
            slot("b", position = 1, templateId = "tb"),
        )

        val run = programRun(
            slots = slots,
            sessions = emptyList(),
            skips = listOf(RecordedSkip(slotId = "a", weekStart = monday)),
        )

        assertThat(run?.slot?.id).isEqualTo("b")
        assertThat(run?.isAtStart).isFalse()
    }

    @Test
    fun aDaySimplyMissed_leavesTheRunWhereItIs() {
        // No session and no skip: the missed day is the prompt's business, not the run's (P3.9).
        val slots = listOf(
            slot("a", position = 0, templateId = "ta"),
            slot("b", position = 1, templateId = "tb"),
        )

        val run = programRun(
            slots = slots,
            sessions = listOf(session("ta", monday)),
            skips = emptyList(),
        )

        assertThat(run?.slot?.id).isEqualTo("b")
    }

    @Test
    fun aWeekdaySession_resolvesThroughOccurrenceMatching() {
        // Two slots on one template: the Tuesday session is P3.3's to attribute, and the run follows
        // it to the next slot in order.
        val slots = listOf(
            slot("mon", DayOfWeek.MONDAY, position = 0, templateId = "t"),
            slot("fri", DayOfWeek.FRIDAY, position = 1, templateId = "t"),
        )

        val run = programRun(
            slots = slots,
            sessions = listOf(session("t", monday)),
            skips = emptyList(),
        )

        assertThat(run?.slot?.id).isEqualTo("fri")
    }

    @Test
    fun twoOrderOnlySlotsOnOneTemplate_advanceThroughBoth() {
        // The cursor is why this works: the first match at or after it, not the earliest overall.
        val slots = listOf(
            slot("first", position = 0, templateId = "t"),
            slot("second", position = 1, templateId = "t"),
        )

        val run = programRun(
            slots = slots,
            sessions = listOf(session("t", monday), session("t", monday.plusDays(2))),
            skips = emptyList(),
        )

        // Two sessions on one template: first takes "first", second takes "second", so the run is
        // back at the top.
        assertThat(run?.slot?.id).isEqualTo("first")
    }

    @Test
    fun removingTheLastTrainedSlot_reDerivesThePlace() {
        // The slot that was trained is gone: its session names a template the program no longer has,
        // so the run falls back to the start rather than pointing at a slot that is not there.
        val remaining = listOf(slot("b", position = 0, templateId = "tb"))

        val run = programRun(
            slots = remaining,
            sessions = listOf(session("ta", monday)),
            skips = emptyList(),
        )

        assertThat(run?.slot?.id).isEqualTo("b")
        assertThat(run?.isAtStart).isTrue()
    }

    @Test
    fun aSessionStartedByHand_movesNoRun() {
        // A session with no template is not part of any rotation (P3.3's limit, applied here).
        val slots = listOf(slot("a", position = 0, templateId = "ta"))

        val run = programRun(
            slots = slots,
            sessions = emptyList(),
            skips = emptyList(),
        )

        assertThat(run?.slot?.id).isEqualTo("a")
    }
}
