package com.example.androidapp.ui.adherence

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.model.MonthAdherence
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
    ) {
        composeTestRule.setContent {
            AndroidAppTheme {
                AdherenceScreen(
                    state = state,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onBack = onBack,
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
}
