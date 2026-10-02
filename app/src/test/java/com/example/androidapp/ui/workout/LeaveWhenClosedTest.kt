package com.example.androidapp.ui.workout

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Leaving a screen exactly once (ROADMAP B44).
 *
 * The workout route closed itself from two `LaunchedEffect(closed)` blocks, so a discard
 * popped the back stack twice and left the navigation host blank. These pin the behaviour the
 * route now delegates: nothing happens while the screen is open, the leave happens on the
 * transition, and a screen that is already closed does not leave again when it recomposes.
 *
 * What this cannot see is a *second* call site in the route — the duplication is removed by
 * construction, not caught here. Only a navigation test that renders the real graph could
 * count pops, and the project has no route-level harness to hang one on.
 */
@RunWith(AndroidJUnit4::class)
class LeaveWhenClosedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whileTheScreenIsOpen_itIsNotLeft() {
        var leaves = 0
        composeTestRule.setContent {
            LeaveWhenClosed(closed = false, onLeave = { leaves++ })
        }

        composeTestRule.runOnIdle { assertEquals(0, leaves) }
    }

    @Test
    fun closing_leavesOnce() {
        var leaves = 0
        val closed = mutableStateOf(false)
        composeTestRule.setContent {
            LeaveWhenClosed(closed = closed.value, onLeave = { leaves++ })
        }

        composeTestRule.runOnIdle { assertEquals(0, leaves) }

        composeTestRule.runOnIdle { closed.value = true }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle { assertEquals(1, leaves) }
    }

    @Test
    fun aScreenThatStaysClosed_doesNotLeaveAgainWhenItRecomposes() {
        var leaves = 0
        val closed = mutableStateOf(false)
        // Read by the content lambda, so changing it recomposes the parent — the case that
        // would restart the effect if `closed` were not the only key.
        val recompositions = mutableIntStateOf(0)

        composeTestRule.setContent {
            recompositions.intValue
            LeaveWhenClosed(closed = closed.value, onLeave = { leaves++ })
        }

        composeTestRule.runOnIdle { closed.value = true }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle { recompositions.intValue++ }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { recompositions.intValue++ }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle { assertEquals(1, leaves) }
    }
}
