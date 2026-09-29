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

    private fun setScreen(
        state: WorkoutsHomeUiState,
        onStartWorkout: () -> Unit = {},
        onOpenWorkout: (String) -> Unit = {},
        onOpenHistory: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AndroidAppTheme {
                WorkoutsHomeScreen(
                    state = state,
                    clock = remember { mutableStateOf(WorkoutClock()) },
                    onStartWorkout = onStartWorkout,
                    onOpenWorkout = onOpenWorkout,
                    onOpenHistory = onOpenHistory,
                    onOpenLibrary = {},
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
            onOpenHistory = { openedHistory = true },
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
