package com.example.androidapp.ui.statistics

import com.example.androidapp.domain.DataError
import java.io.IOException
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.Equipment
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.RangeKind
import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.theme.AndroidAppTheme
import java.time.Instant
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
        onSelectExercise: (String) -> Unit = {},
        onOpenMeasurements: (() -> Unit)? = null,
        onOpenAdherence: (() -> Unit)? = null,
        onSetGoal: (Double?) -> Unit = {},
    ) {
        composeTestRule.setContent {
            AndroidAppTheme {
                StatisticsScreen(
                    state = state,
                    onSelectRange = onSelectRange,
                    onSelectMetric = onSelectMetric,
                    onSelectExercise = onSelectExercise,
                    onOpenMeasurements = onOpenMeasurements,
                    onOpenAdherence = onOpenAdherence,
                    onSetGoal = onSetGoal,
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

        assertThat(chosen?.kind).isEqualTo(RangeKind.LAST_YEAR)
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

        assertThat(chosen).isEqualTo(volume)
    }

    @Test
    fun choosingCustom_asksForTheDatesBeforeItApplies() {
        // A chip that selected an unbounded window would make the chart look broken rather than empty, so
        // custom asks its question first and refuses to apply a window with one end.
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Statistics.range(RangeKind.CUSTOM.name)).performClick()

        composeTestRule.onNodeWithTag(TestTags.Statistics.CUSTOM_FROM).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.CUSTOM_TO).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.CUSTOM_APPLY).assertIsNotEnabled()
    }

    @Test
    fun aCustomRange_saysSoInTheChips() {
        setScreen(
            state = StatisticsUiState(
                isLoading = false,
                range = StatisticsRange(
                    kind = RangeKind.CUSTOM,
                    from = java.time.LocalDate.of(2026, 8, 1),
                    to = java.time.LocalDate.of(2026, 8, 31),
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Statistics.range(RangeKind.CUSTOM.name)).assertIsSelected()
        composeTestRule.onNodeWithTag(TestTags.Statistics.range(RangeKind.LAST_MONTH.name)).assertIsNotSelected()
    }

    @Test
    fun aTarget_isShownInTheMetricsOwnUnit_andEditable() {
        // Typed as 82 kilograms and reported as 82000 grams: the row shows what a person reads, and the
        // screen hands over what the app stores (ROADMAP N39).
        var reported: Double? = null
        var called = false
        setScreen(
            state = StatisticsUiState(isLoading = false, series = weightSeries(), goal = 80_000.0),
            onSetGoal = { called = true; reported = it },
        )

        composeTestRule.onNodeWithTag(TestTags.Statistics.GOAL_SET).performScrollTo()
        composeTestRule.onNodeWithTag(TestTags.Statistics.GOAL_SET).assertTextEquals("80")

        composeTestRule.onNodeWithTag(TestTags.Statistics.GOAL_SET).performClick()
        composeTestRule.onNodeWithTag(TestTags.Statistics.GOAL_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(TestTags.Statistics.GOAL_FIELD).performTextInput("82")
        composeTestRule.onNodeWithTag(TestTags.Statistics.GOAL_CONFIRM).performClick()

        assertThat(called).isTrue()
        assertThat(reported).isEqualTo(82_000.0)
    }

    @Test
    fun clearingATarget_isOfferedOnlyWhenThereIsOne() {
        setScreen(state = StatisticsUiState(isLoading = false, series = weightSeries(), goal = 80_000.0))

        composeTestRule.onNodeWithTag(TestTags.Statistics.GOAL_SET).performScrollTo().performClick()

        composeTestRule.onNodeWithTag(TestTags.Statistics.GOAL_CLEAR).assertExists()
    }

    @Test
    fun withNoTarget_theRowAsksForOne_ratherThanShowingZero() {
        // A target of zero would be a line along the bottom of every chart and a claim nobody made.
        setScreen(state = StatisticsUiState(isLoading = false, series = weightSeries()))

        composeTestRule.onNodeWithTag(TestTags.Statistics.GOAL_SET).performScrollTo()

        composeTestRule.onNodeWithTag(TestTags.Statistics.GOAL_SET).assertTextEquals("Set a target")
    }

    @Test
    fun theMovingAveragePeriod_isConfigurable_andSaysWhichIsOn() {
        // Seven by default, which is the reading a daily weigh-in wants: the mean of the last week against the
        // noise of the days (N40).
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Statistics.MOVING_AVERAGE).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.movingAveragePeriod(DAYS)).assertIsSelected()

        composeTestRule.onNodeWithTag(TestTags.Statistics.movingAveragePeriod(14)).performScrollTo().performClick()

        composeTestRule.onNodeWithTag(TestTags.Statistics.movingAveragePeriod(14)).assertIsSelected()
    }

    @Test
    fun aQuantityMetric_anchorsItsAxisAtZero() {
        // The visible half of N38: a volume bar starts where volume starts, and the label says so. The bars
        // themselves are a canvas, which the app blanks out for screen readers — so this is the assertion
        // that can see the policy at all.
        setScreen(
            state = StatisticsUiState(
                isLoading = false,
                selection = StatisticsSelection(metric = MetricKey.Exercise(ExerciseTrendMetric.VOLUME)),
                series = MetricSeries(
                    key = MetricKey.Exercise(ExerciseTrendMetric.VOLUME),
                    readings = listOf(
                        MetricReading(Instant.parse("2026-09-01T08:00:00Z"), 4_000_000.0),
                        MetricReading(Instant.parse("2026-09-15T08:00:00Z"), 4_500_000.0),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Statistics.CHART_MIN).assertTextEquals("0")
    }

    @Test
    fun theChartSaysWhenItStartsAndEnds_andOnWhatScale() {
        // A canvas cannot be read by a screen reader, and "when did this start and end" is the first question
        // even for a reader who can see the line (ROADMAP N37).
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Statistics.CHART_FIRST_DATE).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.CHART_LAST_DATE).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.CHART_MIN).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.CHART_MAX).assertExists()
    }

    @Test
    fun oneReading_isANumberRatherThanALine() {
        // The chart needs two points to mean anything; the axis labels would be describing a line that is not
        // drawn, so they go with it.
        setScreen(
            state = StatisticsUiState(
                isLoading = false,
                series = MetricSeries(
                    key = MetricKey.Body(BodyMetric.WEIGHT),
                    readings = listOf(MetricReading(Instant.parse("2026-09-01T08:00:00Z"), 82_000.0)),
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Statistics.CHART).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.Statistics.CHART_FIRST_DATE).assertDoesNotExist()
    }

    @Test
    fun theReadingsAreCollapsed_untilTheyAreAskedFor() {
        // The chart is what the screen is for; a year of readings is a wall of numbers to anyone who has not
        // asked for one.
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Statistics.READINGS_TOGGLE).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.reading(0)).assertDoesNotExist()
    }

    @Test
    fun expanding_putsTheAverageOnTop_andTheReadingsNewestFirst() {
        setScreen()

        // The screen scrolls and the chart's labels sit above this, so the control may be below the fold:
        // Compose dispatches a click at coordinates, and a node that is not on screen is not clickable.
        composeTestRule.onNodeWithTag(TestTags.Statistics.READINGS_TOGGLE).performScrollTo().performClick()

        composeTestRule.onNodeWithTag(TestTags.Statistics.READINGS_AVERAGE).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.reading(0)).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.reading(1)).assertExists()
        composeTestRule.onNodeWithTag(TestTags.Statistics.reading(2)).assertDoesNotExist()
    }

    @Test
    fun expanding_showsTheTrendBesideTheAverage() {
        // The row N36 deliberately left out, arriving with the line that gives it a number (N39).
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Statistics.READINGS_TOGGLE).performScrollTo().performClick()

        composeTestRule.onNodeWithTag(TestTags.Statistics.READINGS_TREND).assertExists()
    }

    @Test
    fun withNothingRecorded_thereIsNoReadingsControl() {
        // A disclosure control that opens onto emptiness is worse than no control.
        setScreen(
            state = StatisticsUiState(
                isLoading = false,
                series = MetricSeries(
                    key = MetricKey.Body(BodyMetric.WEIGHT),
                    readings = listOf(MetricReading(Instant.parse("2026-09-01T08:00:00Z"), null)),
                ),
            ),
        )

        composeTestRule.onNodeWithTag(TestTags.Statistics.READINGS_TOGGLE).assertDoesNotExist()
    }

    @Test
    fun measurementsAreReachable_fromHere() {
        // The regression this action fixes: deleting the trends screen took its measurements link with it,
        // and the screen was registered but reachable from nowhere.
        var opened = false
        setScreen(onOpenMeasurements = { opened = true })

        composeTestRule.onNodeWithTag(TestTags.Statistics.MEASUREMENTS).performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun withoutAWiredAction_thereIsNoMeasurementsButton() {
        // Like every other optional action here: a preview or a test that does not exercise it gets no dead
        // control.
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Statistics.MEASUREMENTS).assertDoesNotExist()
    }

    @Test
    fun adherenceIsReachable_fromHere() {
        // The aggregate over the session-level plan-versus-actual (ROADMAP P3.5), pushed from the tab that
        // answers how everything is going rather than earning a sixth tab (N34).
        var opened = false
        setScreen(onOpenAdherence = { opened = true })

        composeTestRule.onNodeWithTag(TestTags.Statistics.ADHERENCE).performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun withoutAWiredAdherenceAction_thereIsNoButton() {
        setScreen()

        composeTestRule.onNodeWithTag(TestTags.Statistics.ADHERENCE).assertDoesNotExist()
    }

    @Test
    fun theLiftPicker_offersTheLibrary_andReportsTheChoice() {
        var chosen: String? = null
        setScreen(
            state = StatisticsUiState(
                isLoading = false,
                selection = StatisticsSelection(metric = MetricKey.Exercise(ExerciseTrendMetric.VOLUME)),
                lifts = listOf(
                    lift("back-squat", "Back Squat"),
                    lift("bench-press", "Barbell Bench Press"),
                ),
            ),
            onSelectExercise = { chosen = it },
        )

        composeTestRule.onNodeWithTag(TestTags.Statistics.LIFT).performClick()
        composeTestRule.onNodeWithTag(TestTags.Statistics.lift("bench-press")).performClick()

        assertThat(chosen).isEqualTo("bench-press")
    }

    private fun lift(id: String, name: String) = Exercise(
        id = id,
        name = name,
        primaryMuscle = MuscleGroup.QUADS,
        secondaryMuscles = emptyList(),
        equipment = Equipment.BARBELL,
        movementPattern = MovementPattern.SQUAT,
        isCustom = false,
    )

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

    @Test
    fun withALiftAlreadyChosen_thePickerIsStillOffered() {
        // The defect this guards: the picker was drawn only while *nothing* was selected, and every entry
        // point selects a lift, so arriving from the library or from a lift just performed left no way to
        // change it. The prompt is for the empty case; the control is for either.
        setScreen(
            state = StatisticsUiState(
                isLoading = false,
                selection = StatisticsSelection(
                    metric = MetricKey.Exercise(ExerciseTrendMetric.VOLUME),
                    exerciseId = "back-squat",
                ),
                lifts = listOf(lift("back-squat", "Back Squat"), lift("bench-press", "Barbell Bench Press")),
                series = series,
            ),
        )

        // No question, because an answer is already on screen...
        composeTestRule.onNodeWithTag(TestTags.Statistics.CHOOSE_LIFT).assertDoesNotExist()
        // ...but the control that changes the answer is.
        composeTestRule.onNodeWithTag(TestTags.Statistics.LIFT).assertExists()
    }

    @Test
    fun theChosenLift_isTheOneTheControlNames() {
        setScreen(
            state = StatisticsUiState(
                isLoading = false,
                selection = StatisticsSelection(
                    metric = MetricKey.Exercise(ExerciseTrendMetric.VOLUME),
                    exerciseId = "bench-press",
                ),
                lifts = listOf(lift("back-squat", "Back Squat"), lift("bench-press", "Barbell Bench Press")),
                series = series,
            ),
        )

        composeTestRule.onNodeWithText("Barbell Bench Press").assertExists()
    }

    @Test
    fun loading_saysSo_ratherThanClaimingZero() {
        // The overview reads "0 workouts · 0 kg" from the default state, which is a claim about data that has
        // not been read yet — and the same zeros used to appear behind a failed read.
        setScreen(state = StatisticsUiState(isLoading = true))

        composeTestRule.onNodeWithText("Loading statistics…").assertExists()
    }

    @Test
    fun aFailedRead_saysSo_ratherThanClaimingZero() {
        setScreen(
            state = StatisticsUiState(
                isLoading = false,
                error = DataError.Storage(IOException("disk full")),
            ),
        )

        composeTestRule.onNodeWithText("Couldn’t save that. Your last change may not be stored.").assertExists()
    }
}

/** A pair of weigh-ins: enough for a chart, which is where the target line lives. */
private fun weightSeries() = MetricSeries(
    key = MetricKey.Body(BodyMetric.WEIGHT),
    readings = listOf(
        MetricReading(Instant.parse("2026-09-01T08:00:00Z"), 83_000.0),
        MetricReading(Instant.parse("2026-09-15T08:00:00Z"), 82_000.0),
    ),
)
