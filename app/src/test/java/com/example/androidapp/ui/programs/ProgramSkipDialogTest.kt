package com.example.androidapp.ui.programs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.ui.components.TestTags
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The point-of-start question (ROADMAP P3.3).
 *
 * The example the roadmap writes is the assertion: *"You missed Paused Squat on Tuesday. Do
 * it now, or continue with Bench?"* — and both answers have to reach the caller, because
 * one starts the missed day and the other settles every miss this week.
 */
@RunWith(AndroidJUnit4::class)
class ProgramSkipDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(
        prompt: SkipPromptUi = SkipPromptUi(
            missedTemplateName = "Paused Squat",
            missedWeekday = DayOfWeek.TUESDAY,
            nextLabel = "Bench",
        ),
        onDoItNow: () -> Unit = {},
        onContinue: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ProgramSkipDialog(
                prompt = prompt,
                onDoItNow = onDoItNow,
                onContinue = onContinue,
                onDismiss = onDismiss,
            )
        }
    }

    @Test
    fun theQuestion_namesTheMissedWorkout_andWhatWasBeingStarted() {
        show()

        composeTestRule.onNodeWithTag(TestTags.Programs.SKIP_PROMPT).assertIsDisplayed()
        composeTestRule.onNodeWithText("Paused Squat", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Tuesday", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("continue with Bench", substring = true).assertIsDisplayed()
    }

    @Test
    fun doingItNow_reachesItsCallback() {
        var doItNow = false
        show(onDoItNow = { doItNow = true })

        composeTestRule.onNodeWithTag(TestTags.Programs.SKIP_DO_NOW).performClick()

        assertThat(doItNow).isTrue()
    }

    @Test
    fun continuing_reachesItsCallback() {
        var continued = false
        show(onContinue = { continued = true })

        composeTestRule.onNodeWithTag(TestTags.Programs.SKIP_CONTINUE).performClick()

        assertThat(continued).isTrue()
    }

    @Test
    fun withNothingToName_theSecondAnswer_isJustContinue() {
        // Starting an empty workout has no plan to name, so the button must not pretend one.
        show(
            prompt = SkipPromptUi(
                missedTemplateName = "Paused Squat",
                missedWeekday = DayOfWeek.TUESDAY,
                nextLabel = null,
            ),
        )

        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
        composeTestRule.onNodeWithText("continue with", substring = true).assertDoesNotExist()
    }
}
