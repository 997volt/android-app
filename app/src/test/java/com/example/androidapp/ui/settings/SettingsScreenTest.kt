package com.example.androidapp.ui.settings

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.DataError
import com.example.androidapp.ui.components.TestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings screen (ROADMAP B23).
 *
 * One setting so far, and the assertions are the ones that matter for it: the screen shows the
 * stored value, a tap sends the choice, and the chip that is already in force reads as selected —
 * without which the screen would not say which rest is in use.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val chosen = mutableListOf<Int>()
    private var backs = 0

    private fun show(state: SettingsUiState) {
        composeTestRule.setContent {
            SettingsScreen(
                state = state,
                onSetDefaultRest = { chosen += it },
                onBack = { backs++ },
            )
        }
    }

    @Test
    fun theStoredValue_isShown_asATime() {
        show(SettingsUiState(defaultRestSeconds = 90))

        composeTestRule.onNodeWithText("Now: 1:30").assertExists()
        composeTestRule.onNodeWithTag(TestTags.settingRest(90)).assertIsSelected()
    }

    @Test
    fun tappingAChoice_sendsIt() {
        show(SettingsUiState(defaultRestSeconds = 90))

        composeTestRule.onNodeWithTag(TestTags.settingRest(30)).performClick()

        assertEquals(listOf(30), chosen)
    }

    @Test
    fun aFailure_isSaidOutLoud() {
        // The stored-vs-tapped rule means a refused choice leaves the old value in force; the
        // screen has to say why, or nothing distinguishes it from a tap that did not register.
        show(
            SettingsUiState(
                defaultRestSeconds = 90,
                error = DataError.Invalid("a rest must be between 5 seconds and an hour"),
            ),
        )

        composeTestRule.onNodeWithText("a rest must be between 5 seconds and an hour").assertExists()
    }

    @Test
    fun back_leaves() {
        show(SettingsUiState())

        // The back control is icon-only, so its label is a content description (N21's rule that
        // accessibility accompanies each screen).
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }
}
