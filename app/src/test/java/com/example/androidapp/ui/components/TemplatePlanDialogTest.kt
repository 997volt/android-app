package com.example.androidapp.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.TemplateSet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The plan dialog's summary line (ROADMAP B6).
 *
 * A planned set stores its RPE in half-points, and this dialog was the one place that
 * passed the count straight to the marker: a plan saying 9.5 rendered as **"RPE 19"** —
 * the exact confusion the halves representation exists to prevent. The formatting now
 * lives in one shared `rpeMarker`, and this is the test that would have caught it.
 */
@RunWith(AndroidJUnit4::class)
class TemplatePlanDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(set: TemplateSet) {
        composeTestRule.setContent {
            TemplatePlanDialog(
                exerciseName = "Back Squat",
                sets = listOf(set),
                onAddSet = {},
                onEditSet = {},
                onDeleteSet = {},
                onDuplicate = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun aHalfStepTargetRpe_readsAsALifterWritesIt() {
        show(plannedSet(targetRpeHalves = 19))

        composeTestRule.onNodeWithText("RPE 9.5", substring = true).assertIsDisplayed()
    }

    @Test
    fun aWholeTargetRpe_hasNoTrailingZero() {
        show(plannedSet(targetRpeHalves = 16))

        composeTestRule.onNodeWithText("RPE 8", substring = true).assertIsDisplayed()
    }

    private fun plannedSet(targetRpeHalves: Int?) = TemplateSet(
        id = "ts1",
        templateExerciseId = "te1",
        setIndex = 0,
        role = SetType.TOP_SET,
        targetWeightGrams = 140_000L,
        targetRepsMax = 2,
        targetRpeHalves = targetRpeHalves,
    )
}
