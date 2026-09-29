package com.example.androidapp.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
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
 *
 * **The live region is sought on any node, not on the text node.** It is applied to
 * the snackbar container, and `Modifier.semantics` does not merge descendants — so
 * the region and the text legitimately live on different nodes. Asserting
 * `onNodeWithText(...)` carries it looked reasonable and was simply wrong; that
 * mismatch is what failed twice in CI before this was understood.
 */
@RunWith(AndroidJUnit4::class)
class SnackbarAnnouncementTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * `SnackbarDuration.Indefinite` on purpose: it keeps the snackbar on screen
     * until dismissed, so the assertion cannot race a four-second timeout.
     */
    private fun showFailure(message: String = MESSAGE) {
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

        val liveRegion = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        composeTestRule.onNode(liveRegion).assertExists()
        // The message must actually be on screen too, or the region announces
        // nothing.
        composeTestRule.onNodeWithText(MESSAGE).assertIsDisplayed()
    }

    @Test
    fun andItIsPolite_soItWaitsBehindWhateverTheUserIsDoing() {
        // Assertive would cut across the user mid-entry; a failed save can wait.
        showFailure()

        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }

    private companion object {
        const val MESSAGE = "Couldn't save that. Your last change may not be stored."
    }
}
