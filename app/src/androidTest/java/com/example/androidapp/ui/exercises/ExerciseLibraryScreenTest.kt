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
@RunWith(AndroidJUnit4::class)
class ExerciseLibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val items = listOf(
        ExerciseListItem(id = "back-squat", name = "Back Squat", muscleLabel = "Quads", equipmentLabel = "Barbell"),
        ExerciseListItem(id = "hammer-curl", name = "Hammer Curl", muscleLabel = "Biceps", equipmentLabel = "Dumbbell"),
    )

    private fun setScreen(
        state: ExerciseLibraryUiState,
        onQueryChange: (String) -> Unit = {},
        onExerciseClick: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            ExerciseLibraryScreen(
                state = state,
                onQueryChange = onQueryChange,
                onExerciseClick = onExerciseClick,
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
