package com.example.androidapp.ui.workout

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.PlanComparison
import com.example.androidapp.ui.components.TestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The review a finished workout gets (ROADMAP N20).
 *
 * The sentence this screen exists for is the delta on the top set — prescribed 6×2 at 92.5,
 * performed 6×2 at 92.5, top single 2.5 over plan — so that is what is asserted, along with
 * the two things the review must not hide: an exercise that was skipped, and a rating the
 * user had just given.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutSummaryDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var dismissals = 0

    private fun show(summary: WorkoutSummary) {
        composeTestRule.setContent {
            WorkoutSummaryDialog(summary = summary, onDismiss = { dismissals++ })
        }
    }

    private fun summary(
        comparisons: List<PlanComparison> = emptyList(),
        ratings: List<ExerciseRating> = emptyList(),
    ) = WorkoutSummary(
        note = null,
        readinessNote = null,
        totalSets = 8,
        totalReps = 40,
        totalVolumeGrams = 1_000_000L,
        ratings = ratings,
        comparisons = comparisons,
    )

    @Test
    fun theTotals_areReadable_withUnits() {
        show(summary())

        composeTestRule.onNodeWithText("Sets 8 · reps 40 · 1000 kg").assertExists()
    }

    @Test
    fun anOverPlanTopSet_saysSoInKilos() {
        show(
            summary(
                comparisons = listOf(
                    PlanComparison(
                        name = "Deadlift",
                        prescribedSets = 1,
                        performedSets = 1,
                        prescribedReps = 1,
                        performedReps = 1,
                        prescribedTopWeightGrams = 90_000L,
                        performedTopWeightGrams = 92_500L,
                        topSetDeltaGrams = 2_500L,
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("Warm-up sets are not counted.").assertExists()
        composeTestRule.onNodeWithText("prescribed 1×1 at 90 kg").assertExists()
        composeTestRule.onNodeWithText("performed 1×1 at 92.5 kg").assertExists()
        composeTestRule.onNodeWithText("2.5 kg over plan").assertExists()
    }

    @Test
    fun aSkippedExercise_isNotQuietlyOmitted() {
        // A review that only lists what was done is a compliment, not a record.
        show(
            summary(
                comparisons = listOf(
                    PlanComparison(
                        name = "Squat",
                        prescribedSets = 5,
                        performedSets = 0,
                        prescribedReps = 5,
                        performedReps = 0,
                        prescribedTopWeightGrams = 100_000L,
                        performedTopWeightGrams = null,
                        topSetDeltaGrams = null,
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("performed not performed").assertExists()
        composeTestRule.onNodeWithText("skipped — the plan asked for it").assertExists()
    }

    @Test
    fun aRatingTheUserJustGave_isReadBack() {
        // The ratings used to go nowhere the user could see (N20's complaint).
        show(summary(ratings = listOf(ExerciseRating("Back Squat", muscleFeel = 8, jointPain = 2))))

        composeTestRule.onNodeWithText("How it felt").assertExists()
        composeTestRule.onNodeWithText("Back Squat — felt 8/10, joints 2/10").assertExists()
    }

    @Test
    fun withoutAPlan_thereIsNoComparisonSection() {
        show(summary())

        composeTestRule.onNodeWithText("Plan vs actual").assertDoesNotExist()
    }

    @Test
    fun done_dismisses() {
        show(summary())

        composeTestRule.onNodeWithTag(TestTags.SUMMARY_DONE).performClick()

        assertEquals(1, dismissals)
    }
}
