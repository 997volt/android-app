package com.example.androidapp.ui.statistics

import java.time.ZoneOffset
import java.time.LocalDate
import java.time.Instant
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.AlertDialog
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
    var choosingDates by remember { mutableStateOf(false) }
    if (choosingDates) {
        CustomRangeDialog(
            initial = state.range,
            onDismiss = { choosingDates = false },
            onConfirm = { chosen ->
                choosingDates = false
                onSelectRange(chosen)
            },
        )
    }

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
            RangeChips(
                range = state.range,
                onSelectRange = onSelectRange,
                onChooseDates = { choosingDates = true },
            )
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
 * Custom asks for its dates before it applies, because the alternative is a chip that selects an unbounded
 * window and a chart that looks broken rather than empty.
 */
@Composable
private fun RangeChips(
    range: StatisticsRange,
    onSelectRange: (StatisticsRange) -> Unit,
    onChooseDates: () -> Unit,
) {
    // One source at the top level, which the Compose ruleset asks for and which reads better: the chips
    // are one control, wrapped over as many rows as they need.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RangeKind.entries
            .chunked(CHIPS_PER_ROW)
            .forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { kind ->
                        if (kind == RangeKind.CUSTOM) {
                            // Custom is a chip like the others, but choosing it asks a question first: the
                            // dates. It is selected whenever the range *is* custom, so the bar still says
                            // which window is on screen.
                            FilterChip(
                                selected = range.kind == RangeKind.CUSTOM,
                                onClick = onChooseDates,
                                label = { Text(stringResource(kind.labelRes)) },
                                modifier = Modifier.testTag(TestTags.Statistics.range(kind.name)),
                            )
                        } else {
                            RangeChip(
                                kind = kind,
                                selected = range.kind == kind,
                                onSelectRange = onSelectRange,
                            )
                        }
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

/**
 * The custom range, with its From and To (ROADMAP N35).
 *
 * Each date is chosen in a calendar of its own, and applying is refused until both are: a window with one
 * end is the same as no window, and the type's tolerance for a missing end exists so an older stored value
 * cannot break a screen, not as a state to offer.
 *
 * The dates are read back in UTC, which is the zone the picker reports in — converting them in the device's
 * zone would shift the day for anyone west of Greenwich and make "From 1 August" mean 31 July.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    initial: StatisticsRange,
    onDismiss: () -> Unit,
    onConfirm: (StatisticsRange) -> Unit,
) {
    var from by remember { mutableStateOf(initial.from) }
    var to by remember { mutableStateOf(initial.to) }
    var picking by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.range_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateRow(
                    labelRes = R.string.statistics_custom_from,
                    date = from,
                    testTag = TestTags.Statistics.CUSTOM_FROM,
                    onClick = { picking = true },
                )
                DateRow(
                    labelRes = R.string.statistics_custom_to,
                    date = to,
                    testTag = TestTags.Statistics.CUSTOM_TO,
                    onClick = { picking = false },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(StatisticsRange(RangeKind.CUSTOM, from = from, to = to)) },
                enabled = from != null && to != null,
                modifier = Modifier.testTag(TestTags.Statistics.CUSTOM_APPLY),
            ) {
                Text(stringResource(R.string.statistics_custom_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    picking?.let { choosing ->
        DatePrompt(
            initial = (if (choosing) from else to)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            onDismiss = { picking = null },
            onPick = { date ->
                if (choosing) from = date else to = date
                picking = null
            },
        )
    }
}

/**
 * One calendar, for one end of the range.
 *
 * Its own composable rather than a block inside the dialog: the dialog has a question, an answer and two
 * dates to explain, and the calendar is a screen-sized thing that happens to be nested in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePrompt(initial: Long?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/** One end of a custom range: what it is, what it currently says, and the way to change it. */
@Composable
private fun DateRow(labelRes: Int, date: LocalDate?, testTag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(testTag)) {
        Text(stringResource(labelRes) + ": " + (date?.toString() ?: stringResource(R.string.statistics_custom_no_date)))
    }
}
