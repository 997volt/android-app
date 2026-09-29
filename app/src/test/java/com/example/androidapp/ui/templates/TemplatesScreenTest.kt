package com.example.androidapp.ui.templates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.ui.components.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The template list (ROADMAP N3).
 *
 * The two actions on a row are easy to transpose and impossible to notice by eye —
 * tapping the row edits the plan, tapping Start begins the workout — so both are
 * asserted by the tag that shows which one fired.
 */
@RunWith(AndroidJUnit4::class)
class TemplatesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setScreen(
        state: TemplatesUiState = twoTemplates,
        onCreate: (String) -> Unit = {},
        onOpen: (String) -> Unit = {},
        onStart: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            TemplatesScreen(
                state = state,
                onCreateTemplate = onCreate,
                onOpenTemplate = onOpen,
                onStartTemplate = onStart,
                onBack = {},
            )
        }
    }

    @Test
    fun eachTemplate_showsItsNameAndExerciseCount() {
        setScreen()

        composeTestRule.onNodeWithText("Push day").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 exercises").assertIsDisplayed()
        composeTestRule.onNodeWithText("Legs").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 exercise").assertIsDisplayed()
    }

    @Test
    fun anEmptyList_explainsWhatATemplateIs() {
        setScreen(TemplatesUiState(isLoading = false, templates = emptyList()))

        composeTestRule.onNodeWithTag(TestTags.TEMPLATES_EMPTY).assertIsDisplayed()
    }

    @Test
    fun tappingARow_editsThatTemplate() {
        var opened: String? = null
        setScreen(onOpen = { opened = it })

        composeTestRule.onNodeWithTag(TestTags.templateRow("t1")).performClick()

        assertEquals("t1", opened)
    }

    @Test
    fun theStartButton_startsThatTemplate_ratherThanEditingIt() {
        var started: String? = null
        var opened: String? = null
        setScreen(onStart = { started = it }, onOpen = { opened = it })

        composeTestRule.onNodeWithTag(TestTags.templateStart("t2")).performClick()

        assertEquals("t2", started)
        assertEquals("Start must not open the editor", null, opened)
    }

    @Test
    fun theNewTemplateAction_asksForATemplate() {
        var created: String? = null
        setScreen(onCreate = { created = it })

        composeTestRule.onNodeWithTag(TestTags.TEMPLATES_NEW).performClick()

        assertTrue("the button must create, not open", created != null)
    }

    private companion object {
        val twoTemplates = TemplatesUiState(
            isLoading = false,
            templates = listOf(
                WorkoutTemplate(id = "t1", name = "Push day", exerciseCount = 5),
                WorkoutTemplate(id = "t2", name = "Legs", exerciseCount = 1),
            ),
        )
    }
}
