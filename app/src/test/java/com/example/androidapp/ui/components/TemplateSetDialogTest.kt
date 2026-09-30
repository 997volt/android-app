package com.example.androidapp.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.repository.TemplateSetEdit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The planned-set dialog (ROADMAP N14).
 *
 * The first test is the one that matters: the draft is held in a `remember`, not a
 * `rememberSaveable`, because a data class is not Bundle-saveable and registering one
 * throws as the dialog opens. That crash reached a device and no test could see it,
 * because every other test drove the ViewModel instead of the dialog.
 */
@RunWith(AndroidJUnit4::class)
class TemplateSetDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var saved: TemplateSetEdit? = null

    private fun show(initial: TemplateSetEdit = TemplateSetEdit(), isNew: Boolean = true) {
        composeTestRule.setContent {
            TemplateSetDialog(
                initial = initial,
                isNew = isNew,
                onDismiss = {},
                onSave = { saved = it },
            )
        }
    }

    @Test
    fun theDialogOpens_andSavesWhatWasTyped() {
        show()

        composeTestRule.onNodeWithTag(TestTags.TEMPLATE_SET_WEIGHT).performTextInput("140")
        composeTestRule.onNodeWithTag(TestTags.TEMPLATE_SET_REPS_MIN).performTextInput("1")
        composeTestRule.onNodeWithTag(TestTags.TEMPLATE_SET_REPS_MAX).performTextInput("2")
        composeTestRule.onNodeWithTag(TestTags.TEMPLATE_SET_RPE).performTextInput("9")
        composeTestRule.onNodeWithTag(TestTags.TEMPLATE_SET_NOTE).performTextInput("grind")
        composeTestRule.onNodeWithTag(TestTags.TEMPLATE_SET_SAVE).performClick()

        assertEquals(140_000L, saved?.targetWeightGrams)
        assertEquals(1, saved?.targetRepsMin)
        assertEquals(2, saved?.targetRepsMax)
        assertEquals(9, saved?.targetRpe)
        assertEquals("grind", saved?.note)
    }

    @Test
    fun everyFieldCanBeLeftEmpty() {
        // A plan may say "work up to a heavy single" and mean it (N14).
        show()

        composeTestRule.onNodeWithTag(TestTags.TEMPLATE_SET_SAVE).performClick()

        assertEquals(TemplateSetEdit(role = SetType.NORMAL), saved)
    }

    @Test
    fun theRoleCanBeChanged_fromTheDialog() {
        show()

        composeTestRule.onNodeWithTag(TestTags.TEMPLATE_SET_ROLE).performClick()
        composeTestRule.onNodeWithTag(TestTags.templateSetRole("TOP_SET")).performClick()
        composeTestRule.onNodeWithTag(TestTags.TEMPLATE_SET_SAVE).performClick()

        assertEquals(SetType.TOP_SET, saved?.role)
    }

    @Test
    fun anExistingTargets_areShownRatherThanReset() {
        show(
            initial = TemplateSetEdit(
                role = SetType.DROP,
                targetWeightGrams = 60_000L,
                targetRepsMax = 8,
            ),
            isNew = false,
        )

        composeTestRule.onNodeWithTag(TestTags.TEMPLATE_SET_SAVE).performClick()

        assertEquals(SetType.DROP, saved?.role)
        assertEquals(60_000L, saved?.targetWeightGrams)
        assertEquals(8, saved?.targetRepsMax)
    }
}
