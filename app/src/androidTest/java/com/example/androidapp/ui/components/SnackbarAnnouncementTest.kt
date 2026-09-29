package com.example.androidapp.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
 * A failed write must be *announced*, not merely *drawn* (ROADMAP P1.17).
 *
 * Every write failure in this app reaches the user through a snackbar — that is
 * what F7 exists to do — so a snackbar a screen reader never reads surfaces nothing
 * at all to the user who needs it most.
 *
 * These assert the behaviour of [AnnouncingSnackbarHost] rather than of Material3.
 * That is deliberate: the app sets the live region itself, so a Material3 upgrade
 * cannot silently take the announcement away, and this test is checking our code.
 */
@RunWith(AndroidJUnit4::class)
class SnackbarAnnouncementTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * `SnackbarDuration.Indefinite` on purpose: it keeps the snackbar on screen
     * until dismissed, so the assertion cannot race a four-second timeout.
     */
    private fun showFailure(message: String = "Couldn't save that. Your last change may not be stored.") {
        composeTestRule.setContent {
            AndroidAppTheme {
                val hostState = remember { SnackbarHostState() }
                AnnouncingSnackbarHost(hostState)
                LaunchedEffect(Unit) {
                    hostState.showSnackbar(message, duration = SnackbarDuration.Indefinite)
                }
            }
        }
    }

    @Test
    fun aFailedWriteCarriesALiveRegion() {
        showFailure()

        composeTestRule
            .onNodeWithText("Couldn't save that. Your last change may not be stored.")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun andItIsPolite_soItWaitsBehindWhateverTheUserIsDoing() {
        // Assertive would cut across the user mid-entry; a failed save can wait.
        showFailure("Couldn't save that.")

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
