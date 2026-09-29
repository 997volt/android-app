package com.example.androidapp.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.ui.components.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the number steppers (ROADMAP P1.3a).
 *
 * `Weight.step` already had unit tests and no callers. These are about the wiring
 * the unit tests cannot see: that a tap actually moves the field, and that the
 * floors hold at the extremes.
 */
@RunWith(AndroidJUnit4::class)
class SetEditorDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var saved: Pair<Int, Long>? = null

    private fun show(reps: Int = 5, weightGrams: Long = 100_000L) {
        composeTestRule.setContent {
            SetEditorDialog(
                initialReps = reps,
                initialWeightGrams = weightGrams,
                onDismiss = {},
                onSave = { r, w -> saved = r to w },
            )
        }
    }

    @Test
    fun steppingWeight_addsTheDefaultStep() {
        show(weightGrams = 100_000L)

        // 100 kg, and the default step is 2.5 kg.
        composeTestRule.onNodeWithTag(TestTags.SET_INCREASE_WEIGHT).performClick()

        composeTestRule.onNodeWithTag(TestTags.SET_WEIGHT_FIELD).assertTextContains("102.5")
    }

    @Test
    fun steppingRepsDown_stopsAtOne() {
        // A set of zero reps is not a set, so this floor is 1 — unlike weight,
        // where 0 is meaningful.
        show(reps = 1)

        composeTestRule.onNodeWithTag(TestTags.SET_DECREASE_REPS).performClick()

        composeTestRule.onNodeWithTag(TestTags.SET_REPS_FIELD).assertTextContains("1")
    }

    @Test
    fun anUnusableWeight_disablesSave_ratherThanGuessing() {
        show()

        composeTestRule.onNodeWithTag(TestTags.SET_WEIGHT_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(TestTags.SET_WEIGHT_FIELD).performTextInput("not a number")

        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).assertIsNotEnabled()
    }

    @Test
    fun aZeroWeightIsAccepted_becauseBodyweightSetsHaveNoExternalLoad() {
        // This is the bodyweight decision, asserted rather than assumed: a
        // push-up is reps at 0 kg, and it must be savable today.
        show(weightGrams = 0L)

        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).assertIsEnabled().performClick()

        assert(saved == 5 to 0L) { "expected a 0 kg set to save, got $saved" }
    }

    @Test
    fun steppingWeightDown_stopsAtZero() {
        show(weightGrams = 1_000L)

        composeTestRule.onNodeWithTag(TestTags.SET_DECREASE_WEIGHT).performClick()
        composeTestRule.onNodeWithTag(TestTags.SET_DECREASE_WEIGHT).performClick()

        // Clamped by Weight.step, not by a second copy of the rule here.
        composeTestRule.onNodeWithTag(TestTags.SET_WEIGHT_FIELD).assertTextContains("0")
    }
}
