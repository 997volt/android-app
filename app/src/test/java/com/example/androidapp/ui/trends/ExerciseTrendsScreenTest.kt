package com.example.androidapp.ui.trends

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.ui.components.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The per-exercise captions (ROADMAP N17).
 *
 * A load is stored and charted in grams, and the first version of this screen printed
 * that straight into the sentence: "Recorded once: 160000.0 kg". Device verification
 * found it; this test is what keeps it found.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseTrendsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(vararg sections: ExerciseTrendSection) {
        composeTestRule.setContent {
            ExerciseTrendsScreen(
                state = ExerciseTrendsUiState(
                    isLoading = false,
                    exerciseName = "Back Squat",
                    sessions = sections.size,
                    sections = sections.toList(),
                ),
                onBack = {},
            )
        }
    }

    @Test
    fun aLoad_isReadInKilograms_notGrams() {
        show(
            section(
                metric = ExerciseTrendMetric.HEAVIEST_SET,
                values = listOf(160_000.0),
                latest = 160_000.0,
                average = 160_000.0,
            ),
        )

        composeTestRule.onNodeWithText("160 kg", substring = true).assertIsDisplayed()
    }

    @Test
    fun reps_areReadAsACount() {
        show(
            section(
                metric = ExerciseTrendMetric.TOTAL_REPS,
                values = listOf(8.0),
                latest = 8.0,
                average = 8.0,
            ),
        )

        composeTestRule.onNodeWithText("8", substring = true).assertIsDisplayed()
    }

    @Test
    fun assistance_saysWhichWayIsForward() {
        // N17's direction decision: on an assisted machine a climb is not progress.
        show(
            section(
                metric = ExerciseTrendMetric.ASSISTANCE,
                values = listOf(20_000.0, 12_500.0),
                latest = 12_500.0,
                average = 16_250.0,
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.EXERCISE_TRENDS_DIRECTION).assertIsDisplayed()
        composeTestRule.onNodeWithText("12.5 kg", substring = true).assertIsDisplayed()
    }

    @Test
    fun aMetricNeverRecorded_getsNoSection() {
        // A card of empty charts teaches nothing.
        show(
            section(
                metric = ExerciseTrendMetric.HEAVIEST_SET,
                values = listOf(160_000.0),
                latest = 160_000.0,
                average = 160_000.0,
            ),
            section(metric = ExerciseTrendMetric.JOINT_PAIN, values = listOf(null), latest = null, average = null),
        )

        composeTestRule.onNodeWithTag(TestTags.exerciseTrend("section", "JOINT_PAIN")).assertDoesNotExist()
    }

    private fun section(
        metric: ExerciseTrendMetric,
        values: List<Double?>,
        latest: Double?,
        average: Double?,
    ) = ExerciseTrendSection(
        metric = metric,
        values = values,
        latest = latest,
        average = average,
        recorded = values.count { it != null },
    )
}
