package com.example.androidapp.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.DataError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The name-only creation dialog (ROADMAP N2).
 *
 * The rule these assert is that a nameless exercise cannot be created: there is
 * no sensible "unspecified" name to fall back to, unlike the taxonomy fields.
 */
@RunWith(AndroidJUnit4::class)
class NewExerciseDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var created: String? = null
    private var dismissed = false

    private fun show(error: DataError? = null) {
        composeTestRule.setContent {
            NewExerciseDialog(
                error = error,
                onDismiss = { dismissed = true },
                onCreate = { created = it },
            )
        }
    }

    @Test
    fun addIsDisabled_untilANameIsTyped() {
        show()

        composeTestRule.onNodeWithTag(TestTags.NEW_EXERCISE_SAVE).assertIsNotEnabled()

        composeTestRule.onNodeWithTag(TestTags.NEW_EXERCISE_NAME).performTextInput("Sled Push")

        composeTestRule.onNodeWithTag(TestTags.NEW_EXERCISE_SAVE).assertIsEnabled()
    }

    @Test
    fun aWhitespaceOnlyName_doesNotEnableAdd() {
        show()

        composeTestRule.onNodeWithTag(TestTags.NEW_EXERCISE_NAME).performTextInput("   ")

        composeTestRule.onNodeWithTag(TestTags.NEW_EXERCISE_SAVE).assertIsNotEnabled()
    }

    @Test
    fun adding_reportsTheTrimmedName() {
        show()

        composeTestRule.onNodeWithTag(TestTags.NEW_EXERCISE_NAME).performTextInput("  Sled Push  ")
        composeTestRule.onNodeWithTag(TestTags.NEW_EXERCISE_SAVE).performClick()

        assertEquals("Sled Push", created)
    }

    @Test
    fun cancel_dismissesWithoutCreating() {
        show()

        composeTestRule.onNodeWithTag(TestTags.NEW_EXERCISE_CANCEL).performClick()

        assertEquals(true, dismissed)
        assertEquals(null, created)
    }

    @Test
    fun aFailedWrite_isShownInsideTheDialog() {
        show(error = DataError.Storage(IOException("disk full")))

        composeTestRule.onNodeWithText("Couldn’t save that. Your last change may not be stored.")
            .assertIsDisplayed()
    }
}
