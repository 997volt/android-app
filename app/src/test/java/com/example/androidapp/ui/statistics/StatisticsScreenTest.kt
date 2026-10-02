package com.example.androidapp.ui.statistics

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.RangeKind
import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.theme.AndroidAppTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the Statistics screen offers (ROADMAP N35). */
@RunWith(AndroidJUnit4::class)
class StatisticsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val series = MetricSeries(
        key = MetricKey.Body(BodyMetric.WEIGHT),
        readings = listOf(
            MetricReading(Instant.parse("2026-09-01T08:00:00Z"), 83_000.0),
            MetricReading(Instant.parse("2026-09-15T08:00:00Z"), 82_000.0),
        ),
    )

    private fun setScreen(
        state: StatisticsUiState = StatisticsUiState(
            isLoading = false,
            range = StatisticsRange(RangeKind.LAST_MONTH),
            overview = StatisticsOverview(workouts = 12, volumeGrams = 96_000_000L, personalRecords = 4),
            series = series,
        ),
        onSelectRange: (StatisticsRange) -> Unit = {},
        onSelectMetric: (MetricKey) -> Unit = {},
    ) {
        composeTestRule.setContent {
            AndroidAppTheme {
                StatisticsScreen(
                    state = state,
                    onSelectRange = onSelectRange,
                    onSelectMetric = onSelectMetric,
                )
            }
        }
    }

    @Test
    fun theThreeNumbers_areTheOverview() {
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Statistics.OVERVIEW_WORKOUTS).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.OVERVIEW_VOLUME).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.OVERVIEW_RECORDS).assertExists()
    }

    @Test
    fun aRecordCountThatWasNotAsked_isADashRatherThanAZero() {
        // Zero would claim "you set no records", which is a different statement from "not counted".
        setScreen(
            state = StatisticsUiState(
                isLoading = false,
                overview = StatisticsOverview(workouts = 3, volumeGrams = 1_000L, personalRecords = null),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Statistics.OVERVIEW_RECORDS)
            .assertTextEquals("—")
    }

    @Test
    fun theRangeChips_sayWhichIsSelected_andReportAChoice() {
        var chosen: StatisticsRange? = null
        setScreen(onSelectRange = { chosen = it })

        composeTestRule.onNodeWithTag(TestTags.Statistics.range(RangeKind.LAST_MONTH.name)).assertIsSelected()
        composeTestRule.onNodeWithTag(TestTags.Statistics.range(RangeKind.LAST_YEAR.name)).assertIsNotSelected()

        composeTestRule.onNodeWithTag(TestTags.Statistics.range(RangeKind.LAST_YEAR.name)).performClick()

        assertEquals(RangeKind.LAST_YEAR, chosen?.kind)
    }

    @Test
    fun thePicker_offersEverySeries_andReportsTheChoice() {
        var chosen: MetricKey? = null
        setScreen(onSelectMetric = { chosen = it })

        composeTestRule.onNodeWithTag(TestTags.Statistics.METRIC).performClick()

        // One from each group, rather than all twenty-one: the registry's own test proves it is complete,
        // and this is about the picker showing what the registry holds.
        val volume = MetricKey.Exercise(ExerciseTrendMetric.VOLUME)
        composeTestRule.onNodeWithTag(TestTags.Statistics.metric(volume.id)).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.metric(MetricKey.Workout(
            com.example.androidapp.domain.model.TrendMetric.RPE,
        ).id)).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.metric(MetricKey.Tape(
            com.example.androidapp.domain.model.TapeSite.WAIST,
        ).id)).assertExists()

        composeTestRule.onNodeWithTag(TestTags.Statistics.metric(volume.id)).performClick()

        assertEquals(volume, chosen)
    }

    @Test
    fun aMetricThatNeedsALift_asksForOne() {
        setScreen(
            state = StatisticsUiState(
                isLoading = false,
                selection = StatisticsSelection(metric = MetricKey.Exercise(ExerciseTrendMetric.VOLUME)),
                series = series,
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Statistics.CHOOSE_LIFT).assertExists()
    }

    @Test
    fun aMetricThatNeedsNothing_doesNotAsk() {
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Statistics.CHOOSE_LIFT).assertDoesNotExist()
    }
}
