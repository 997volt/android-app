package com.example.androidapp.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
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
 * A failed write is *announced*, not merely *drawn* (ROADMAP P1.17).
 *
 * Every write failure in this app reaches the user through a snackbar — that is
 * what F7 exists to do — so a snackbar a screen reader never reads surfaces nothing
 * at all to the user who needs it most.
 *
 * **Material3 already provides this**, which is the answer to the question this file
 * was written to ask: `Snackbar` sets `LiveRegionMode.Polite` and a pane title of
 * "Alert". So the outcome of P1.17's third item is this regression test, not a
 * product change.
 *
 * Two wrong attempts are worth recording, because both looked obviously right:
 *
 *  1. Adding our own live region "so it does not depend on a library internal". A
 *     test then found **two** nodes announcing the same message — a double
 *     announcement waiting to happen. It was reverted.
 *  2. Asserting the live region via `onNodeWithText(...)`. The snackbar does **not**
 *     merge its contents, so that finds the text *child* while the region lives on
 *     the container — a different node. The assertion failed while the behaviour was
 *     perfectly correct.
 *
 * Hence: anchor on whatever carries the region, and check the message separately.
 * That is independent of which node the library chooses, which is the point.
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
                SnackbarHost(hostState)
                LaunchedEffect(Unit) {
                    hostState.showSnackbar(message, duration = SnackbarDuration.Indefinite)
                }
            }
        }
    }

    /** The snackbar container: whatever node actually carries the live region. */
    private fun announced() =
        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))

    @Test
    fun aFailedWriteIsAnnouncedPolitely() {
        showFailure()

        announced().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            ),
        )
        // A region announcing nothing would be useless, so the message must be up.
        composeTestRule.onNodeWithText(MESSAGE).assertIsDisplayed()
    }

    @Test
    fun andItIsAnnouncedAsAnAlert() {
        // The pane title is what makes a screen reader treat this as something
        // needing attention rather than as ordinary content.
        showFailure()

        announced().assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Alert"))
    }

    private companion object {
        const val MESSAGE = "Couldn't save that. Your last change may not be stored."
    }
}
