package com.example.androidapp.ui.workout

import com.example.androidapp.domain.model.PersonalRecordMoment
import com.example.androidapp.domain.model.ProgressionReason
import com.example.androidapp.domain.model.SetType
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.ui.components.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /**
     * The screen's events, grouped: one flat parameter per callback stops reading as
     * a call site long before it stops compiling.
     */
    private data class Actions(
        val onFinishExercise: (String, Int?, Int?, String?) -> Unit = { _, _, _, _ -> },
        val onReopenExercise: (String) -> Unit = {},
        val onRemoveExercise: (String) -> Unit = {},
        val onRateExercise: (String, Int?, Int?, String?) -> Unit = { _, _, _, _ -> },
        val onFinish: (String?) -> Unit = {},
        val onLogSet: (String, SetType) -> Unit = { _, _ -> },
    )

    private fun setScreen(
        state: ActiveWorkoutUiState,
        actions: Actions = Actions(),
        personalRecord: PersonalRecordMoment? = null,
    ) {
        composeTestRule.setContent {
            ActiveWorkoutScreen(
                state = state,
                clock = remember { mutableStateOf(WorkoutClock()) },
                onAddExercise = {},
                onLogSet = actions.onLogSet,
                personalRecord = personalRecord,
                onUpdateSet = { _, _, _, _, _, _, _ -> },
                onRemoveExercise = actions.onRemoveExercise,
                onRateExercise = actions.onRateExercise,
                onFinish = actions.onFinish,
                onFinishExercise = actions.onFinishExercise,
                onReopenExercise = actions.onReopenExercise,
                onDeleteSet = {},
                onUndoDelete = {},
                onDismissUndo = {},
                onSkipRest = {},
                onAdjustRest = {},
                onSaveReadinessNote = {},
                onDismissReadinessPrompt = {},
                onUndoFinishExercise = {},
                onDismissFinishUndo = {},
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

    /** What the screen reports when an exercise is finished (N7, N8, N9). */
    private data class FinishCall(
        val id: String,
        val muscleFeel: Int?,
        val jointPain: Int?,
        val jointPainNote: String?,
    )

    @Test
    fun tappingDone_asksHowItFelt_beforeFinishing() {
        var finished: FinishCall? = null
        setScreen(
            state(isFinished = false),
            actions = Actions(
                onFinishExercise = { id, feel, pain, note ->
                    finished = FinishCall(id, feel, pain, note)
                },
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_DONE).performClick()

        // N8: the prompt comes up first, and nothing is written until it is answered.
        composeTestRule.onNodeWithTag(TestTags.RATING_MUSCLE_FIELD).assertExists()
        assertEquals(null, finished)

        composeTestRule.onNodeWithTag(TestTags.RATING_SAVE).performClick()

        assertEquals("se1", finished?.id)
    }

    @Test
    fun skippingTheRatingPrompt_finishesWithoutRatings() {
        var finished: FinishCall? = null
        setScreen(
            state(isFinished = false),
            actions = Actions(
                onFinishExercise = { id, feel, pain, note ->
                    finished = FinishCall(id, feel, pain, note)
                },
            ),
        )
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_DONE).performClick()

        composeTestRule.onNodeWithTag(TestTags.RATING_DISMISS).performClick()

        assertEquals(FinishCall("se1", null, null, null), finished)
    }

    @Test
    fun savingTheRatings_passesBothThrough() {
        var finished: FinishCall? = null
        setScreen(
            state(isFinished = false),
            actions = Actions(
                onFinishExercise = { id, feel, pain, note ->
                    finished = FinishCall(id, feel, pain, note)
                },
            ),
        )
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_DONE).performClick()
        composeTestRule.onNodeWithTag(TestTags.RATING_MUSCLE_FIELD).performTextInput("8")
        composeTestRule.onNodeWithTag(TestTags.RATING_JOINT_FIELD).performTextInput("2")
        // N9's location rides with the ratings the prompt collects.
        composeTestRule.onNodeWithTag(TestTags.RATING_JOINT_NOTE_FIELD).performTextInput("left knee")

        composeTestRule.onNodeWithTag(TestTags.RATING_SAVE).performClick()

        assertEquals(FinishCall("se1", 8, 2, "left knee"), finished)
    }

    @Test
    fun anExerciseCanBeRated_beforeItIsDone() {
        // ROADMAP N10: the ratings used to be reachable only through the Done
        // prompt, which means recording how a set felt from memory, afterwards.
        var rated: Rounding? = null
        setScreen(
            state(isFinished = false),
            Actions(
                onRateExercise = { id, feel, pain, note ->
                    rated = Rounding(id, feel, pain, note)
                },
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_RATING_ROW, useUnmergedTree = true)
            .performClick()
        composeTestRule.onNodeWithTag(TestTags.RATING_MUSCLE_FIELD).performTextInput("7")
        composeTestRule.onNodeWithTag(TestTags.RATING_JOINT_FIELD).performTextInput("3")
        composeTestRule.onNodeWithTag(TestTags.RATING_SAVE).performClick()

        assertEquals(Rounding("se1", 7, 3, null), rated)
    }

    @Test
    fun anExerciseAlreadyRated_showsWhatItSaid() {
        setScreen(state(isFinished = false).copy(exercises = listOf(finishedRow(rated = true))))

        // N9's location rides in the same summary.
        composeTestRule.onNodeWithText("Muscle feel 8 · Joint pain 2 · left knee").assertExists()
    }

    @Test
    fun aDoneExercise_canStillBeRated() {
        // "At any time" includes after Done: the row is not tied to the prompt.
        var rated: Rounding? = null
        setScreen(
            state(isFinished = false).copy(exercises = listOf(finishedRow(rated = false))),
            Actions(
                onRateExercise = { id, feel, pain, note ->
                    rated = Rounding(id, feel, pain, note)
                },
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_RATING_ROW, useUnmergedTree = true)
            .performClick()
        composeTestRule.onNodeWithTag(TestTags.RATING_MUSCLE_FIELD).performTextInput("5")
        composeTestRule.onNodeWithTag(TestTags.RATING_SAVE).performClick()

        assertEquals(Rounding("se1", 5, null, null), rated)
    }

    /** What the screen reports when a rating is saved outside the Done prompt. */
    private data class Rounding(
        val id: String,
        val muscleFeel: Int?,
        val jointPain: Int?,
        val jointPainNote: String?,
    )

    private fun finishedRow(rated: Boolean) = SessionExerciseRow(
        id = "se1",
        exerciseId = "back-squat",
        name = "Back Squat",
        subtitle = "Quads · Barbell",
        isFinished = true,
        muscleFeel = if (rated) 8 else null,
        jointPain = if (rated) 2 else null,
        jointPainNote = if (rated) "left knee" else null,
        sets = listOf(SetRow(id = "set1", number = 1, reps = 5, weightGrams = 100_000)),
        suggestion = SetSuggestion(reps = 5, weightGrams = 100_000),
    )

    @Test
    fun tappingReopen_reportsThatExercise() {
        var reopened: String? = null
        setScreen(state(isFinished = true), actions = Actions(onReopenExercise = { reopened = it }))

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_REOPEN).performClick()

        assertEquals("se1", reopened)
    }

    @Test
    fun finishing_asksForAComment_andPassesItOn() {
        // ROADMAP N11: the moment of finishing is when the reason is remembered.
        var finished: String? = null
        setScreen(state(isFinished = false), actions = Actions(onFinish = { note -> finished = note }))

        composeTestRule.onNodeWithTag(TestTags.ACTIVE_WORKOUT_FINISH).performClick()
        composeTestRule.onNodeWithTag(TestTags.WORKOUT_NOTE).performTextInput("Legs felt heavy")
        composeTestRule.onNodeWithTag(TestTags.WORKOUT_NOTE_SAVE).performClick()

        assertEquals("Legs felt heavy", finished)
    }

    @Test
    fun skippingTheComment_stillFinishesTheWorkout() {
        // A prompt, not a gate: the user asked to finish.
        var finished: String? = null
        var called = false
        setScreen(state(isFinished = false), actions = Actions(onFinish = { note -> finished = note; called = true }))

        composeTestRule.onNodeWithTag(TestTags.ACTIVE_WORKOUT_FINISH).performClick()
        composeTestRule.onNodeWithTag(TestTags.WORKOUT_NOTE_SKIP).performClick()

        assertTrue(called)
        assertNull(finished)
    }

    @Test
    fun removingAnExercise_asksFirst_andWritesNothingUntilConfirmed() {
        // ROADMAP B2: the removal has no undo, so the dialog is the guard.
        var removed: String? = null
        setScreen(state(isFinished = false), actions = Actions(onRemoveExercise = { removed = it }))

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_REMOVE).performClick()

        assert(removed == null) { "the tap removed the exercise before asking" }
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_REMOVE_CONFIRM).assertExists()

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_REMOVE_CONFIRM).performClick()

        assert(removed == "se1") { "confirming did not remove the exercise, got $removed" }
    }

    @Test
    fun dismissingTheRemovalDialog_leavesTheExerciseInPlace() {
        var removed: String? = null
        setScreen(state(isFinished = false), actions = Actions(onRemoveExercise = { removed = it }))

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_REMOVE).performClick()
        // The dialog's other button: backing out must not remove anything.
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_REMOVE_CANCEL).performClick()

        assert(removed == null) { "cancelling removed the exercise anyway" }
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_REMOVE_CONFIRM).assertDoesNotExist()
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

    @Test
    fun choosingARole_thenLogging_writesThatRole() {
        // ROADMAP N19: the picker used to be behind the editor, so three warm-ups cost
        // three log-then-edit round trips. One tap each way now.
        val logged = mutableListOf<Pair<String, SetType>>()
        setScreen(
            state = state(isFinished = false),
            actions = Actions(onLogSet = { id, role -> logged += id to role }),
        )

        composeTestRule.onNodeWithTag(TestTags.exercisePendingRole("se1")).performClick()
        composeTestRule.onNodeWithTag(TestTags.exercisePendingRole("se1", "WARMUP")).performClick()
        composeTestRule.onNodeWithTag(TestTags.SET_LOG).performClick()

        assertEquals(listOf("se1" to SetType.WARMUP), logged)
    }

    @Test
    fun theChosenRole_isShownOnTheControl() {
        // The control has to say what the next tap will write, or arming it is a guess.
        setScreen(state = state(isFinished = false))

        composeTestRule.onNodeWithText("Role: Working").assertExists()
        composeTestRule.onNodeWithTag(TestTags.exercisePendingRole("se1")).performClick()
        composeTestRule.onNodeWithTag(TestTags.exercisePendingRole("se1", "WARMUP")).performClick()

        composeTestRule.onNodeWithText("Role: Warm-up").assertExists()
    }

    @Test
    fun aProgressedSuggestion_saysWhy() {
        // ROADMAP N22: a number the app chose is an instruction unless it explains itself,
        // and this sentence is the difference between a smart app and a surprising one.
        val base = state(isFinished = false)
        setScreen(
            state = base.copy(
                exercises = base.exercises.map {
                    it.copy(
                        suggestion = SetSuggestion(
                            reps = 9,
                            weightGrams = 20_000L,
                            reason = ProgressionReason.MORE_REPS,
                        ),
                    )
                },
            ),
        )

        composeTestRule.onNodeWithText("One more rep than last time").assertExists()
    }

    @Test
    fun aSuggestionThatIsJustThePlan_hasNothingToExplain() {
        // Null is not "no reason" — it is "nothing to explain", and a line here would train
        // the user to ignore the line that matters.
        setScreen(state = state(isFinished = false))

        composeTestRule.onNodeWithTag(TestTags.SUGGESTION_REASON).assertDoesNotExist()
    }

    @Test
    fun aRecord_isShownAboveTheWork() {
        setScreen(
            state = state(isFinished = false),
            personalRecord = PersonalRecordMoment(
                exerciseName = "Back Squat",
                reps = 5,
                weightGrams = 100_000L,
                previousBestGrams = 95_000L,
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.PERSONAL_RECORD).assertExists()
        composeTestRule.onNodeWithText("Personal record: 100 kg × 5").assertExists()
        composeTestRule.onNodeWithText("Back Squat — your best before was 95 kg").assertExists()
    }

    @Test
    fun theFirstTimeAtARepCount_saysThatInsteadOfClaimingABest() {
        // There was no bar to clear, and claiming one would be a lie the data does not support.
        setScreen(
            state = state(isFinished = false),
            personalRecord = PersonalRecordMoment(
                exerciseName = "Back Squat",
                reps = 3,
                weightGrams = 60_000L,
                previousBestGrams = null,
            ),
        )

        composeTestRule.onNodeWithText("Back Squat — the first time at this rep count").assertExists()
    }

    @Test
    fun withoutARecord_thereIsNoBanner() {
        setScreen(state = state(isFinished = false))

        composeTestRule.onNodeWithTag(TestTags.PERSONAL_RECORD).assertDoesNotExist()
    }

    @Test
    fun editingALoggedSet_opensOnWhatWasStored() {
        // ROADMAP B14: the editor was never told the role or the assistance, so it opened on a
        // plain working set with no help — and saving wrote that over the stored row. A one-rep
        // correction silently destroyed both, which is what this assertion exists to prevent.
        val base = state(isFinished = false)
        setScreen(
            state = base.copy(
                exercises = base.exercises.map { row ->
                    row.copy(
                        sets = listOf(
                            SetRow(
                                id = "set1",
                                number = 1,
                                reps = 8,
                                weightGrams = 0L,
                                setType = SetType.WARMUP,
                                assistanceGrams = 20_000L,
                            ),
                        ),
                    )
                },
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.SET_ROW).performClick()

        composeTestRule.onNodeWithText("Role: Warm-up").assertExists()
        composeTestRule.onNodeWithText("-20").assertExists()
    }
}
