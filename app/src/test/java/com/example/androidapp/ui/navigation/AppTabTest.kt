package com.example.androidapp.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.theme.AndroidAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bottom bar and the rules behind it (ROADMAP N34).
 *
 * The matching is on a route *string* rather than a `NavDestination`, which is what lets the rules be
 * tested without standing up a graph — and the graph test at the end checks the part that is genuinely
 * about navigation: that a tab switch leaves a back entry behind rather than a trail of tabs.
 */
@RunWith(AndroidJUnit4::class)
class AppTabTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val history = WorkoutHistory.serializer().descriptor.serialName
    private val library = ExerciseLibrary.serializer().descriptor.serialName
    private val workouts = WorkoutsHome.serializer().descriptor.serialName
    private val statistics = Statistics.serializer().descriptor.serialName

    @Test
    fun theBar_offersFiveNamedTabs_andSaysWhichIsSelected() {
        composeTestRule.setContent { AndroidAppTheme { AppTabBar(selected = AppTab.HISTORY, onSelect = {}) } }

        TestTags.TAB_WORKOUTS.let(composeTestRule::onNodeWithTag)
        listOf(
            TestTags.TAB_WORKOUTS,
            TestTags.TAB_HISTORY,
            TestTags.TAB_STATISTICS,
            TestTags.TAB_LIBRARY,
            TestTags.TAB_SETTINGS,
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertExists()
        }

        // The state a screen reader announces, which is the difference between a map and five squares.
        composeTestRule.onNodeWithTag(TestTags.TAB_HISTORY).assertIsSelected()
        composeTestRule.onNodeWithTag(TestTags.TAB_WORKOUTS).assertIsNotSelected()
    }

    @Test
    fun choosingATab_reportsThatTab() {
        var chosen: AppTab? = null
        composeTestRule.setContent {
            AndroidAppTheme { AppTabBar(selected = AppTab.WORKOUTS, onSelect = { chosen = it }) }
        }

        composeTestRule.onNodeWithTag(TestTags.TAB_LIBRARY).performClick()

        assertEquals(AppTab.LIBRARY, chosen)
    }

    @Test
    fun aTabRootMapsToItsTab_andAPushedRouteDoesNot() {
        assertEquals(AppTab.HISTORY, AppTab.forRoute(history))
        assertEquals(AppTab.WORKOUTS, AppTab.forRoute(workouts))

        // WorkoutDetail is pushed from a tab, so it is not a tab: a bar that lit up for it would be
        // claiming the detail is a destination of its own.
        val detail = WorkoutDetail("s1").let { WorkoutDetail.serializer().descriptor.serialName }
        assertNull(AppTab.forRoute(detail))
        assertNull(AppTab.forRoute(null))
    }

    @Test
    fun aRouteCarryingArguments_stillMapsToItsTab() {
        // The pattern a route with arguments compiles to has them appended, which is why the match is a
        // prefix: an equality would miss exactly the routes that carry data.
        assertTrue(AppTab.STATISTICS.isCurrent("$statistics?from={from}"))
    }

    @Test
    fun theBarHidesDuringAWorkout_andOnlyThere() {
        val workout = ActiveWorkout.serializer().descriptor.serialName
        val picker = ExercisePicker.serializer().descriptor.serialName

        assertFalse("a live set logger is not a tab", showsTabBar(workout))
        assertFalse("nor is the picker one step earlier", showsTabBar("$picker?templateId={templateId}"))

        assertTrue("a tab root keeps it", showsTabBar(history))
        val detail = "${WorkoutDetail.serializer().descriptor.serialName}?sessionId={sessionId}"
        assertTrue("and so does a detail pushed inside a tab", showsTabBar(detail))
        assertTrue("and before the graph settles", showsTabBar(null))
    }

    @Test
    fun switchingTabs_leavesOneBackEntry_andBackReturnsToWorkouts() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = WorkoutsHome) {
                composable<WorkoutsHome> { Text("workouts") }
                composable<WorkoutHistory> { Text("history") }
                composable<ExerciseLibrary> { Text("library") }
                composable<Statistics> { Text("statistics") }
                composable<Settings> { Text("settings") }
            }
        }

        composeTestRule.runOnIdle { navController.switchTab(AppTab.HISTORY) }
        composeTestRule.waitForIdle()
        assertEquals(history, navController.currentDestination?.route)

        composeTestRule.runOnIdle { navController.switchTab(AppTab.LIBRARY) }
        composeTestRule.waitForIdle()
        assertEquals(library, navController.currentDestination?.route)

        // Coming back finds the tab rather than rebuilding a path through the one in between.
        composeTestRule.runOnIdle { navController.switchTab(AppTab.HISTORY) }
        composeTestRule.waitForIdle()
        assertEquals(history, navController.currentDestination?.route)

        // And back from a tab root lands on Workouts, not on the tab visited before it.
        composeTestRule.runOnIdle { navController.popBackStack() }
        composeTestRule.waitForIdle()
        assertEquals(workouts, navController.currentDestination?.route)
    }
}
