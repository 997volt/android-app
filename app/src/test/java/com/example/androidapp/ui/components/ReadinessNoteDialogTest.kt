package com.example.androidapp.ui.components

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The readiness note dialog (ROADMAP N4).
 *
 * One dialog serves the prompt and the later edit, so what is asserted here is the
 * part that matters: *Skip* and *Cancel* both close without writing, and an empty
 * field saves as "no note" rather than as an empty string.
 */
@RunWith(AndroidJUnit4::class)
class ReadinessNoteDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var saved: String? = null
    private var saveCalled = false
    private var dismissed = false

    private fun show(initial: String = "", isPrompt: Boolean = true) {
        composeTestRule.setContent {
            ReadinessNoteDialog(
                initialNote = initial,
                isPrompt = isPrompt,
                onDismiss = { dismissed = true },
                onSave = {
                    saved = it
                    saveCalled = true
                },
            )
        }
    }

    @Test
    fun anExistingNote_isPrefilled() {
        show(initial = "Slept badly, legs heavy")

        composeTestRule.onNodeWithTag(TestTags.READINESS_NOTE).assertTextContains("Slept badly, legs heavy")
    }

    @Test
    fun aTypedNote_isTrimmedAndSaved() {
        show()

        composeTestRule.onNodeWithTag(TestTags.READINESS_NOTE).performTextInput("  Slept badly  ")
        composeTestRule.onNodeWithTag(TestTags.READINESS_SAVE).performClick()

        assertEquals("Slept badly", saved)
    }

    @Test
    fun savingAnEmptyNote_writesNull() {
        show()

        composeTestRule.onNodeWithTag(TestTags.READINESS_SAVE).performClick()

        assertTrue("save must still be reported", saveCalled)
        assertNull("nothing typed is not an empty note", saved)
    }

    @Test
    fun thePrompt_canBeSkipped_withoutWriting() {
        show(isPrompt = true)

        composeTestRule.onNodeWithTag(TestTags.READINESS_DISMISS).performClick()

        assertTrue(dismissed)
        assertFalse("skipping must not write", saveCalled)
    }

    @Test
    fun theEdit_canBeCancelled_withoutWriting() {
        show(initial = "Slept badly", isPrompt = false)

        composeTestRule.onNodeWithTag(TestTags.READINESS_DISMISS).performClick()

        assertTrue(dismissed)
        assertFalse("cancelling must not write", saveCalled)
    }
}
