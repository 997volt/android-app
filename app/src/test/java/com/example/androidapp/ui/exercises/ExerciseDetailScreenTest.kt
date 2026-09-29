package com.example.androidapp.ui.exercises

import java.io.IOException
import com.example.androidapp.domain.DataError
import com.example.androidapp.R
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.ui.components.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The detail screen's edit mode (ROADMAP N2, N5).
 *
 * The stateless composable is driven with a fixed state, so these are about the
 * wiring a unit test cannot see: which exercises offer Edit, and whether the form
 * reports what was chosen.
 *
 * Pinned to a phone-sized window: the form is taller than Robolectric's default
 * display, and a control pushed off-screen is a test artifact rather than a
 * behaviour change.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ExerciseDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(
        state: ExerciseDetailUiState,
        onEdit: () -> Unit = {},
        onCancelEdit: () -> Unit = {},
        onSave: (ExerciseEdit) -> Unit = {},
    ) {
        composeTestRule.setContent {
            ExerciseDetailScreen(
                state = state,
                onBack = {},
                onEdit = onEdit,
                onCancelEdit = onCancelEdit,
                onSave = onSave,
            )
        }
    }

    private fun customState(isEditing: Boolean) = ExerciseDetailUiState(
        isLoading = false,
        isEditing = isEditing,
        exercise = custom,
    )

    @Test
    fun aSeededExercise_isAlsoEditable() {
        // N5 reversed N2's custom-only rule: a seeded exercise must be editable
        // too, and that is safe because the seeder never updates an existing row.
        var edit = false
        show(ExerciseDetailUiState(isLoading = false, exercise = seeded), onEdit = { edit = true })

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT).performClick()

        assertTrue("the edit action should be wired through", edit)
    }

    @Test
    fun aCustomExercise_offersEdit() {
        var edit = false
        show(customState(isEditing = false), onEdit = { edit = true })

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT).performClick()

        assertTrue("the edit action should be wired through", edit)
    }

    @Test
    fun theEditForm_isPrefilledWithTheCurrentName() {
        show(customState(isEditing = true))

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_NAME).assertTextContains("Sled Push")
    }

    @Test
    fun theEditForm_isPrefilledWithTheExercisesRestAndCue() {
        show(
            ExerciseDetailUiState(
                isLoading = false,
                isEditing = true,
                exercise = seeded.copy(restSeconds = 180, techniqueNote = "Brace, sit back"),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_REST).assertTextContains("180")
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_CUE).assertTextContains("Brace, sit back")
    }

    @Test
    fun saving_reportsTheEditedName() {
        var saved: ExerciseEdit? = null
        show(customState(isEditing = true), onSave = { saved = it })

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_NAME).performTextClearance()
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_NAME).performTextInput("Sled Push Heavy")
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_SAVE).performClick()

        assertEquals("Sled Push Heavy", saved?.name)
        assertEquals(MuscleGroup.OTHER, saved?.primaryMuscle)
    }

    @Test
    fun saving_reportsTheRestAndCue() {
        var saved: ExerciseEdit? = null
        show(ExerciseDetailUiState(isLoading = false, isEditing = true, exercise = seeded), onSave = { saved = it })

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_REST).performTextInput("180")
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_CUE).performTextInput("Brace, sit back")
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_SAVE).performClick()

        assertEquals(180, saved?.restSeconds)
        assertEquals("Brace, sit back", saved?.techniqueNote)
    }

    @Test
    fun anUnparseableRest_disablesSave_ratherThanGuessingASecondsValue() {
        show(ExerciseDetailUiState(isLoading = false, isEditing = true, exercise = seeded))

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_REST).performTextInput("soon")

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_SAVE).assertIsNotEnabled()
    }

    @Test
    fun choosingAMuscle_reportsItOnSave() {
        var saved: ExerciseEdit? = null
        show(customState(isEditing = true), onSave = { saved = it })

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_MUSCLE).performClick()
        composeTestRule.onNodeWithText("Chest").performClick()
        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_SAVE).performClick()

        assertEquals(MuscleGroup.CHEST, saved?.primaryMuscle)
    }

    @Test
    fun clearingTheName_disablesSave() {
        show(customState(isEditing = true))

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_NAME).performTextClearance()

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_SAVE).assertIsNotEnabled()
    }

    @Test
    fun cancel_reportsThatEditingStopped() {
        var cancelled = false
        show(customState(isEditing = true), onCancelEdit = { cancelled = true })

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT_CANCEL).performClick()

        assertTrue("cancel should leave the form", cancelled)
    }

    @Test
    fun theReadOnlyView_showsEachAttributeOnItsOwnRow() {
        show(ExerciseDetailUiState(isLoading = false, exercise = seeded))

        composeTestRule.onNodeWithText("Primary muscle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quads").assertIsDisplayed()
        composeTestRule.onNodeWithText("Barbell").assertIsDisplayed()
    }

    @Test
    fun anUnsetRest_readsAsTheAppDefault_ratherThanNothing() {
        // N5: unset means the 90 s default will actually run, so saying so beats a
        // bare dash the user has to interpret.
        show(ExerciseDetailUiState(isLoading = false, exercise = seeded.copy(restSeconds = null)))

        composeTestRule.onNodeWithText("Default (1:30)").assertIsDisplayed()
    }

    @Test
    fun anExercisesOwnRest_isShownInItsPlace() {
        show(ExerciseDetailUiState(isLoading = false, exercise = seeded.copy(restSeconds = 180)))

        composeTestRule.onNodeWithText("3:00").assertIsDisplayed()
    }

    @Test
    fun anUnsetCue_readsAsNone() {
        show(ExerciseDetailUiState(isLoading = false, exercise = seeded.copy(techniqueNote = null)))

        composeTestRule.onNodeWithText("Technique cue").assertIsDisplayed()
        composeTestRule.onNodeWithText("None").assertIsDisplayed()
    }

    private companion object {
        val seeded = Exercise(
            id = "back-squat",
            name = "Back Squat",
            primaryMuscle = MuscleGroup.QUADS,
            secondaryMuscles = listOf(MuscleGroup.GLUTES),
            equipment = Equipment.BARBELL,
            movementPattern = MovementPattern.SQUAT,
        )

        val custom = Exercise(
            id = "custom-1",
            name = "Sled Push",
            primaryMuscle = MuscleGroup.OTHER,
            equipment = Equipment.OTHER,
            movementPattern = MovementPattern.OTHER,
            isCustom = true,
        )
    }

    @Test
    fun aFailedRead_isShownAsAFailure_notAsAMissingExercise() {
        // ROADMAP B4 and the distinction that matters: "we could not read it" and
        // "it is not there" are different sentences, and the state says which.
        show(ExerciseDetailUiState(isLoading = false, error = DataError.Storage(IOException("locked"))))

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_READ_ERROR).assertIsDisplayed()
        composeTestRule.onNodeWithText(notFoundMessage()).assertDoesNotExist()
    }

    /** The "no such exercise" line, read from resources so wording can change. */
    private fun notFoundMessage(): String =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.exercise_detail_not_found)
}
