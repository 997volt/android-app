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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
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
            deloads = emptyList(),
            month = october,
            today = today,
        )

        assertThat(adherence.done).isEqualTo(1)
        assertThat(adherence.missed).isEqualTo(0)
        assertThat(adherence.trainedDays)
            .containsExactly(monday.plusDays(1), monday.plusDays(2))
    }

    @Test
    fun aDeloadWeek_isNeitherDoneSkippedNorMissed_butItsDaysAreStillDrawn() {
        // ROADMAP P3.10: exempt from the ratio, not from the calendar.
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = listOf(session("t-tue", monday.plusDays(1))),
            skips = emptyList(),
            deloads = listOf(RecordedDeload(programId = "p1", weekStart = monday)),
            month = october,
            today = today,
        )

        assertThat(adherence.scored).isEqualTo(0)
        // The day is still scheduled, and the session in it still marks the day trained.
        assertThat(adherence.scheduledDays).contains(monday.plusDays(1))
        assertThat(adherence.trainedDays).contains(monday.plusDays(1))
    }

    @Test
    fun aDeloadOfAnotherProgram_doesNotExemptThisOne() {
        // The event is keyed by program as well as week: one block backing off is not another's.
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = emptyList(),
            skips = emptyList(),
            deloads = listOf(RecordedDeload(programId = "other", weekStart = monday)),
            month = october,
            today = today,
        )

        assertThat(adherence.missed).isEqualTo(1)
    }

    @Test
    fun occurrencesOn_reportsEachOccurrenceOfTheDay_withItsState() {
        // ROADMAP P3.13: the unit is the occurrence, exactly as the ratio's is (P3.5).
        val occurrences = ProgramSchedule.occurrencesOn(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = listOf(session("t-tue", monday.plusDays(1))),
            skips = emptyList(),
            date = monday.plusDays(1),
            today = today,
        )

        assertThat(occurrences.map { it.state }).containsExactly(OccurrenceState.DONE)
        // A finished session is the record; a skip row cannot argue with it.
        assertThat(occurrences.single().canCorrect).isFalse()
    }

    @Test
    fun aMissedOccurrence_canBeCorrected_andASkippedOneToo() {
        val occurrences = ProgramSchedule.occurrencesOn(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = emptyList(),
            skips = emptyList(),
            date = monday.plusDays(1),
            today = today,
        )
        assertThat(occurrences.single().state).isEqualTo(OccurrenceState.MISSED)
        assertThat(occurrences.single().canCorrect).isTrue()

        val skipped = ProgramSchedule.occurrencesOn(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = emptyList(),
            skips = listOf(RecordedSkip(slotId = "tue", weekStart = monday)),
            date = monday.plusDays(1),
            today = today,
        )
        assertThat(skipped.single().state).isEqualTo(OccurrenceState.SKIPPED)
        assertThat(skipped.single().canCorrect).isTrue()
    }

    @Test
    fun addingLooksBackwardsOnly_aFutureOccurrenceCannotBeSkipped() {
        // P3.3's rule, kept by P3.13: a day passed over is behind you.
        val future = ProgramSchedule.occurrencesOn(
            slots = listOf(slot("fri", DayOfWeek.FRIDAY)),
            sessions = emptyList(),
            skips = emptyList(),
            date = monday.plusDays(4),
            today = today,
        )
        assertThat(future.single().state).isEqualTo(OccurrenceState.PENDING)
        assertThat(future.single().canCorrect).isFalse()

        // Today is not behind you either, but it is the day you can still decide about.
        val todayItself = ProgramSchedule.occurrencesOn(
            slots = listOf(slot("thu", DayOfWeek.THURSDAY)),
            sessions = emptyList(),
            skips = emptyList(),
            date = today,
            today = today,
        )
        assertThat(todayItself.single().state).isEqualTo(OccurrenceState.PENDING)
        assertThat(todayItself.single().canCorrect).isTrue()
    }

    @Test
    fun aDeloadWeek_offersNothingToCorrect() {
        val occurrences = ProgramSchedule.occurrencesOn(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = emptyList(),
            skips = emptyList(),
            date = monday.plusDays(1),
            today = today,
            deloads = listOf(RecordedDeload(programId = "p1", weekStart = monday)),
        )

        assertThat(occurrences.single().state).isEqualTo(OccurrenceState.DELOAD)
        assertThat(occurrences.single().canCorrect).isFalse()
    }

    @Test
    fun thePerSlotBreakdown_sumsToTheWhole() {
        // ROADMAP P3.14: the parts are the whole, accumulated in one pass rather than recomputed.
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(
                slot("mon", DayOfWeek.MONDAY, position = 0, templateId = "t-mon"),
                slot("thu", DayOfWeek.THURSDAY, position = 1, templateId = "t-thu"),
            ),
            sessions = emptyList(),
            skips = emptyList(),
            month = october,
            today = today,
        )

        // Monday the 5th is behind us and untrained, as is Thursday the 1st; Thursday the 8th is
        // today, so it is drawn but not yet scored.
        assertThat(adherence.bySlot.map { it.slotId }).containsExactly("mon", "thu").inOrder()
        assertThat(adherence.missed).isEqualTo(2)
        assertThat(adherence.bySlot.sumOf { it.done }).isEqualTo(adherence.done)
        assertThat(adherence.bySlot.sumOf { it.skipped }).isEqualTo(adherence.skipped)
        assertThat(adherence.bySlot.sumOf { it.missed }).isEqualTo(adherence.missed)
        // The slot's own template travels with the row — the join the per-lift rollup needs, and
        // what names the day on screen.
        assertThat(adherence.bySlot.single { it.slotId == "mon" }.templateId).isEqualTo("t-mon")
    }

    @Test
    fun thePerLiftBreakdown_rollsUpTheSlotsThatPrescribeTheLift() {
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(
                slot("mon", DayOfWeek.MONDAY, position = 0, templateId = "t-a"),
                slot("tue", DayOfWeek.TUESDAY, position = 1, templateId = "t-b"),
            ),
            sessions = emptyList(),
            skips = emptyList(),
            exercisesByTemplate = mapOf(
                "t-a" to listOf("squat"),
                "t-b" to listOf("squat", "bench"),
            ),
            exerciseNames = mapOf("squat" to "Squat", "bench" to "Bench press"),
            month = october,
            today = today,
        )

        // Ordered by name, so the list does not reorder itself month to month.
        assertThat(adherence.byExercise.map { it.exerciseName })
            .containsExactly("Bench press", "Squat")
            .inOrder()
        // A lift trained by two slots is counted in both on purpose: the question is whether this
        // lift keeps being skipped, not whether this day does.
        assertThat(adherence.byExercise.single { it.exerciseId == "squat" }.scored).isEqualTo(2)
        assertThat(adherence.byExercise.single { it.exerciseId == "bench" }.scored).isEqualTo(1)
    }

    @Test
    fun withNoJoinToRollUp_thereIsNoPerLiftBreakdown() {
        val adherence = ProgramSchedule.monthAdherence(
            slots = listOf(slot("mon", DayOfWeek.MONDAY, templateId = "t-a")),
            sessions = emptyList(),
            skips = emptyList(),
            month = october,
            today = today,
        )

        assertThat(adherence.byExercise).isEmpty()
        // The per-slot rows are still there: only the lift join was missing.
        assertThat(adherence.bySlot).hasSize(1)
    }

    @Test
    fun theStreak_countsScheduledOccurrencesDoneInARow() {
        // ROADMAP P3.15: occurrences, not days — an unscheduled day is rest, so it is invisible
        // here rather than a gap that breaks the run.
        val streak = ProgramSchedule.streak(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = listOf(
                session("t-tue", monday.minusWeeks(2)),
                session("t-tue", monday.minusWeeks(1)),
                session("t-tue", monday.plusDays(1)),
            ),
            skips = emptyList(),
            since = monday.minusWeeks(2),
            today = today,
        )

        assertThat(streak?.count).isEqualTo(3)
        assertThat(streak?.startedOn).isEqualTo(monday.minusWeeks(2).plusDays(1))
    }

    @Test
    fun aMissAndASkip_bothBreakTheRun() {
        val missed = ProgramSchedule.streak(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = emptyList(),
            skips = emptyList(),
            since = monday,
            today = today,
        )
        // Tuesday the 6th is behind us and untrained.
        assertThat(missed?.count).isEqualTo(0)
        assertThat(missed?.startedOn).isNull()

        val skipped = ProgramSchedule.streak(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = emptyList(),
            skips = listOf(RecordedSkip(slotId = "tue", weekStart = monday)),
            since = monday,
            today = today,
        )
        // A skip is the same to a run as a miss: scheduled, and not done.
        assertThat(skipped?.count).isEqualTo(0)
    }

    @Test
    fun aDeloadWeek_neitherExtendsNorBreaksTheRun() {
        val streak = ProgramSchedule.streak(
            slots = listOf(slot("tue", DayOfWeek.TUESDAY)),
            sessions = listOf(
                session("t-tue", monday.minusWeeks(2)),
                session("t-tue", monday.minusWeeks(1)),
            ),
            skips = emptyList(),
            since = monday.minusWeeks(2),
            today = today,
            deloads = listOf(RecordedDeload(programId = "p1", weekStart = monday)),
        )

        // The 6th is exempt, so the run reaches back through it to the 22nd.
        assertThat(streak?.count).isEqualTo(2)
        assertThat(streak?.startedOn).isEqualTo(monday.minusWeeks(2).plusDays(1))
    }

    @Test
    fun anUntrainedToday_neitherExtendsNorBreaksTheRun() {
        val streak = ProgramSchedule.streak(
            slots = listOf(slot("thu", DayOfWeek.THURSDAY)),
            sessions = listOf(session("t-thu", monday.minusDays(4))),
            skips = emptyList(),
            since = monday.minusDays(4),
            today = today,
        )

        // Today is Thursday the 8th, not yet trained and not passed over; the 1st was done.
        assertThat(streak?.count).isEqualTo(1)
        assertThat(streak?.startedOn).isEqualTo(monday.minusDays(4))
    }

    @Test
    fun anOrderOnlyProgram_hasNoStreak() {
        // No weekday slot means no day to miss; the pins carry no skip record either.
        val streak = ProgramSchedule.streak(
            slots = listOf(slot("a", weekday = null)),
            sessions = emptyList(),
            skips = emptyList(),
            since = monday,
            today = today,
        )

        assertThat(streak).isNull()
    }

    private companion object {
        /** A ratio of thirds is not exact in binary floating point. */
        const val TOLERANCE = 1e-9
    }
}
