package com.example.androidapp.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.RangeKind
import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.TrendChartFrame

/** The Statistics tab (ROADMAP N35). */
@Composable
fun StatisticsRoute(
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsScreen(
        state = state,
        onSelectRange = viewModel::onSelectRange,
        onSelectMetric = viewModel::onSelectMetric,
        modifier = modifier,
    )
}

/**
 * One chart with a picker over every series, the range it covers, and three numbers about it.
 *
 * A tab root, so it has no back arrow: the shell's bar is the way out, and a root that offered "back" would
 * be offering to go somewhere the user did not come from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onSelectRange: (StatisticsRange) -> Unit,
    onSelectMetric: (MetricKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_statistics)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RangeChips(range = state.range, onSelectRange = onSelectRange)
            MetricPicker(selected = state.selection.metric, onSelectMetric = onSelectMetric)
            Overview(overview = state.overview)

            if (state.needsExercise) {
                Text(
                    text = stringResource(R.string.statistics_choose_lift),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TestTags.Statistics.CHOOSE_LIFT),
                )
            }

            state.series?.let { SeriesChart(series = it) }
        }
    }
}

/**
 * The range, as chips.
 *
 * `CUSTOM` is not among them yet: choosing dates is a dialog of its own, and a chip that selected an
 * unbounded window would be a control that does not do what it says. The type and the store already carry
 * it, so the chip is a UI piece rather than a change to either.
 */
@Composable
private fun RangeChips(range: StatisticsRange, onSelectRange: (StatisticsRange) -> Unit) {
    // One source at the top level, which the Compose ruleset asks for and which reads better: the chips
    // are one control, wrapped over as many rows as they need.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RangeKind.entries
            .filter { it != RangeKind.CUSTOM }
            .chunked(CHIPS_PER_ROW)
            .forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { kind ->
                        RangeChip(kind = kind, selected = range.kind == kind, onSelectRange = onSelectRange)
                    }
                }
            }
    }
}

/** Six fixed ranges, two rows of three — which is the layout rather than a rule about ranges. */
private const val CHIPS_PER_ROW = 3

@Composable
private fun RangeChip(kind: RangeKind, selected: Boolean, onSelectRange: (StatisticsRange) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onSelectRange(StatisticsRange(kind)) },
        label = { Text(stringResource(kind.labelRes)) },
        modifier = Modifier.testTag(TestTags.Statistics.range(kind.name)),
    )
}

/** One picker over every series, grouped the way the registry groups them (ROADMAP N35). */
@Composable
private fun MetricPicker(selected: MetricKey, onSelectMetric: (MetricKey) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selectedEntry = MetricRegistry.entryFor(selected)

    Column {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.testTag(TestTags.Statistics.METRIC),
        ) {
            Text(
                text = stringResource(R.string.statistics_metric) + ": " +
                    stringResource(selectedEntry.labelRes),
            )
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        MetricGroup.entries.forEach { group ->
            MetricRegistry.entries.filter { it.group == group }.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.labelRes)) },
                    onClick = {
                        open = false
                        onSelectMetric(entry.key)
                    },
                    modifier = Modifier.testTag(TestTags.Statistics.metric(entry.key.id)),
                    )
                }
            }
        }
    }
}

/** The three numbers, and no more (ROADMAP N35). */
@Composable
private fun Overview(overview: StatisticsOverview) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Number(
            labelRes = R.string.statistics_overview_workouts,
            value = overview.workouts.toString(),
            testTag = TestTags.Statistics.OVERVIEW_WORKOUTS,
        )
        Number(
            labelRes = R.string.statistics_overview_volume,
            value = Weight.kilograms(overview.volumeGrams),
            testTag = TestTags.Statistics.OVERVIEW_VOLUME,
        )
        Number(
            labelRes = R.string.statistics_overview_records,
            // A dash rather than a zero: "not counted yet" and "you set none" are different statements
            // (see StatisticsOverview.personalRecords).
            value = overview.personalRecords?.toString() ?: stringResource(R.string.statistics_not_counted),
            testTag = TestTags.Statistics.OVERVIEW_RECORDS,
        )
    }
}

@Composable
private fun Number(labelRes: Int, value: String, testTag: String) {
    Column {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag(testTag))
    }
}

/** The series, drawn through the chart every other trend uses. N37 gives it a time axis. */
@Composable
private fun SeriesChart(series: MetricSeries) {
    val readings = series.readings.mapNotNull { it.value }
    if (readings.size < 2) return

    val span = readings.max() - readings.min()
    val pad = if (span == 0.0) 1.0 else span * AXIS_PAD_FRACTION
    TrendChartFrame(
        values = series.readings.map { it.value },
        minValue = readings.min() - pad,
        maxValue = readings.max() + pad,
        testTag = TestTags.Statistics.CHART,
    )
}

/** Room above and below the line, so a reading at the top edge is not mistaken for a ceiling. */
private const val AXIS_PAD_FRACTION = 0.1
