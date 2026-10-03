package com.example.androidapp.ui.adherence

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.model.DayOccurrence
import com.example.androidapp.domain.model.MonthAdherence
import com.example.androidapp.domain.model.SlotAdherence
import com.example.androidapp.domain.model.Streak
import com.example.androidapp.domain.model.ExerciseAdherence
import com.example.androidapp.domain.model.OccurrenceState
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the Adherence screen offers (ROADMAP P3.5). */
@RunWith(AndroidJUnit4::class)
class AdherenceScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val october = YearMonth.of(2026, 10)

    private fun setScreen(
        state: AdherenceUiState = AdherenceUiState(
            month = october,
            currentMonth = october,
            isLoading = false,
            adherence = MonthAdherence(
                done = 3,
                skipped = 1,
                missed = 0,
                trainedDays = setOf(LocalDate.of(2026, 10, 6)),
                scheduledDays = setOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 13)),
            ),
        ),
        onPreviousMonth: () -> Unit = {},
        onNextMonth: () -> Unit = {},
        onBack: () -> Unit = {},
        onToggleDeload: (String, LocalDate, Boolean) -> Unit = { _, _, _ -> },
        onSelectDay: (LocalDate) -> Unit = {},
        onDismissDay: () -> Unit = {},
        onSkipToggle: (DayOccurrence, Boolean) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            AndroidAppTheme {
                AdherenceScreen(
                    state = state,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onBack = onBack,
                    onToggleDeload = onToggleDeload,
                    onSelectDay = onSelectDay,
                    onDismissDay = onDismissDay,
                    onSkipToggle = onSkipToggle,
                )
            }
        }
    }

    @Test
    fun theRatio_isShown_asAPercentage_andAsCounts() {
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Adherence.RATIO).assertTextEquals("75%")
        composeTestRule.onNodeWithTag(TestTags.Adherence.SUMMARY).assertTextEquals("Scheduled: 4")
        composeTestRule.onNodeWithTag(TestTags.Adherence.DONE).assertTextEquals("3")
        composeTestRule.onNodeWithTag(TestTags.Adherence.SKIPPED).assertTextEquals("1")
        composeTestRule.onNodeWithTag(TestTags.Adherence.MISSED).assertTextEquals("0")
    }

    @Test
    fun aMonthWithNothingElapsedScheduled_saysSo_ratherThanShowingZeroPercent() {
        // Neither 0% nor 100% is true of a month with nothing to score, so neither is shown.
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                adherence = MonthAdherence(),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.NO_RATIO).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.RATIO).assertDoesNotExist()
        composeTestRule.onNodeWithText("Nothing was scheduled this month.").assertExists()
    }

    @Test
    fun withNoActiveProgram_itSaysTheCalendarStillMarksTheDays() {
        // A different fact from an unscheduled month, and worded apart because only one of them
        // is the user's to fix.
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                hasActiveProgram = false,
                adherence = MonthAdherence(trainedDays = setOf(LocalDate.of(2026, 10, 6))),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.NO_RATIO).assertExists()
        composeTestRule.onNodeWithText(
            "No program is active, so there is nothing to score. The calendar still marks the days you trained.",
        ).assertExists()
        // The day is still drawn, which is the point: it needs no schedule.
        composeTestRule.onNodeWithTag(TestTags.Adherence.dayCell("2026-10-06")).assertExists()
    }

    @Test
    fun theGrid_drawsEveryDayOfTheShownMonth() {
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Adherence.dayCell("2026-10-01")).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.dayCell("2026-10-31")).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.dayCell("2026-11-01")).assertDoesNotExist()
    }

    @Test
    fun aTrainedDay_isAnnounced_asTrained_notOnlyDrawn() {
        // A filled circle says nothing to a screen reader, so the state is part of the label.
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Adherence.dayCell("2026-10-06"))
            .assertContentDescriptionEquals("Oct 6, 2026, trained")
    }

    @Test
    fun aScheduledDayNobodyTrained_isAnnounced_asScheduled() {
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Adherence.dayCell("2026-10-13"))
            .assertContentDescriptionEquals("Oct 13, 2026, scheduled")
    }

    @Test
    fun aRestDay_isAnnounced_asRest() {
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Adherence.dayCell("2026-10-15"))
            .assertContentDescriptionEquals("Oct 15, 2026, rest")
    }

    @Test
    fun theMonth_isNamed_andNavigable() {
        var back = false
        var forward = false
        setScreen(
            state = AdherenceUiState(
                month = october.minusMonths(1),
                currentMonth = october,
                isLoading = false,
            ),
            onPreviousMonth = { back = true },
            onNextMonth = { forward = true },
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.MONTH).assertTextEquals("September 2026")
        composeTestRule.onNodeWithTag(TestTags.Adherence.NEXT_MONTH).assertIsEnabled()

        composeTestRule.onNodeWithTag(TestTags.Adherence.PREVIOUS_MONTH).performClick()
        composeTestRule.onNodeWithTag(TestTags.Adherence.NEXT_MONTH).performClick()

        assertThat(back).isTrue()
        assertThat(forward).isTrue()
    }

    @Test
    fun onTheCurrentMonth_forwardIsClosed() {
        // Forward no further than the month it is now: a month that has not happened has nothing
        // to score.
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Adherence.NEXT_MONTH).assertIsNotEnabled()
    }

    @Test
    fun goingBack_isAlwaysOffered() {
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Adherence.PREVIOUS_MONTH).assertIsEnabled()
    }

    @Test
    fun loading_saysSo_ratherThanDrawingAnEmptyMonth() {
        setScreen(state = AdherenceUiState(month = october, currentMonth = october, isLoading = true))

        composeTestRule.onNodeWithText("Loading adherence…").assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.dayCell("2026-10-01")).assertDoesNotExist()
    }

    @Test
    fun aFailedRead_saysSo_ratherThanDrawingAnEmptyMonth() {
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                error = DataError.Storage(IOException("disk full")),
            ),
        )

        composeTestRule.onNodeWithText("Couldn’t save that. Your last change may not be stored.")
            .assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.dayCell("2026-10-01")).assertDoesNotExist()
    }

    @Test
    fun aStartedWeek_canBeMarkedAsADeload_forItsProgram() {
        // ROADMAP P3.10: an event keyed by program and week, marked where the ratio is read.
        val week = LocalDate.of(2026, 10, 5)
        var toggled: Triple<String, LocalDate, Boolean>? = null
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                programs = listOf(WorkoutProgram(id = "p1", name = "Upper/Lower", isActive = true)),
                markableWeeks = listOf(week),
            ),
            onToggleDeload = { programId, weekStart, marked ->
                toggled = Triple(programId, weekStart, marked)
            },
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.DELOAD_TITLE).assertExists()
        composeTestRule.onNodeWithText("Week of Oct 5, 2026").assertExists()
        // The section is below the calendar in a scrolling column, so the click scrolls first.
        composeTestRule.onNodeWithTag(TestTags.Adherence.deloadWeek("2026-10-05", "p1"))
            .performScrollTo()
            .performClick()

        assertThat(toggled).isEqualTo(Triple("p1", week, true))
    }

    @Test
    fun aWeekAlreadyMarked_isToggledOff() {
        val week = LocalDate.of(2026, 10, 5)
        var marked: Boolean? = null
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                programs = listOf(WorkoutProgram(id = "p1", name = "Upper/Lower", isActive = true)),
                deloadWeeks = mapOf("p1" to setOf(week)),
                markableWeeks = listOf(week),
            ),
            onToggleDeload = { _, _, isMarked -> marked = isMarked },
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.deloadWeek("2026-10-05", "p1"))
            .performScrollTo()
            .performClick()

        assertThat(marked).isFalse()
    }

    @Test
    fun theRunOfScheduledWork_isShownWithItsStart() {
        // ROADMAP P3.15: a number with its start, never a nudge.
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                adherence = MonthAdherence(scheduledDays = setOf(LocalDate.of(2026, 10, 6))),
                streak = Streak(count = 3, startedOn = LocalDate.of(2026, 10, 1)),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.STREAK).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.STREAK_START).assertExists()
        composeTestRule.onNodeWithText("3 sessions done in a row").assertExists()
    }

    @Test
    fun withNoProgram_thereIsNoRunToShow() {
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                hasActiveProgram = false,
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.STREAK).assertDoesNotExist()
    }

    @Test
    fun theMonth_isBrokenDownByDayAndByLift() {
        // ROADMAP P3.14: the same aggregate, read two ways.
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                adherence = MonthAdherence(
                    done = 1,
                    skipped = 1,
                    missed = 1,
                    scheduledDays = setOf(LocalDate.of(2026, 10, 6)),
                    bySlot = listOf(
                        SlotAdherence(
                            slotId = "s1",
                            templateId = "t1",
                            templateName = "Heavy lower",
                            done = 1,
                            skipped = 1,
                        ),
                    ),
                    byExercise = listOf(ExerciseAdherence("e1", "Back squat", done = 1, missed = 1)),
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.BY_SLOT_TITLE).performScrollTo().assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.slotBreakdown("s1"))
            .performScrollTo()
            .assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.BY_EXERCISE_TITLE)
            .performScrollTo()
            .assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.exerciseBreakdown("e1"))
            .performScrollTo()
            .assertExists()
        composeTestRule.onNodeWithText("Back squat").assertExists()
    }

    @Test
    fun withNothingScored_thereAreNoBreakdownSections() {
        // No active program: the pins carry no skip record, so there is nothing to break down.
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                adherence = MonthAdherence(scheduledDays = setOf(LocalDate.of(2026, 10, 6))),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.BY_SLOT_TITLE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.Adherence.BY_EXERCISE_TITLE).assertDoesNotExist()
    }

    @Test
    fun tappingAScheduledDay_opensItsOccurrences() {
        // ROADMAP P3.13: a scheduled day is the way into the correction dialog.
        val date = LocalDate.of(2026, 10, 6)
        var selected: LocalDate? = null
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                adherence = MonthAdherence(scheduledDays = setOf(date)),
            ),
            onSelectDay = { selected = it },
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.dayCell(date.toString()))
            .performScrollTo()
            .performClick()

        assertThat(selected).isEqualTo(date)
    }

    @Test
    fun aMissedOccurrence_canBeMarkedSkipped_fromTheDialog() {
        val date = LocalDate.of(2026, 10, 6)
        var toggled: Pair<DayOccurrence, Boolean>? = null
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                day = DayCorrection(
                    date = date,
                    occurrences = listOf(
                        DayOccurrence(
                            slotId = "s1",
                            templateId = "t1",
                            templateName = "Heavy lower",
                            date = date,
                            weekStart = LocalDate.of(2026, 10, 5),
                            state = OccurrenceState.MISSED,
                            canCorrect = true,
                        ),
                    ),
                ),
            ),
            onSkipToggle = { occurrence, skipped -> toggled = occurrence to skipped },
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.DAY_DIALOG).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.dayOccurrence("s1")).assertExists()
        composeTestRule.onNodeWithText("Heavy lower").assertExists()
        composeTestRule.onNodeWithText("Missed").assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.daySkipped("s1")).performClick()

        assertThat(toggled?.first?.slotId).isEqualTo("s1")
        assertThat(toggled?.second).isTrue()
    }

    @Test
    fun aDoneOccurrence_offersNoCorrection() {
        val date = LocalDate.of(2026, 10, 6)
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                day = DayCorrection(
                    date = date,
                    occurrences = listOf(
                        DayOccurrence(
                            slotId = "s1",
                            templateId = "t1",
                            templateName = "Heavy lower",
                            date = date,
                            weekStart = LocalDate.of(2026, 10, 5),
                            state = OccurrenceState.DONE,
                            canCorrect = false,
                        ),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("Done").assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.daySkipped("s1")).assertDoesNotExist()
    }

    @Test
    fun aDayWithNothingScheduled_saysSo_andCanBeClosed() {
        var dismissed = false
        setScreen(
            state = AdherenceUiState(
                month = october,
                currentMonth = october,
                isLoading = false,
                day = DayCorrection(date = LocalDate.of(2026, 10, 6), occurrences = emptyList()),
            ),
            onDismissDay = { dismissed = true },
        )

        composeTestRule.onNodeWithTag(TestTags.Adherence.DAY_EMPTY).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Adherence.DAY_CLOSE).performClick()

        assertThat(dismissed).isTrue()
    }
}
