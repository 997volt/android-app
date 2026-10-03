package com.example.androidapp.domain.model

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/**
 * The month's ratio and the days it is drawn over (ROADMAP P3.5).
 *
 * The aggregate over P3.3's occurrence matching: "how often did the days I scheduled actually
 * happen" is a joining of slots, finished sessions and skip rows, so it is stated exactly here,
 * as a pure function, with a Monday-start week taken in the session's own zone.
 */
class ProgramAdherenceTest {

    // A real Monday, so the week arithmetic is stated rather than computed by the test.
    private val monday = LocalDate.of(2026, 10, 5)
    private val october = YearMonth.of(2026, 10)

    // The Thursday of the week of the 5th: `today` is chosen so each weekday's *only* elapsed
    // occurrence in the month is the one in that week, because a recurring weekday slot is easy
    // to miscount across a month.
    private val today = LocalDate.of(2026, 10, 8)

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
        templateId: String?,
        date: LocalDate,
        zone: ZoneId = ZoneOffset.UTC,
    ) = AdherenceSession(
        // Distinct per template and day, though adherence matching never reads it.
        sessionId = "$templateId@$date",
        templateId = templateId,
        startedAt = date.atStartOfDay(zone).toInstant(),
        zone = zone,
    )

    @Test
    fun aFinishedSessionOnTheSlotsDay_isDone() {
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = listOf(session("t-tue", monday.plusDays(1))),
            skips = emptyList(),
            month = october,
            today = today,
        )

        assertThat(adherence.done).isEqualTo(1)
        assertThat(adherence.skipped).isEqualTo(0)
        assertThat(adherence.missed).isEqualTo(0)
        assertThat(adherence.ratio).isEqualTo(1.0)
        assertThat(adherence.trainedDays).containsExactly(monday.plusDays(1))
        assertThat(adherence.scheduledDays).contains(monday.plusDays(1))
    }

    @Test
    fun anElapsedScheduledDay_withNothingDoneAndNoSkip_isMissed() {
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = emptyList(),
            skips = emptyList(),
            month = october,
            today = today,
        )

        assertThat(adherence.missed).isEqualTo(1)
        assertThat(adherence.done).isEqualTo(0)
        assertThat(adherence.ratio).isEqualTo(0.0)
    }

    @Test
    fun aRecordedSkip_isSkipped_ratherThanMissed() {
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = emptyList(),
            skips = listOf(RecordedSkip(slotId = "tue", weekStart = monday)),
            month = october,
            today = today,
        )

        assertThat(adherence.skipped).isEqualTo(1)
        assertThat(adherence.missed).isEqualTo(0)
        assertThat(adherence.ratio).isEqualTo(0.0)
    }

    @Test
    fun aSkipForAnotherWeek_doesNotSettleThisOne() {
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = emptyList(),
            skips = listOf(RecordedSkip(slotId = "tue", weekStart = monday.minusWeeks(1))),
            month = october,
            today = today,
        )

        assertThat(adherence.skipped).isEqualTo(0)
        assertThat(adherence.missed).isEqualTo(1)
    }

    @Test
    fun done_winsOverASkip_forTheSameOccurrence() {
        // The prompt never writes both, but the definitions are stated in one order so the
        // aggregate cannot read a settled occurrence as a skip if a row ever did exist.
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = listOf(session("t-tue", monday.plusDays(1))),
            skips = listOf(RecordedSkip(slotId = "tue", weekStart = monday)),
            month = october,
            today = today,
        )

        assertThat(adherence.done).isEqualTo(1)
        assertThat(adherence.skipped).isEqualTo(0)
    }

    @Test
    fun aDayAfterToday_isScheduledButNeverMissed() {
        // The 1st is a Thursday, so every Friday is still ahead: a month cannot fail you for a
        // day that has not happened.
        val firstOfOctober = LocalDate.of(2026, 10, 1)
        val friday = LocalDate.of(2026, 10, 30)

        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("fri", DayOfWeek.FRIDAY)),
            sessions = emptyList(),
            skips = emptyList(),
            month = october,
            today = firstOfOctober,
        )

        assertThat(friday.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)
        assertThat(adherence.missed).isEqualTo(0)
        assertThat(adherence.done).isEqualTo(0)
        assertThat(adherence.scheduledDays).contains(friday)
    }

    @Test
    fun todaysOwnDay_isNotMissedYet() {
        // The 1st of October is itself a Thursday, and the only occurrence that has not elapsed.
        val firstOfOctober = LocalDate.of(2026, 10, 1)
        val thursday = LocalDate.of(2026, 10, 15)

        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("thu", DayOfWeek.THURSDAY)),
            sessions = emptyList(),
            skips = emptyList(),
            month = october,
            today = firstOfOctober,
        )

        assertThat(firstOfOctober.dayOfWeek).isEqualTo(DayOfWeek.THURSDAY)
        assertThat(adherence.missed).isEqualTo(0)
        assertThat(adherence.ratio).isNull()
        assertThat(adherence.scheduledDays).contains(thursday)
    }

    @Test
    fun finishingTodaysWorkout_isDone() {
        // The 1st is itself a Thursday, so today's occurrence has not elapsed a moment before now.
        val firstOfOctober = LocalDate.of(2026, 10, 1)

        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("thu", DayOfWeek.THURSDAY)),
            sessions = listOf(session("t-thu", firstOfOctober)),
            skips = emptyList(),
            month = october,
            today = firstOfOctober,
        )

        assertThat(adherence.done).isEqualTo(1)
        assertThat(adherence.ratio).isEqualTo(1.0)
    }

    @Test
    fun twoSlotsOnOneDay_areTwoOccurrences_whileTheCalendarMarksOneDay() {
        // The unit is the occurrence, not the day: P3.3 settles them one at a time, so the ratio
        // counts two and "which days did I train" counts one.
        val slots = listOf(
            slot("first", DayOfWeek.TUESDAY, position = 0, templateId = "t"),
            slot("second", DayOfWeek.TUESDAY, position = 1, templateId = "t"),
        )

        val adherence = ProgramSchedule.monthAdherence(
            slots = slots,
            sessions = listOf(session("t", monday.plusDays(1))),
            skips = emptyList(),
            month = october,
            today = today,
        )

        assertThat(adherence.done).isEqualTo(1)
        assertThat(adherence.missed).isEqualTo(1)
        assertThat(adherence.scored).isEqualTo(2)
        assertThat(adherence.trainedDays).containsExactly(monday.plusDays(1))
    }

    @Test
    fun aWeekdayLessSlot_isNeverScored_becauseItHasNoDayToMiss() {
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("any", weekday = null)),
            sessions = emptyList(),
            skips = emptyList(),
            month = october,
            today = today,
        )

        assertThat(adherence.scored).isEqualTo(0)
        assertThat(adherence.scheduledDays).isEmpty()
        assertThat(adherence.ratio).isNull()
    }

    @Test
    fun aHandStartedSession_marksADayTrainedButSettlesNoOccurrence() {
        // P3.3's inherited limit: only a session started *from* the template resolves an
        // occurrence. The day still counts as trained — that needs no schedule.
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = listOf(session(templateId = null, date = monday.plusDays(1))),
            skips = emptyList(),
            month = october,
            today = today,
        )

        assertThat(adherence.done).isEqualTo(0)
        assertThat(adherence.missed).isEqualTo(1)
        assertThat(adherence.trainedDays).containsExactly(monday.plusDays(1))
    }

    @Test
    fun withNoSlots_theDaysTrainedAreStillDrawn() {
        val adherence = ProgramSchedule.monthAdherence(
            slots = emptyList(),
            sessions = listOf(session(templateId = null, date = LocalDate.of(2026, 10, 7))),
            skips = emptyList(),
            month = october,
            today = today,
        )

        assertThat(adherence.trainedDays).containsExactly(LocalDate.of(2026, 10, 7))
        assertThat(adherence.scored).isEqualTo(0)
        assertThat(adherence.ratio).isNull()
    }

    @Test
    fun aSessionInAnotherZone_marksItsOwnDay_andSettlesItsOwnWeek() {
        // ROADMAP N25 extended to the calendar: 23:00 UTC on Monday is Tuesday morning in Tokyo.
        val tokyo = ZoneOffset.ofHours(9)
        val lateMondayUtc = monday.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(23 * 3600L)

        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = listOf(AdherenceSession("tokyo", "t-tue", lateMondayUtc, tokyo)),
            skips = emptyList(),
            month = october,
            today = today,
        )

        assertThat(lateMondayUtc.atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(monday)
        assertThat(adherence.done).isEqualTo(1)
        assertThat(adherence.trainedDays).containsExactly(monday.plusDays(1))
    }

    @Test
    fun anOccurrenceInThisMonth_canBeSettledByASessionInTheLastWeekOfTheOneBefore() {
        // Sunday the 1st of November belongs to the week starting Monday 26 October, so a workout
        // done on the Friday before is "done early" (P3.3) — and it is this month's occurrence.
        val november = YearMonth.of(2026, 11)
        val novemberFirst = LocalDate.of(2026, 11, 1)

        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("sun", DayOfWeek.SUNDAY)),
            sessions = listOf(session("t-sun", LocalDate.of(2026, 10, 30))),
            skips = emptyList(),
            month = november,
            today = LocalDate.of(2026, 11, 15),
        )

        assertThat(novemberFirst.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(adherence.done).isEqualTo(1)
        // The Sunday after it — the 8th — elapsed unworked.
        assertThat(adherence.missed).isEqualTo(1)
    }

    @Test
    fun aSessionOutsideTheMonth_trainsNoDayInIt() {
        val adherence = ProgramSchedule.monthAdherence(
            slots = emptyList(),
            sessions = listOf(session(templateId = null, date = LocalDate.of(2026, 9, 30))),
            skips = emptyList(),
            month = october,
            today = today,
        )

        assertThat(adherence.trainedDays).isEmpty()
    }

    @Test
    fun theRatio_isDoneOverEveryScoredOccurrence() {
        // The 15th, so the month holds four elapsed occurrences: Tuesday the 6th and 13th, Friday
        // the 2nd and 9th. Two of them were done, one was skipped, one was missed: 2 of 4.
        val midMonth = LocalDate.of(2026, 10, 15)
        val slots = listOf(
            slot("tue", DayOfWeek.TUESDAY, position = 0, templateId = "t"),
            slot("fri", DayOfWeek.FRIDAY, position = 1, templateId = "t"),
        )

        val adherence = ProgramSchedule.monthAdherence(
            slots = slots,
            // Two sessions, so the Tuesday occurrences — one per week — are done.
            sessions = listOf(
                session("t", monday.plusDays(1)),
                session("t", monday.plusDays(8)),
            ),
            skips = listOf(RecordedSkip(slotId = "fri", weekStart = monday)),
            month = october,
            today = midMonth,
        )

        assertThat(adherence.done).isEqualTo(2)
        assertThat(adherence.skipped).isEqualTo(1)
        assertThat(adherence.missed).isEqualTo(1)
        assertThat(adherence.scored).isEqualTo(4)
        assertThat(adherence.ratio).isWithin(TOLERANCE).of(0.5)
    }

    @Test
    fun aMonthWithNoElapsedScheduledDay_saysSo_ratherThanReportingZeroOrOne() {
        // The whole month is ahead: nothing has elapsed, so there is no ratio to report.
        val nextMonth = YearMonth.of(2026, 11)

        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = emptyList(),
            skips = emptyList(),
            month = nextMonth,
            today = LocalDate.of(2026, 10, 15),
        )

        assertThat(adherence.scored).isEqualTo(0)
        assertThat(adherence.ratio).isNull()
        assertThat(adherence.scheduledDays).contains(LocalDate.of(2026, 11, 3))
    }

    @Test
    fun aSecondFinishedSessionFromTheSameTemplate_inAWeek_isUnmatched() {
        // P3.3's inherited limit, stated here because the ratio reads it: one occurrence is
        // settled by the first session, and the extra workout is a trained day that scores nothing.
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY, templateId = "t")),
            sessions = listOf(
                session("t", monday.plusDays(1)),
                session("t", monday.plusDays(2)),
            ),
            skips = emptyList(),
            month = october,
            today = today,
        )

        assertThat(adherence.done).isEqualTo(1)
        assertThat(adherence.missed).isEqualTo(0)
        assertThat(adherence.trainedDays)
            .containsExactly(monday.plusDays(1), monday.plusDays(2))
    }

    private companion object {
        /** A ratio of thirds is not exact in binary floating point. */
        const val TOLERANCE = 1e-9
    }
}
