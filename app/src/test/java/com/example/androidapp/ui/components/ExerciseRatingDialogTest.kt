package com.example.androidapp.ui.components

import com.example.androidapp.R
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How an exercise felt — muscle feel and joint pain (ROADMAP N8).
 *
 * Both fields are optional and the whole capture is skippable, so what matters here
 * is that saving nothing writes nothing, that a value off the 1–10 scale blocks
 * Save rather than being clamped, and that dismissing the prompt is not a write.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseRatingDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var saved: Pair<Int?, Int?>? = null
    private var savedNote: String? = null
    private var saveCalled = false
    private var dismissed = false

    private fun show(
        initialFeel: Int? = null,
        initialPain: Int? = null,
        initialNote: String = "",
        isPrompt: Boolean = true,
    ) {
        composeTestRule.setContent {
            ExerciseRatingDialog(
                initialMuscleFeel = initialFeel,
                initialJointPain = initialPain,
                initialJointPainNote = initialNote,
                isPrompt = isPrompt,
                onDismiss = { dismissed = true },
                onSave = { feel, pain, note ->
                    saved = feel to pain
                    savedNote = note
                    saveCalled = true
                },
            )
        }
    }

    @Test
    fun savingWithBothFieldsEmpty_meansNotRated() {
        show()

        composeTestRule.onNodeWithTag(TestTags.RATING_SAVE).performClick()

        assertTrue(saveCalled)
        assertEquals(null to null, saved)
    }

    @Test
    fun typedRatings_areReportedOnSave() {
        show()

        composeTestRule.onNodeWithTag(TestTags.RATING_MUSCLE_FIELD).performTextInput("8")
        composeTestRule.onNodeWithTag(TestTags.RATING_JOINT_FIELD).performTextInput("2")
        composeTestRule.onNodeWithTag(TestTags.RATING_SAVE).performClick()

        assertEquals(8 to 2, saved)
    }

    @Test
    fun aValueOutsideTheScale_disablesSave_ratherThanClampingIt() {
        show()

        composeTestRule.onNodeWithTag(TestTags.RATING_MUSCLE_FIELD).performTextInput("11")

        composeTestRule.onNodeWithTag(TestTags.RATING_SAVE).assertIsNotEnabled()
    }

    @Test
    fun anUnparseableValue_disablesSave() {
        show()

        composeTestRule.onNodeWithTag(TestTags.RATING_JOINT_FIELD).performTextInput("sore")

        composeTestRule.onNodeWithTag(TestTags.RATING_SAVE).assertIsNotEnabled()
    }

    @Test
    fun existingRatings_arePrefilled() {
        show(initialFeel = 8, initialPain = 2, isPrompt = false)

        composeTestRule.onNodeWithTag(TestTags.RATING_MUSCLE_FIELD).assertTextContains("8")
        composeTestRule.onNodeWithTag(TestTags.RATING_JOINT_FIELD).assertTextContains("2")
    }

    @Test
    fun bothScales_sayWhatTheirEndsMean() {
        // ROADMAP N12: the number is chosen here, so the meaning of 1 and 10 has to
        // be here — that is what keeps a 7 this month comparable to a 7 next month.
        show()

        composeTestRule.onNodeWithText(muscleAnchors()).assertExists()
        composeTestRule.onNodeWithText(jointAnchors()).assertExists()
    }

    /** Read from resources, so a reworded anchor does not break the test. */
    private fun muscleAnchors(): String =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.rating_muscle_anchors)

    private fun jointAnchors(): String =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.rating_joint_anchors)

    @Test
    fun theJointPainLocation_ridesAlongWithTheRating() {
        // ROADMAP N9: the note is saved with the rating it explains, trimmed.
        show()

        composeTestRule.onNodeWithTag(TestTags.RATING_MUSCLE_FIELD).performTextInput("8")
        composeTestRule.onNodeWithTag(TestTags.RATING_JOINT_FIELD).performTextInput("4")
        composeTestRule.onNodeWithTag(TestTags.RATING_JOINT_NOTE_FIELD).performTextInput("  left shoulder  ")
        composeTestRule.onNodeWithTag(TestTags.RATING_SAVE).performClick()

        assertEquals(8 to 4, saved)
        assertEquals("left shoulder", savedNote)
    }

    @Test
    fun anEmptyLocation_isSavedAsNothing_ratherThanAnEmptyString() {
        show(initialFeel = 8)

        composeTestRule.onNodeWithTag(TestTags.RATING_SAVE).performClick()

        assertEquals(null, savedNote)
    }

    @Test
    fun anExistingLocation_isPrefilled_whenEditingLater() {
        show(initialNote = "right knee", isPrompt = false)

        composeTestRule.onNodeWithTag(TestTags.RATING_JOINT_NOTE_FIELD).assertTextContains("right knee")
    }

    @Test
    fun dismissing_writesNothing() {
        show()

        composeTestRule.onNodeWithTag(TestTags.RATING_DISMISS).performClick()

        assertTrue(dismissed)
        assertFalse(saveCalled)
    }
}
