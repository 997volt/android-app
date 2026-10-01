package com.example.androidapp.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The typed confirmation for starting over (ROADMAP N18).
 *
 * The point of the field is that "Delete everything" is pressed by accident exactly once,
 * so the assertion that matters is that the button does **nothing** until the word is
 * typed. `uiautomator` cannot check that — Compose reports `enabled` on a parent node —
 * which is why it is checked here.
 */
@RunWith(AndroidJUnit4::class)
class ClearEverythingDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var exportCalls = 0
    private var confirmCalls = 0
    private var dismissals = 0

    private fun show(withExport: Boolean = true) {
        composeTestRule.setContent {
            ClearEverythingDialog(
                onExport = if (withExport) ({ exportCalls++ }) else null,
                onConfirm = { confirmCalls++ },
                onDismiss = { dismissals++ },
            )
        }
    }

    @Test
    fun theConfirmButton_doesNothing_untilTheWordIsTyped() {
        show()

        composeTestRule.onNodeWithTag(TestTags.CLEAR_CONFIRM_ACTION).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(TestTags.CLEAR_CONFIRM_FIELD).performTextInput("delete everything")
        composeTestRule.onNodeWithTag(TestTags.CLEAR_CONFIRM_ACTION).assertIsNotEnabled()
        assertEquals("nothing was deleted by the near miss", 0, confirmCalls)
    }

    @Test
    fun theWord_makesItPressable_andItIsCaseInsensitive() {
        show()

        composeTestRule.onNodeWithTag(TestTags.CLEAR_CONFIRM_FIELD).performTextInput(" delete ")
        composeTestRule.onNodeWithTag(TestTags.CLEAR_CONFIRM_ACTION).assertIsEnabled()
        composeTestRule.onNodeWithTag(TestTags.CLEAR_CONFIRM_ACTION).performClick()

        assertEquals(1, confirmCalls)
    }

    @Test
    fun theExport_isOfferedInsideTheDialog_beforeAnythingIsConfirmed() {
        // Platform backup is off, so the file is the only thing that can survive this —
        // offering it is part of the action, not a courtesy beside it (N18).
        show()

        composeTestRule.onNodeWithTag(TestTags.CLEAR_EXPORT_FIRST).performClick()

        assertEquals(1, exportCalls)
        assertEquals("exporting is not confirming", 0, confirmCalls)
    }

    @Test
    fun withNoExportAvailable_theDialogStillConfirms() {
        // An import-only screen (no SAF) must not be able to delete without a way out, so
        // the button is simply absent rather than dead.
        show(withExport = false)

        composeTestRule.onNodeWithTag(TestTags.CLEAR_EXPORT_FIRST).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.CLEAR_CONFIRM_FIELD).performTextInput("DELETE")
        composeTestRule.onNodeWithTag(TestTags.CLEAR_CONFIRM_ACTION).assertIsEnabled()
    }

    @Test
    fun cancel_closesWithoutDeleting() {
        show()

        composeTestRule.onNodeWithTag(TestTags.CLEAR_CANCEL).performClick()

        assertEquals(1, dismissals)
        assertEquals(0, confirmCalls)
    }
}
