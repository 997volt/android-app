package com.example.androidapp.ui.exercises

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

/**
 * The detail screen's edit mode (ROADMAP N2).
 *
 * The stateless composable is driven with a fixed state, so these are about the
 * wiring a unit test cannot see: which exercises offer Edit, and whether the form
 * reports what was chosen.
 */
@RunWith(AndroidJUnit4::class)
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
    fun aSeededExercise_offersNoEditAction() {
        show(ExerciseDetailUiState(isLoading = false, exercise = seeded))

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_EDIT).assertDoesNotExist()
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
}
