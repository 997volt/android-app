package com.example.androidapp.ui.trends

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.model.TrendMetric
import com.example.androidapp.ui.components.TestTags
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The trends screen (ROADMAP N13).
 *
 * The chart is drawn on a Canvas and so is invisible to the semantics tree, which is
 * exactly why the assertions are about the *caption* — the sentence a screen reader
 * hears — and about whether a line is offered at all.
 */
@RunWith(AndroidJUnit4::class)
class TrendsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setScreen(state: TrendsUiState) {
        composeTestRule.setContent {
            TrendsScreen(state = state, onBack = {})
        }
    }

    @Test
    fun everyMetric_getsItsOwnSection_andItsOwnCaption() {
        setScreen(state())

        // assertExists rather than assertIsDisplayed: the third section sits below
        // the fold in the test window, and scrolling it into view would test the
        // scroll rather than the content.
        TrendMetric.entries.forEach { metric ->
            composeTestRule.onNodeWithTag(TestTags.trendSection(metric.name)).assertExists()
            composeTestRule.onNodeWithTag(TestTags.trendCaption(metric.name)).assertExists()
        }
    }

    @Test
    fun aMetricWithATrend_isDrawn_andOneWithASinglePoint_isNot() {
        // The distinction the screen exists to make honestly: one reading is a number.
        setScreen(state())

        composeTestRule.onNodeWithTag(TestTags.trendChart(TrendMetric.RPE.name)).assertExists()
        composeTestRule.onNodeWithTag(TestTags.trendChart(TrendMetric.JOINT_PAIN.name))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.trendCaption(TrendMetric.JOINT_PAIN.name))
            .assertExists()
    }

    @Test
    fun theWindow_saysHowManyWorkoutsAreOnScreen() {
        setScreen(state())

        composeTestRule.onNodeWithTag(TestTags.TRENDS_WINDOW).assertIsDisplayed()
    }

    @Test
    fun withNothingRecorded_theScreenSaysSo_ratherThanDrawingEmptyAxes() {
        setScreen(
            TrendsUiState(
                isLoading = false,
                windowSize = 0,
                sections = TrendMetric.entries.map {
                    TrendSection(metric = it, values = emptyList(), latest = null, average = null, recorded = 0)
                },
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.TRENDS_EMPTY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.trendChart(TrendMetric.RPE.name)).assertDoesNotExist()
    }

    @Test
    fun aFailedRead_isShown_whereTheChartsWouldHaveBeen() {
        setScreen(TrendsUiState(isLoading = false, error = DataError.Storage(IOException("locked"))))

        composeTestRule.onNodeWithTag(TestTags.TRENDS_READ_ERROR).assertIsDisplayed()
    }

    private fun state() = TrendsUiState(
        isLoading = false,
        windowSize = 4,
        sections = listOf(
            TrendSection(
                metric = TrendMetric.RPE,
                values = listOf(6.0, 7.0, null, 8.0),
                latest = 8.0,
                average = 7.0,
                recorded = 3,
            ),
            TrendSection(
                metric = TrendMetric.MUSCLE_FEEL,
                values = listOf(6.0, 7.0, 7.0, 8.0),
                latest = 8.0,
                average = 7.0,
                recorded = 4,
            ),
            TrendSection(
                metric = TrendMetric.JOINT_PAIN,
                values = listOf(null, 2.0, null, null),
                latest = 2.0,
                average = 2.0,
                recorded = 1,
            ),
        ),
    )
}
