package com.example.androidapp.ui.programs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.ui.components.TestTags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The program list (ROADMAP P3.3).
 *
 * The two actions on a row are easy to transpose and impossible to notice by eye —
 * tapping the row edits the program, tapping Use makes it the one home follows — so both
 * are asserted by the tag that shows which one fired.
 */
@RunWith(AndroidJUnit4::class)
class ProgramsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setScreen(
        state: ProgramsUiState = twoPrograms,
        onCreate: (String) -> Unit = {},
        onOpen: (String) -> Unit = {},
        onSetActive: (String) -> Unit = {},
        onMoveProgram: (String, Int) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            ProgramsScreen(
                state = state,
                onCreateProgram = onCreate,
                onOpenProgram = onOpen,
                onBack = {},
                onSetActive = onSetActive,
                onMoveProgram = onMoveProgram,
            )
        }
    }

    @Test
    fun eachProgram_showsItsNameAndSlotCount() {
        setScreen()

        composeTestRule.onNodeWithText("Upper/Lower").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 workouts").assertIsDisplayed()
        composeTestRule.onNodeWithText("PPL").assertIsDisplayed()
        composeTestRule.onNodeWithText("6 workouts").assertIsDisplayed()
    }

    @Test
    fun anEmptyList_explainsWhatAProgramIs() {
        setScreen(ProgramsUiState(isLoading = false, programs = emptyList()))

        composeTestRule.onNodeWithTag(TestTags.Programs.EMPTY).assertIsDisplayed()
    }

    @Test
    fun tappingARow_editsThatProgram() {
        var opened: String? = null
        setScreen(onOpen = { opened = it })

        composeTestRule.onNodeWithTag(TestTags.Programs.row("p1")).performClick()

        assertThat(opened).isEqualTo("p1")
    }

    @Test
    fun theUseButton_makesThatProgramActive_ratherThanEditingIt() {
        var activated: String? = null
        var opened: String? = null
        setScreen(onSetActive = { activated = it }, onOpen = { opened = it })

        composeTestRule.onNodeWithTag(TestTags.Programs.use("p2")).performClick()

        assertThat(activated).isEqualTo("p2")
        assertThat(opened).isNull()
    }

    @Test
    fun theActiveProgram_isLabelled_ratherThanOfferingToUseItself() {
        setScreen()

        composeTestRule.onNodeWithText("In use").assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.Programs.use("p1")).assertDoesNotExist()
        // The inactive one still offers the choice.
        composeTestRule.onNodeWithTag(TestTags.Programs.use("p2")).assertIsDisplayed()
    }

    @Test
    fun theNewProgramAction_asksForAProgram() {
        var created: String? = null
        setScreen(onCreate = { created = it })

        composeTestRule.onNodeWithTag(TestTags.Programs.NEW).performClick()

        assertThat(created).isNotNull()
    }

    @Test
    fun theMoveButtons_reorderThePrograms_ratherThanEditingThem() {
        // The authored order is P3.12's whole point, so moving is asserted by the tag that fired
        // and by the row's own click not firing with it.
        var moved: Pair<String, Int>? = null
        var opened: String? = null
        setScreen(
            onMoveProgram = { id, delta -> moved = id to delta },
            onOpen = { opened = it },
        )

        composeTestRule.onNodeWithTag(TestTags.Programs.moveProgram("p2", up = true)).performClick()

        assertThat(moved).isEqualTo("p2" to -1)
        assertThat(opened).isNull()
    }

    private companion object {
        val twoPrograms = ProgramsUiState(
            isLoading = false,
            programs = listOf(
                WorkoutProgram(id = "p1", name = "Upper/Lower", slotCount = 4, isActive = true),
                WorkoutProgram(id = "p2", name = "PPL", slotCount = 6),
            ),
        )
    }
}
