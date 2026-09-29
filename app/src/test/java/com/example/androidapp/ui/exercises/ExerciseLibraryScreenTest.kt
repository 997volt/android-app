package com.example.androidapp.ui.exercises

import java.io.IOException
import com.example.androidapp.domain.DataError
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import com.example.androidapp.ui.components.TestTags
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        ExerciseListItem(id = "back-squat", name = "Back Squat", subtitle = "Quads · Barbell"),
        ExerciseListItem(id = "hammer-curl", name = "Hammer Curl", subtitle = "Biceps · Dumbbell"),
    )

            @Test
    fun anEmptyLibrary_saysSo_ratherThanBlamingTheSearch() {
        setScreen(
            ExerciseLibraryUiState(isLoading = false, items = emptyList(), libraryIsEmpty = true),
        )

        composeTestRule.onNodeWithTag(TestTags.LIBRARY_EMPTY_LIBRARY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.LIBRARY_NO_MATCH).assertDoesNotExist()
    }

    @Test
    fun aSearchWithNoHits_namesTheQuery() {
        setScreen(
            ExerciseLibraryUiState(
                isLoading = false,
                items = emptyList(),
                libraryIsEmpty = false,
                query = "zzz",
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.LIBRARY_NO_MATCH).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.LIBRARY_EMPTY_LIBRARY).assertDoesNotExist()
    }

    private fun setScreen(
        state: ExerciseLibraryUiState,
        onQueryChange: (String) -> Unit = {},
        onExerciseClick: (String) -> Unit = {},
        onNewExercise: (() -> Unit)? = null,
        onOpenHistory: (() -> Unit)? = null,
    ) {
        composeTestRule.setContent {
            ExerciseLibraryScreen(
                state = state,
                title = "Exercise library",
                onQueryChange = onQueryChange,
                onExerciseClick = onExerciseClick,
                onNewExercise = onNewExercise,
                onOpenHistory = onOpenHistory,
            )
        }
    }

    @Test
    fun rendersTitleAndRows() {
        setScreen(ExerciseLibraryUiState(isLoading = false, items = items))

        composeTestRule.onNodeWithTag(TestTags.LIBRARY_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Back Squat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quads · Barbell").assertIsDisplayed()
    }

    @Test
    fun anUneditedCustomExercise_rendersItsNameWithoutAnOtherSubtitle() {
        setScreen(
            ExerciseLibraryUiState(
                isLoading = false,
                items = listOf(ExerciseListItem(id = "custom-1", name = "Sled Push", subtitle = null)),
            ),
        )

        composeTestRule.onNodeWithText("Sled Push").assertIsDisplayed()
        composeTestRule.onNodeWithText("Other · Other").assertDoesNotExist()
    }

    @Test
    fun withNoCreateCallback_thereIsNoNewExerciseAction() {
        setScreen(ExerciseLibraryUiState(isLoading = false, items = items))

        composeTestRule.onNodeWithTag(TestTags.LIBRARY_NEW_EXERCISE).assertDoesNotExist()
    }

    @Test
    fun tappingNewExercise_reportsIt() {
        var started = false
        setScreen(
            ExerciseLibraryUiState(isLoading = false, items = items),
            onNewExercise = { started = true },
        )

        composeTestRule.onNodeWithTag(TestTags.LIBRARY_NEW_EXERCISE).performClick()

        assertTrue("the picker's create action should be wired through", started)
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

        composeTestRule.onNodeWithTag(TestTags.LIBRARY_NO_MATCH).assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading exercises…").assertDoesNotExist()
    }

    @Test
    fun theLibrarysMenu_noLongerOffersExportOrImport() {
        // ROADMAP B1: they moved to the home overflow, and leaving a second copy
        // here is the drift the fix exists to remove — one path, not two.
        setScreen(ExerciseLibraryUiState(isLoading = false), onOpenHistory = {})
        composeTestRule.onNodeWithTag(TestTags.LIBRARY_MENU).performClick()

        composeTestRule.onNodeWithTag(TestTags.DATA_EXPORT).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.DATA_IMPORT).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.LIBRARY_HISTORY).assertIsDisplayed()
    }

    @Test
    fun aFailedRead_isShown_inPlaceOfTheList() {
        // ROADMAP B4: the failure is rendered where the data would have been, so an
        // empty list cannot be mistaken for an empty library.
        setScreen(
            ExerciseLibraryUiState(
                isLoading = false,
                error = DataError.Storage(IOException("database is locked")),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.LIBRARY_READ_ERROR).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.LIBRARY_EMPTY_LIBRARY).assertDoesNotExist()
    }
}
