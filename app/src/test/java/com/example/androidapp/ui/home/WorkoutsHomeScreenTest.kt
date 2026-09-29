package com.example.androidapp.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.ui.workout.WorkoutClock
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The home screen (ROADMAP N1).
 *
 * Two of these moved here from the library's tests: the start/resume button used to
 * live there, and N1 is precisely the change that moved it. Leaving them behind
 * would have tested a button that no longer exists on that screen.
 *
 * Matched by tag, so a translation cannot break them.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutsHomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * The top bar's menu actions, grouped rather than passed one by one: the
     * helper is a screen setter, and four flat callbacks plus state is already at
     * the limit of what reads as a call site.
     */
    private data class MenuActions(
        val onOpenHistory: () -> Unit = {},
        val onOpenTemplates: () -> Unit = {},
    )

    private fun setScreen(
        state: WorkoutsHomeUiState,
        onStartWorkout: () -> Unit = {},
        onStartFromTemplate: () -> Unit = {},
        onOpenWorkout: (String) -> Unit = {},
        menu: MenuActions = MenuActions(),
    ) {
        composeTestRule.setContent {
            AndroidAppTheme {
                WorkoutsHomeScreen(
                    state = state,
                    clock = remember { mutableStateOf(WorkoutClock()) },
                    onStartWorkout = onStartWorkout,
                    onStartFromTemplate = onStartFromTemplate,
                    onOpenWorkout = onOpenWorkout,
                    onOpenHistory = menu.onOpenHistory,
                    onOpenLibrary = {},
                    onOpenTemplates = menu.onOpenTemplates,
                )
            }
        }
    }

    @Test
    fun withNothingLogged_theScreenPointsAtStart() {
        var started = false
        setScreen(WorkoutsHomeUiState(isLoading = false), onStartWorkout = { started = true })

        // An empty list with no explanation tells a first-run user nothing.
        composeTestRule.onNodeWithTag(TestTags.HOME_FIRST_RUN).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.HOME_START).performClick()

        assert(started) { "the start button did not reach its callback" }
    }

    @Test
    fun withAWorkoutRunning_theButtonOffersToResume() {
        setScreen(
            WorkoutsHomeUiState(
                isLoading = false,
                activeWorkout = ActiveWorkoutInfo(
                    startedAt = Instant.parse("2026-09-29T10:00:00Z"),
                    exerciseCount = 3,
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_RESUME).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.HOME_START).assertDoesNotExist()
    }

    @Test
    fun theStartAction_offersBothWaysToBegin() {
        // N3: the start action presents the choice — empty, or from a plan set up
        // in advance. With a workout already open there is no choice to make.
        var fromTemplate = false
        setScreen(
            WorkoutsHomeUiState(isLoading = false),
            onStartFromTemplate = { fromTemplate = true },
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_START).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.HOME_START_FROM_TEMPLATE).performClick()

        assert(fromTemplate) { "Start from template did not open the template list" }
    }

    @Test
    fun withAWorkoutRunning_theTemplateChoiceIsNotOffered() {
        setScreen(
            WorkoutsHomeUiState(
                isLoading = false,
                activeWorkout = ActiveWorkoutInfo(
                    startedAt = Instant.parse("2026-09-29T10:00:00Z"),
                    exerciseCount = 3,
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_START_FROM_TEMPLATE).assertDoesNotExist()
    }

    @Test
    fun theOverflowMenu_reachesTheTemplates() {
        var opened = false
        setScreen(
            WorkoutsHomeUiState(isLoading = false),
            menu = MenuActions(onOpenTemplates = { opened = true }),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_MENU).performClick()
        composeTestRule.onNodeWithTag(TestTags.HOME_TEMPLATES).performClick()

        assert(opened) { "the menu did not reach the templates" }
    }

    @Test
    fun recentWorkouts_areListed_andOpenTheirDetail() {
        var opened: String? = null
        setScreen(
            WorkoutsHomeUiState(isLoading = false, recent = listOf(summary("session-1"))),
            onOpenWorkout = { opened = it },
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_RECENT_ROW).performClick()

        assert(opened == "session-1") { "expected the row to open its workout, got $opened" }
    }

    @Test
    fun seeAll_leadsToTheFullHistory() {
        // Home is a home-sized view; the month-grouped history is still the full one.
        var openedHistory = false
        setScreen(
            WorkoutsHomeUiState(isLoading = false, recent = listOf(summary("session-1"))),
            menu = MenuActions(onOpenHistory = { openedHistory = true }),
        )

        composeTestRule.onNodeWithTag(TestTags.HOME_SEE_ALL).performClick()

        assert(openedHistory) { "See all did not open the history" }
    }

    private fun summary(id: String) = WorkoutSummary(
        id = id,
        startedAt = Instant.parse("2026-09-29T08:00:00Z"),
        finishedAt = Instant.parse("2026-09-29T09:00:00Z"),
        exerciseCount = 3,
        setCount = 12,
        volumeGrams = 1_000_000L,
    )
}
