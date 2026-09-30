package com.example.androidapp.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.asRating
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.TrendChartFrame
import com.example.androidapp.ui.components.dataErrorMessage

/**
 * One exercise's trends (ROADMAP N17).
 *
 * The narrow question a lifter asks — *how is my bench press going* — answered from rows
 * the app already writes. It reuses N13's chart and adds nothing to storage.
 */
@Composable
fun ExerciseTrendsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseTrendsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseTrendsScreen(state = state, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseTrendsScreen(
    state: ExerciseTrendsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.exerciseName.isEmpty()) {
                            stringResource(R.string.exercise_trends_title)
                        } else {
                            stringResource(R.string.exercise_trends_title_named, state.exerciseName)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.exercise_detail_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> CenteredMessage(
                text = stringResource(R.string.history_loading),
                modifier = Modifier.padding(innerPadding),
            )

            state.error != null -> CenteredMessage(
                text = dataErrorMessage(state.error),
                modifier = Modifier.padding(innerPadding).testTag(TestTags.EXERCISE_TRENDS_ERROR),
            )

            state.isEmpty -> CenteredMessage(
                text = stringResource(R.string.exercise_trends_empty),
                hint = stringResource(R.string.exercise_trends_empty_hint),
                modifier = Modifier.padding(innerPadding).testTag(TestTags.EXERCISE_TRENDS_EMPTY),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Only the metrics this exercise actually recorded: a card of empty
                // charts teaches nothing, and for most lifts there is no assistance.
                items(state.sections.count { it.recorded > 0 }) { index ->
                    val section = state.sections.filter { it.recorded > 0 }[index]
                    ExerciseTrendSectionBlock(section = section, sessions = state.sessions)
                }
            }
        }
    }
}

@Composable
private fun ExerciseTrendSectionBlock(
    section: ExerciseTrendSection,
    sessions: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(TestTags.exerciseTrend("section", section.metric.name))) {
        Text(
            text = stringResource(section.metric.titleRes()),
            style = MaterialTheme.typography.titleMedium,
        )
        if (!section.metric.higherIsBetter) {
            // The assisted direction, said in words: a climbing line here means the
            // machine is doing more of the work (N17's decision).
            Text(
                text = stringResource(R.string.exercise_trends_less_is_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TestTags.EXERCISE_TRENDS_DIRECTION),
            )
        }
        Text(
            text = section.caption(sessions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TestTags.exerciseTrend("caption", section.metric.name)),
        )

        if (section.hasLine) {
            TrendChartFrame(
                values = section.values,
                minValue = section.axisMin,
                maxValue = section.axisMax,
                testTag = TestTags.exerciseTrend("chart", section.metric.name),
            )
        }
    }
}

private fun ExerciseTrendMetric.titleRes(): Int = when (this) {
    ExerciseTrendMetric.HEAVIEST_SET -> R.string.exercise_trend_heaviest
    ExerciseTrendMetric.ESTIMATED_1RM -> R.string.exercise_trend_one_rep_max
    ExerciseTrendMetric.VOLUME -> R.string.exercise_trend_volume
    ExerciseTrendMetric.TOTAL_REPS -> R.string.exercise_trend_reps
    ExerciseTrendMetric.ASSISTANCE -> R.string.exercise_trend_assistance
    ExerciseTrendMetric.RPE -> R.string.trends_metric_rpe
    ExerciseTrendMetric.MUSCLE_FEEL -> R.string.trends_metric_muscle
    ExerciseTrendMetric.JOINT_PAIN -> R.string.trends_metric_joint
}

/** The sentence under a chart: what it is now, and what it has averaged. */
@Composable
private fun ExerciseTrendSection.caption(sessions: Int): String = when {
    recorded == 0 -> stringResource(R.string.trends_not_recorded)
    recorded == 1 -> stringResource(R.string.trends_recorded_once, latest.orEmptyValue(metric))
    else -> pluralStringResource(
        R.plurals.trends_caption,
        recorded,
        latest.orEmptyValue(metric),
        average.orEmptyValue(metric),
        sessions,
    )
}

/**
 * A value as the metric is read: kilograms for a load, a count for reps, and one decimal
 * for a rating — the same shape N13's captions use, so the two screens agree.
 */
@Composable
private fun Double?.orEmptyValue(metric: ExerciseTrendMetric): String = when {
    this == null -> ""
    metric == ExerciseTrendMetric.TOTAL_REPS -> toLong().toString()
    // Stored in grams, read in kilograms: the chart's values stay in grams (they are
    // only compared with each other), but a caption is for a person.
    metric.isLoad -> stringResource(R.string.exercise_trend_kilograms, Weight.kilograms(toLong()))
    else -> asRating()
}
