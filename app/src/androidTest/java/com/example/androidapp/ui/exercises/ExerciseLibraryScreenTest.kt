package com.example.androidapp.ui.exercises

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Instrumented UI tests for the library screen.
 *
 * The screen under test is the *stateless* composable, so there is no Hilt
 * container and no repository involved — the state is handed in directly.
 * Testing the stateful `ExerciseLibraryRoute` would need `hilt-android-testing`
 * and a custom test runner; that belongs with the ViewModel-wiring tests later.
 *
 *   ./gradlew connectedDebugAndroidTest
 */
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import com.example.androidapp.ui.workout.WorkoutClock

@RunWith(AndroidJUnit4::class)
class ExerciseLibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val items = listOf(
        ExerciseListItem(id = "back-squat", name = "Back Squat", muscleLabel = "Quads", equipmentLabel = "Barbell"),
        ExerciseListItem(id = "hammer-curl", name = "Hammer Curl", muscleLabel = "Biceps", equipmentLabel = "Dumbbell"),
    )

    @Test
    fun withNothingRunning_theButtonOffersToStart() {
        var started = false
        setScreen(
            ExerciseLibraryUiState(isLoading = false, items = items),
            onStartWorkout = { started = true },
        )

        // useUnmergedTree: the FAB aggregates its slots, so the label lives on a
        // child node rather than on the merged button.
        composeTestRule.onNodeWithText("Start workout", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        assert(started) { "the button did not reach its callback" }
    }

    @Test
    fun withAWorkoutRunning_theButtonOffersToResume_itWithTheElapsedTime() {
        // Before P1.16 this screen said "Start workout" while a workout was open in
        // the database, which quietly undid the crash recovery on the ordinary
        // back-out path.
        setScreen(
            ExerciseLibraryUiState(
                isLoading = false,
                items = items,
                activeWorkout = ActiveWorkoutInfo(
                    startedAt = Instant.parse("2026-09-28T08:00:00Z"),
                    exerciseCount = 3,
                ),
            ),
            onStartWorkout = {},
        )

        composeTestRule.onNodeWithText("Resume workout", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("3 exercises", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        // The offer to start must be gone, not merely joined by a second button.
        composeTestRule.onNodeWithText("Start workout").assertDoesNotExist()
    }

    private fun setScreen(
        state: ExerciseLibraryUiState,
        onQueryChange: (String) -> Unit = {},
        onExerciseClick: (String) -> Unit = {},
        onStartWorkout: (() -> Unit)? = null,
    ) {
        composeTestRule.setContent {
            ExerciseLibraryScreen(
                state = state,
                clock = remember { mutableStateOf(WorkoutClock()) },
                title = "Exercise library",
                onQueryChange = onQueryChange,
                onExerciseClick = onExerciseClick,
                onStartWorkout = onStartWorkout,
            )
        }
    }

    @Test
    fun rendersTitleAndRows() {
        setScreen(ExerciseLibraryUiState(isLoading = false, items = items))

        composeTestRule.onNodeWithText("Exercise library").assertIsDisplayed()
        composeTestRule.onNodeWithText("Back Squat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quads · Barbell").assertIsDisplayed()
    }

    @Test
    fun typingInSearchField_reportsTheQuery() {
        var typed: String? = null
        setScreen(ExerciseLibraryUiState(isLoading = false, items = items), onQueryChange = { typed = it })

        composeTestRule.onNode(hasSetTextAction()).performTextInput("squat")

        assertEquals("squat", typed)
    }

    @Test
    fun tappingRow_reportsThatExerciseId() {
        var clicked: String? = null
        setScreen(ExerciseLibraryUiState(isLoading = false, items = items), onExerciseClick = { clicked = it })

        composeTestRule.onNodeWithText("Hammer Curl").performClick()

        assertEquals("hammer-curl", clicked)
    }

    @Test
    fun emptyResult_showsMessageInsteadOfSpinner() {
        setScreen(ExerciseLibraryUiState(query = "zzz", isLoading = false, items = emptyList()))

        composeTestRule.onNodeWithText("No exercises match", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading exercises…").assertDoesNotExist()
    }
}
