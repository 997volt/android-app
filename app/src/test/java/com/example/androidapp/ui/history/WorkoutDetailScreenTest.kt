package com.example.androidapp.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.WorkoutSession
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import com.example.androidapp.ui.components.TestTags
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the history detail screen (ROADMAP P1.6, P1.7).
 *
 * The stateless composable, so no Hilt container and no repository: the state is
 * handed in and the callbacks are recorded. This covers the wiring the data-layer
 * tests cannot — that the editor actually opens and that deleting asks first.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val state = WorkoutDetailUiState(
        isLoading = false,
        session = WorkoutSession(
            id = "a",
            startedAt = Instant.parse("2026-09-28T07:00:00Z"),
            finishedAt = Instant.parse("2026-09-28T08:00:00Z"),
        ),
        exercises = listOf(
            HistoryExercise(
                id = "se1",
                name = "Back Squat",
                sets = listOf(HistorySet(id = "set1", reps = 5, weightGrams = 100_000)),
            ),
        ),
    )

    @Test
    fun aSetRowSaysWhatTappingItDoes() {
        // P1.17: the row is editable, and a screen-reader user should be told that
        // rather than left to guess. The tag is what makes this assertable without
        // matching on a translated string.
        setScreen()

                // useUnmergedTree: a tag on a child of a merging parent is not visible in
        // the merged tree — the same trap the FAB hit earlier in this file.
        composeTestRule.onNodeWithTag(TestTags.SET_ROW, useUnmergedTree = true)
            .assert(hasClickLabel())
    }

    private fun setScreen(
        uiState: WorkoutDetailUiState = state,
        onUpdateSet: (String, Int, Long, Int?, String?) -> Unit = { _, _, _, _, _ -> },
        onDeleteSet: (String) -> Unit = {},
        onRateExercise: (String, Int?, Int?) -> Unit = { _, _, _ -> },
        onDeleteWorkout: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            WorkoutDetailScreen(
                state = uiState,
                onUpdateSet = onUpdateSet,
                onDeleteSet = onDeleteSet,
                onRateExercise = onRateExercise,
                onDeleteWorkout = onDeleteWorkout,
                onBack = {},
            )
        }
    }

    @Test
    fun rendersTheExerciseAndItsSet() {
        setScreen()

        composeTestRule.onNodeWithText("Back Squat").assertIsDisplayed()
        composeTestRule.onNodeWithText("100 kg × 5").assertIsDisplayed()
    }

    @Test
    fun theReadinessNote_ridesThroughHistory() {
        // N4: the note is captured while training, so history has to show it.
        setScreen(
            uiState = state.copy(
                session = state.session?.copy(readinessNote = "Slept badly, legs heavy"),
            ),
        )

        composeTestRule.onNodeWithText("Readiness").assertIsDisplayed()
        composeTestRule.onNodeWithText("Slept badly, legs heavy").assertIsDisplayed()
    }

    @Test
    fun withoutAReadinessNote_thereIsNoEmptyBlock() {
        setScreen()

        composeTestRule.onNodeWithText("Readiness").assertDoesNotExist()
    }

    @Test
    fun aSetsRpeAndComment_areShownInHistory() {
        // N6: the workout row carries only a marker, so this is where the comment's
        // text actually lives.
        setScreen(
            uiState = state.copy(
                exercises = listOf(
                    HistoryExercise(
                        id = "se1",
                        name = "Back Squat",
                        sets = listOf(
                            HistorySet(
                                id = "set1",
                                reps = 5,
                                weightGrams = 100_000,
                                rpe = 8,
                                note = "Felt heavy",
                            ),
                        ),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("RPE 8").assertIsDisplayed()
        composeTestRule.onNodeWithText("Felt heavy").assertIsDisplayed()
    }

    @Test
    fun anExercisesFeelRatings_areShownInHistory() {
        // N8: captured when the exercise was marked done, and editable from here.
        setScreen(
            uiState = state.copy(
                exercises = listOf(
                    HistoryExercise(
                        id = "se1",
                        name = "Back Squat",
                        sets = emptyList(),
                        muscleFeel = 8,
                        jointPain = 2,
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("Muscle feel 8 · Joint pain 2").assertIsDisplayed()
    }

    @Test
    fun tappingTheFeelRow_opensTheRatingEditor() {
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_RATING_ROW, useUnmergedTree = true)
            .performClick()

        composeTestRule.onNodeWithTag(TestTags.RATING_MUSCLE_FIELD).assertExists()
    }

    @Test
    fun tappingASet_opensTheEditor() {
        // The whole point of P1.7: a mis-tap must be correctable from history.
        setScreen()

        composeTestRule.onNodeWithText("100 kg × 5").performClick()

        composeTestRule.onNodeWithText("Edit set").assertIsDisplayed()
    }

    @Test
    fun deletingAWorkout_asksFirst_andOnlyThenReports() {
        var deleted = false
        setScreen(onDeleteWorkout = { deleted = true })

        composeTestRule.onNodeWithContentDescription("Delete workout").performClick()

        // Still nothing deleted: a confirmation has to stand between the tap and
        // the deletion, because there is no undo in the app.
        composeTestRule.onNodeWithText("Delete this workout?").assertIsDisplayed()
        assert(!deleted) { "the workout was deleted before it was confirmed" }
    }
}

/** True when the node's click action carries an accessibility label. */
private fun hasClickLabel(): SemanticsMatcher = SemanticsMatcher("has a click label") { node ->
    node.config.getOrNull(SemanticsActions.OnClick)?.label != null
}
