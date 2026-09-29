package com.example.androidapp.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.ui.theme.AndroidAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settles whether a failed write is *announced* or merely *drawn* (ROADMAP P1.17).
 *
 * F7 exists to surface write failures, but a snackbar that a screen reader never
 * reads surfaces nothing at all to the user who needs it most. The app shows every
 * failure through a Material3 `Snackbar`, so the question is whether that component
 * already carries a live region. If it does, the correct change is to record that
 * with a test rather than to add a redundant announcement on top.
 *
 * Written against `Snackbar` directly rather than through a screen, so the result
 * is about the component and cannot be confused by a screen's own timing.
 */
@RunWith(AndroidJUnit4::class)
class SnackbarAnnouncementTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun materialSnackbarsCarryALiveRegion() {
        composeTestRule.setContent {
            AndroidAppTheme {
                Snackbar { Text("Couldn't save that. Your last change may not be stored.") }
            }
        }

        composeTestRule
            .onNodeWithText("Couldn't save that. Your last change may not be stored.")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun andItIsPolite_soItDoesNotInterruptWhateverTheUserIsDoing() {
        // Polite rather than Assertive: a failed save should wait its turn behind
        // the field the user is in, not cut across them mid-entry.
        composeTestRule.setContent {
            AndroidAppTheme {
                Snackbar { Text("Couldn't save that.") }
            }
        }

        composeTestRule
            .onNodeWithText("Couldn't save that.")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }
}
