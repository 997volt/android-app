package com.example.androidapp.ui.workout

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.ui.components.TestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The active workout screen (closing the testing gap the roadmap names).
 *
 * The stateless composable with a fixed state, so this covers what the ViewModel
 * tests cannot see: that "Done" and "Reopen" are the right way round, that a done
 * exercise really loses its Log set button, and that its sets stop being tappable.
 */
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setScreen(
        state: ActiveWorkoutUiState,
        onFinishExercise: (String) -> Unit = {},
        onReopenExercise: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            ActiveWorkoutScreen(
                state = state,
                clock = remember { mutableStateOf(WorkoutClock()) },
                onAddExercise = {},
                onLogSet = {},
                onUpdateSet = { _, _, _, _, _ -> },
                onRemoveExercise = {},
                onDeleteSet = {},
                onUndoDelete = {},
                onDismissUndo = {},
                onSkipRest = {},
                onAdjustRest = {},
                onSaveReadinessNote = {},
                onDismissReadinessPrompt = {},
                onFinishExercise = onFinishExercise,
                onUndoFinishExercise = {},
                onDismissFinishUndo = {},
                onReopenExercise = onReopenExercise,
                onFinish = {},
                onDiscard = {},
                onBack = {},
            )
        }
    }

    @Test
    fun anOpenExercise_offersDone_andItsLogSetButton() {
        setScreen(state(isFinished = false))

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_DONE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_REOPEN).assertDoesNotExist()
        composeTestRule.onNodeWithText("Log set · 100 kg × 5").assertIsDisplayed()
        // N5's cue, on the screen it was added for.
        composeTestRule.onNodeWithText("Brace, sit back").assertIsDisplayed()
    }

    @Test
    fun aDoneExercise_hidesLogSet_andOffersReopen() {
        setScreen(state(isFinished = true))

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_REOPEN).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_DONE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_FINISHED_LABEL).assertIsDisplayed()
        // The accident N7 exists to prevent: no way to add another set.
        composeTestRule.onNodeWithText("Log set · 100 kg × 5").assertDoesNotExist()
    }

    @Test
    fun tappingDone_reportsThatExercise() {
        var finished: String? = null
        setScreen(state(isFinished = false), onFinishExercise = { finished = it })

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_DONE).performClick()

        assertEquals("se1", finished)
    }

    @Test
    fun tappingReopen_reportsThatExercise() {
        var reopened: String? = null
        setScreen(state(isFinished = true), onReopenExercise = { reopened = it })

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_REOPEN).performClick()

        assertEquals("se1", reopened)
    }

    @Test
    fun aDoneExercisesSet_cannotBeTapped() {
        setScreen(state(isFinished = true))

        // useUnmergedTree: the tag is on a child of a merging parent.
        composeTestRule.onNodeWithTag(TestTags.SET_ROW, useUnmergedTree = true)
            .assertHasNoClickAction()
    }

    @Test
    fun anOpenExercisesSet_isStillEditable() {
        setScreen(state(isFinished = false))

        composeTestRule.onNodeWithTag(TestTags.SET_ROW, useUnmergedTree = true)
            .assertHasClickAction()
    }

    private fun state(isFinished: Boolean) = ActiveWorkoutUiState(
        isLoading = false,
        sessionId = "s1",
        startedAt = "07:42",
        exercises = listOf(
            SessionExerciseRow(
                id = "se1",
                exerciseId = "back-squat",
                name = "Back Squat",
                subtitle = "Quads · Barbell",
                techniqueNote = "Brace, sit back",
                isFinished = isFinished,
                sets = listOf(SetRow(id = "set1", number = 1, reps = 5, weightGrams = 100_000)),
                suggestion = SetSuggestion(reps = 5, weightGrams = 100_000),
            ),
        ),
    )
}
