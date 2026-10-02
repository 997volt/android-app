package com.example.androidapp.ui.trends

import com.example.androidapp.ui.measurements.MeasurementFormat
import com.example.androidapp.domain.Weight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.model.TrendMetric
import com.example.androidapp.domain.model.asRating
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.RATING_AXIS_MAX
import com.example.androidapp.ui.components.RATING_AXIS_MIN
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.TrendChartFrame
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.theme.AndroidAppTheme

/**
 * What the app collected, read back (ROADMAP N13).
 *
 * Three series over the same workouts, each on the same fixed 1–10 axis — which is
 * what makes them comparable at a glance and is the one thing the label work (N12)
 * bought: a 7 means the same thing on every chart here.
 */
@Composable
fun TrendsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TrendsScreen(state = state, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    state: TrendsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.trends_title),
                        modifier = Modifier.testTag(TestTags.TRENDS_TITLE),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> CenteredMessage(
                text = stringResource(R.string.trends_loading),
                modifier = Modifier.padding(innerPadding),
            )

            state.error != null -> CenteredMessage(
                text = dataErrorMessage(state.error),
                modifier = Modifier.padding(innerPadding).testTag(TestTags.TRENDS_READ_ERROR),
            )

            state.hasNothingRecorded -> CenteredMessage(
                text = stringResource(R.string.trends_empty),
                hint = stringResource(R.string.trends_empty_hint),
                modifier = Modifier.padding(innerPadding).testTag(TestTags.TRENDS_EMPTY),
            )

            else -> TrendsBody(state = state, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun TrendsBody(state: TrendsUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = pluralStringResource(R.plurals.trends_window, state.windowSize, state.windowSize),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TestTags.TRENDS_WINDOW),
        )

        state.sections.forEach { section ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            TrendSectionBlock(section)
        }

        // Measurements after the training trends (ROADMAP N32): the same chart, their own axis.
        state.measurements.filter { it.recorded > 0 }.forEach { trend ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            MeasurementSectionBlock(trend)
        }
    }
}

@Composable
private fun MeasurementSectionBlock(trend: MeasurementTrend, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag(TestTags.Measurements.trend(trend.labelRes))) {
        Text(
            text = stringResource(trend.labelRes),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = trend.caption(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TestTags.Measurements.trendCaption(trend.labelRes)),
        )

        if (trend.hasLine) {
            val readings = trend.values.filterNotNull()
            TrendChartFrame(
                values = trend.values,
                minValue = readings.min() - axisPad(readings),
                maxValue = readings.max() + axisPad(readings),
                testTag = TestTags.Measurements.trendChart(trend.labelRes),
            )
        }
    }
}

/**
 * How far the axis reaches past the readings.
 *
 * A measurement series is not a rating on a fixed 1-to-10, so its own range is what the chart has to
 * show — with a little room above and below, so a line at the top edge is not mistaken for a ceiling.
 * A series where every reading is the same has no range at all, so it gets one.
 */
private fun axisPad(readings: List<Double>): Double {
    val span = readings.max() - readings.min()
    return if (span == 0.0) 1.0 else span * AXIS_PAD_FRACTION
}

private const val AXIS_PAD_FRACTION = 0.1

@Composable
private fun TrendSectionBlock(section: TrendSection, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag(TestTags.trendSection(section.metric.name))) {
        Text(
            text = stringResource(section.metric.titleRes()),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = section.caption(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TestTags.trendCaption(section.metric.name)),
        )

        if (section.hasLine) {
            TrendChartFrame(
                values = section.values,
                minValue = RATING_AXIS_MIN,
                maxValue = RATING_AXIS_MAX,
                testTag = TestTags.trendChart(section.metric.name),
            )
        }
    }
}

private fun TrendMetric.titleRes(): Int = when (this) {
    TrendMetric.RPE -> R.string.trends_metric_rpe
    TrendMetric.MUSCLE_FEEL -> R.string.trends_metric_muscle
    TrendMetric.JOINT_PAIN -> R.string.trends_metric_joint
}

/** The sentence under a chart: what it is now, and what it has averaged. */
@Composable
private fun TrendSection.caption(): String = when {
    recorded == 0 -> stringResource(R.string.trends_not_recorded)
    recorded == 1 -> stringResource(R.string.trends_recorded_once, latest.orEmptyRating())
    // A plural, not a string: the count inflects in languages this app may be
    // translated into, and lint is right to insist on it.
    else -> pluralStringResource(
        R.plurals.trends_caption,
        recorded,
        latest.orEmptyRating(),
        average.orEmptyRating(),
        recorded,
    )
}

/** The caption's numbers; a recorded metric always has both, so this is a formality. */
private fun Double?.orEmptyRating(): String = this?.asRating().orEmpty()

@Preview(showBackground = true)
@Composable
private fun TrendsScreenPreview() {
    AndroidAppTheme {
        TrendsScreen(
            state = TrendsUiState(
                isLoading = false,
                windowSize = 4,
                sections = listOf(
                    TrendSection(
                        metric = TrendMetric.RPE,
                        values = listOf(7.0, 7.5, null, 8.0),
                        latest = 8.0,
                        average = 7.5,
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
            ),
            onBack = {},
        )
    }
}

/** What a measurement series reads as: the latest reading, and how many there have been. */
private fun MeasurementTrend.caption(): String {
    val value = latest ?: return ""
    val reading = when (unit) {
        MeasurementUnit.KILOGRAMS -> Weight.kilograms(value.toLong())
        MeasurementUnit.PERCENT -> MeasurementFormat.percent(value.toInt())
        MeasurementUnit.CENTIMETRES -> MeasurementFormat.centimetres(value.toLong())
    }
    return reading
}
