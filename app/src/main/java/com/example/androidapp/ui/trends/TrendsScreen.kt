package com.example.androidapp.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.model.TrendMetric
import com.example.androidapp.domain.model.asRating
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.TestTags
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
    }
}

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
            TrendChart(
                values = section.values,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT_DP.dp)
                    .padding(top = 12.dp)
                    .testTag(TestTags.trendChart(section.metric.name)),
            )
        }
    }
}

/**
 * The line, on a fixed 1–10 axis.
 *
 * Fixed rather than scaled to the data, because a chart that rescales makes a 0.2
 * change look like a cliff — and because every series here shares that axis, so the
 * three can be read against each other.
 *
 * A missing value breaks the line rather than being interpolated across: a gap is
 * "not recorded", and drawing through it would invent a measurement.
 *
 * Marked as decorative: the caption above carries the numbers, so a screen reader
 * hears the sentence rather than a description of a picture it cannot see.
 */
@Composable
private fun TrendChart(values: List<Double?>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val axis = TrendAxis(MIN_RATING, MAX_RATING)
        val step = if (values.size > 1) size.width / (values.size - 1) else 0f

        fun y(value: Double): Float = size.height * (1f - axis.fractionOf(value).toFloat())

        // The top and bottom of the scale, so an empty stretch still reads as a scale.
        listOf(MIN_RATING, MAX_RATING).forEach { value ->
            drawLine(
                color = gridColor,
                start = Offset(0f, y(value)),
                end = Offset(size.width, y(value)),
                strokeWidth = GRID_STROKE_PX,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON_PX, DASH_OFF_PX)),
            )
        }

        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val next = values.getOrNull(index + 1)
            if (next != null) {
                drawLine(
                    color = lineColor,
                    start = Offset(step * index, y(value)),
                    end = Offset(step * (index + 1), y(next)),
                    strokeWidth = LINE_STROKE_PX,
                )
            }
            drawCircle(color = dotColor, radius = DOT_RADIUS_PX, center = Offset(step * index, y(value)))
        }
    }
}

/** Where a 1–10 value sits on the axis, as a 0..1 fraction from the bottom. */
private class TrendAxis(private val min: Double, private val max: Double) {
    fun fractionOf(value: Double): Double =
        ((value - min) / (max - min)).coerceIn(0.0, 1.0)
}

private const val MIN_RATING = 1.0
private const val MAX_RATING = 10.0

/** The chart's ink, in pixels: it is a fixed-size glyph, not a responsive layout. */
private const val GRID_STROKE_PX = 1f
private const val LINE_STROKE_PX = 3f
private const val DOT_RADIUS_PX = 4f
private const val DASH_ON_PX = 6f
private const val DASH_OFF_PX = 6f
private const val CHART_HEIGHT_DP = 72

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
