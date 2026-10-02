package com.example.androidapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.example.androidapp.R
import com.example.androidapp.ui.components.TestTags

/**
 * The five surfaces that have earned a permanent home (ROADMAP N34).
 *
 * The order is deliberate: Workouts first because it is where the app opens, Settings last because it is
 * where you go when you are not training. **Templates are not a tab** — a plan is part of working out, so
 * it lives under Workouts — and Library stays the exercise reference it is rather than becoming a second
 * way to start a session.
 *
 * Statistics is the screen that answers "how is everything going" (N35); it replaced the trends screen,
 * which drew three of the twenty-one series the registry now covers.
 */
enum class AppTab(
    /** The route this tab selects. */
    val route: Any,
    /**
     * That route's serial name, which is what a destination's pattern begins with.
     *
     * Derived from the serializer rather than written out: the generated route string is an
     * implementation detail (see Routes.kt), and a hand-copied one would drift the first time a route
     * moved package.
     */
    private val routeName: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String,
) {
    WORKOUTS(
        WorkoutsHome,
        WorkoutsHome.serializer().descriptor.serialName,
        R.string.tab_workouts,
        Icons.Filled.FitnessCenter,
        TestTags.TAB_WORKOUTS,
    ),
    HISTORY(
        WorkoutHistory,
        WorkoutHistory.serializer().descriptor.serialName,
        R.string.tab_history,
        Icons.Filled.History,
        TestTags.TAB_HISTORY,
    ),
    STATISTICS(
        Statistics,
        Statistics.serializer().descriptor.serialName,
        R.string.tab_statistics,
        Icons.Filled.Insights,
        TestTags.TAB_STATISTICS,
    ),
    LIBRARY(
        ExerciseLibrary,
        ExerciseLibrary.serializer().descriptor.serialName,
        R.string.tab_library,
        Icons.Filled.Search,
        TestTags.TAB_LIBRARY,
    ),
    SETTINGS(
        Settings,
        Settings.serializer().descriptor.serialName,
        R.string.tab_settings,
        Icons.Filled.Settings,
        TestTags.TAB_SETTINGS,
    ),
    ;

    /** True when this *is* the tab — not something pushed from it. */
    fun isCurrent(route: String?): Boolean = route.isRouteNamed(routeName)

    companion object {
        /** The tab a route is, or null when it is something pushed from one. */
        fun forRoute(route: String?): AppTab? = entries.firstOrNull { it.isCurrent(route) }
    }
}

/**
 * Whether a destination's pattern is `name` — or `name` followed by its arguments.
 *
 * A `startsWith` rather than an equality because a route with arguments compiles to a pattern like
 * `…ActiveWorkout?templateId={templateId}`, and comparing the whole string would miss exactly the routes
 * that carry data.
 */
private fun String?.isRouteNamed(name: String): Boolean = this?.startsWith(name) == true

/**
 * Whether the bar belongs on this route (ROADMAP N34).
 *
 * False for the two that take the whole screen: a live set logger with a tab bar under it is an invitation
 * to lose the session, and the exercise picker is the same flow one step earlier. Everything else keeps
 * the bar — a pushed detail is still inside a tab — and no route at all (the graph before it settles)
 * keeps it too, because a bar that flickers in is worse than one that appears a frame late.
 */
fun showsTabBar(route: String?): Boolean =
    !route.isRouteNamed(ActiveWorkout.serializer().descriptor.serialName) &&
        !route.isRouteNamed(ExercisePicker.serializer().descriptor.serialName)

/** The bar. A label and a selected state per item, because five unlabelled squares are not a map. */
@Composable
fun AppTabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier.testTag(TestTags.TAB_BAR)) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                // Null, because the label below already says it: a description here would make TalkBack
                // read "Workouts, Workouts, tab".
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.testTag),
            )
        }
    }
}

/**
 * Switches tabs (ROADMAP N34).
 *
 * `popUpTo(start) { saveState = true }` with `restoreState` is what gives each tab its own back stack:
 * leaving History and coming back finds it where it was, and back from a tab root lands on Workouts
 * rather than walking through the tabs in the order they were visited.
 */
fun NavHostController.switchTab(tab: AppTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
