package com.example.androidapp.ui.components

import com.example.androidapp.domain.model.SetType
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the number steppers (ROADMAP P1.3a) and for the set's
 * RPE and comment (N6).
 *
 * `Weight.step` already had unit tests and no callers. These are about the wiring
 * the unit tests cannot see: that a tap actually moves the field, that the floors
 * hold at the extremes, and that an RPE outside the scale blocks Save.
 */
@RunWith(AndroidJUnit4::class)
class SetEditorDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var saved: SetEdit? = null

    private fun show(reps: Int = 5, weightGrams: Long = 100_000L) {
        composeTestRule.setContent {
            SetEditorDialog(
                initialReps = reps,
                initialWeightGrams = weightGrams,
                onDismiss = {},
                onSave = { saved = it },
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

        assertEquals(SetEdit(reps = 5, weightGrams = 0L, rpeHalves = null, note = null), saved)
    }

    @Test
    fun steppingWeightDown_pastZero_becomesAssistance() {
        // The field is one signed number (ROADMAP N15): 1 kg down twice is 1 − 2.5,
        // and a *negative* weight is still impossible — it is assistance instead.
        // Stepping is how a machine's help is reached without typing a minus.
        show(weightGrams = 1_000L)

        // One step down from 1 kg: 1 − 2.5.
        composeTestRule.onNodeWithTag(TestTags.SET_DECREASE_WEIGHT).performClick()

        composeTestRule.onNodeWithTag(TestTags.SET_WEIGHT_FIELD).assertTextContains("-1.5")
    }

    @Test
    fun typingAMinus_storesAssistance_againstAZeroWeight() {
        // ROADMAP N15: one field, two columns.
        show(weightGrams = 0L)

        composeTestRule.onNodeWithTag(TestTags.SET_WEIGHT_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(TestTags.SET_WEIGHT_FIELD).performTextInput("-20")
        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).performClick()

        assertEquals(0L, saved?.weightGrams)
        assertEquals(20_000L, saved?.assistanceGrams)
    }

    @Test
    fun anAssistedSet_opensShowingTheMinus() {
        composeTestRule.setContent {
            SetEditorDialog(
                initialReps = 8,
                initialWeightGrams = 0L,
                initialAssistanceGrams = 20_000L,
                onDismiss = {},
                onSave = { saved = it },
            )
        }

        composeTestRule.onNodeWithTag(TestTags.SET_WEIGHT_FIELD).assertTextContains("-20")
    }

    @Test
    fun theRpeAndCommentFields_areAlwaysOffered_butMayStayEmpty() {
        // N6's decision: RPE is visible on every edit, and the one-tap log path
        // writes neither, so saving without them has to be the normal case.
        //
        // Existence rather than "displayed": the editor is taller than
        // Robolectric's default window, and what matters is that the fields are
        // always part of it, not where they land on a small screen.
        show()

        composeTestRule.onNodeWithTag(TestTags.SET_RPE_FIELD).assertExists()
        composeTestRule.onNodeWithTag(TestTags.SET_NOTE_FIELD).assertExists()

        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).performClick()

        assertEquals(SetEdit(reps = 5, weightGrams = 100_000L, rpeHalves = null, note = null), saved)
    }

    @Test
    fun anRpeAndComment_areReportedOnSave() {
        show()

        composeTestRule.onNodeWithTag(TestTags.SET_RPE_FIELD).performTextInput("8")
        composeTestRule.onNodeWithTag(TestTags.SET_NOTE_FIELD).performTextInput("Felt heavy")
        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).performClick()

        assertEquals("8 is 16 halves", 16, saved?.rpeHalves)
        assertEquals("Felt heavy", saved?.note)
    }

    @Test
    fun anRpeOutsideTheScale_disablesSave_ratherThanClampingIt() {
        // Silently turning 11 into 10 would be a lie about the set.
        show()

        composeTestRule.onNodeWithTag(TestTags.SET_RPE_FIELD).performTextInput("11")

        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).assertIsNotEnabled()
    }

    @Test
    fun anUnparseableRpe_disablesSave() {
        show()

        composeTestRule.onNodeWithTag(TestTags.SET_RPE_FIELD).performTextInput("hard")

        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).assertIsNotEnabled()
    }

    @Test
    fun theRoleSelector_changesWhatIsSaved() {
        // ROADMAP N14: `SetType` carried warm-up, drop and failure with no way to
        // reach them from the UI until this selector existed.
        show()

        composeTestRule.onNodeWithTag(TestTags.SET_ROLE).performClick()
        composeTestRule.onNodeWithTag(TestTags.setRole("TOP_SET")).performClick()
        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).performClick()

        assertEquals(SetType.TOP_SET, saved?.setType)
    }

    @Test
    fun aSetLeftAlone_isAPlainWorkingSet() {
        // The common case must not need a tap: nothing selected is NORMAL (N14).
        show()

        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).performClick()

        assertEquals(SetType.NORMAL, saved?.setType)
    }

    @Test
    fun anExistingSetsRole_isShownRatherThanReset() {
        // Editing a set you logged as a warm-up must keep it one.
        composeTestRule.setContent {
            SetEditorDialog(
                initialReps = 5,
                initialWeightGrams = 60_000L,
                initialSetType = SetType.WARMUP,
                onDismiss = {},
                onSave = { saved = it },
            )
        }

        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).performClick()

        assertEquals(SetType.WARMUP, saved?.setType)
        composeTestRule.onNodeWithText("Role: Warm-up").assertIsDisplayed()
    }

    @Test
    fun aHalfStepRpe_isAccepted_andAHalfIsNot() {
        // ROADMAP N6 extended: 9.5 is a value, 9.3 is not a claim about the set.
        show()

        composeTestRule.onNodeWithTag(TestTags.SET_RPE_FIELD).performTextInput("9.5")
        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).performClick()

        assertEquals(19, saved?.rpeHalves)
    }

    @Test
    fun anRpeFinerThanAHalf_isRefused_ratherThanRounded() {
        show()

        composeTestRule.onNodeWithTag(TestTags.SET_RPE_FIELD).performTextInput("9.3")

        composeTestRule.onNodeWithTag(TestTags.SET_SAVE).assertIsNotEnabled()
    }

    @Test
    fun anExistingHalfStepRpe_opensAsATyped() {
        composeTestRule.setContent {
            SetEditorDialog(
                initialReps = 5,
                initialWeightGrams = 100_000L,
                initialRpe = 19,
                onDismiss = {},
                onSave = { saved = it },
            )
        }

        composeTestRule.onNodeWithTag(TestTags.SET_RPE_FIELD).assertTextContains("9.5")
    }
}
