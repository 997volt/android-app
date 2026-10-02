package com.example.androidapp.ui.statistics

import com.example.androidapp.ui.components.ChartLine
import com.example.androidapp.ui.components.ChartPoint
import java.time.ZoneId
import com.example.androidapp.ui.history.HistoryFormat
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import com.example.androidapp.domain.model.Exercise
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
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.components.TrendChartFrame

/** The Statistics tab (ROADMAP N35). */
@Composable
fun StatisticsRoute(
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel(),
    onOpenMeasurements: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsScreen(
        state = state,
        onSelectRange = viewModel::onSelectRange,
        onSelectMetric = viewModel::onSelectMetric,
        modifier = modifier,
        onSelectExercise = viewModel::onSelectExercise,
        onSetGoal = viewModel::onSetGoal,
        onOpenMeasurements = onOpenMeasurements,
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
    onSelectExercise: (String) -> Unit = {},
    /** Sets or clears the chosen metric's target (ROADMAP N39). */
    onSetGoal: (Double?) -> Unit = {},
    /**
     * Opens body measurements (ROADMAP N35).
     *
     * Their charts are picker entries now, but **recording** an entry is a different job from reading a
     * trend, so the screen stays a pushed destination — and this action is what keeps it reachable, which
     * it stopped being the moment the trends screen that used to link to it was deleted.
     */
    onOpenMeasurements: (() -> Unit)? = null,
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
        topBar = { StatisticsTopBar(onOpenMeasurements = onOpenMeasurements) },
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

            // The overview and the chart both read the series, so neither is drawn until it exists: a frame
            // that printed "0 workouts · 0 kg" while loading was making a claim about data it had not read
            // yet, and the same zeros appeared behind a failed read with no message at all.
            when {
                state.isLoading -> CenteredMessage(
                    text = stringResource(R.string.statistics_loading),
                    showSpinner = true,
                )

                state.error != null -> CenteredMessage(text = dataErrorMessage(state.error))

                else -> StatisticsBody(
                    state = state,
                    series = state.series,
                    onSelectExercise = onSelectExercise,
                    onSetGoal = onSetGoal,
                    goal = state.goal,
                )
            }
        }
    }
}

/**
 * What the screen shows once the series has been read.
 *
 * Split out so the loading and error frames above are a decision about the *state* rather than a set of
 * conditions threaded through the body.
 */
@Composable
private fun StatisticsBody(
    state: StatisticsUiState,
    series: com.example.androidapp.ui.statistics.MetricSeries?,
    onSelectExercise: (String) -> Unit,
    onSetGoal: (Double?) -> Unit,
    goal: Double?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Overview(overview = state.overview)

        // The picker is drawn whenever the metric is about a lift, not only when nothing is chosen yet.
        // Gating it on "no lift selected" made it unreachable: every entry point arrives with one selected,
        // so the lift could never be changed, and the picker's own selected-name branch was dead code.
        if (state.metricNeedsExercise) {
            if (state.needsExercise) {
                Text(
                    text = stringResource(R.string.statistics_choose_lift),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TestTags.Statistics.CHOOSE_LIFT),
                )
            }
            LiftPicker(
                lifts = state.lifts,
                selectedId = state.selection.exerciseId,
                onSelectExercise = onSelectExercise,
            )
        }

        series?.let { series ->
                val entry = MetricRegistry.entryFor(series.key)
                SeriesChart(series = series, metric = entry, goal = state.goal)
                // Under the chart, and the literal answer to "see everything" — which is also the accessible
                // counterpart to a canvas this app blanks out for screen readers (ROADMAP N36).
                ReadingsSection(series = series, metric = entry)
                GoalRow(goal = state.goal, metric = entry, onSetGoal = onSetGoal)
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

/**
 * The readings, newest first, with the average on top (ROADMAP N36).
 *
 * Collapsed by default: the chart is what the screen is for, and a year of readings is a wall of numbers to
 * anyone who has not asked for one. Nothing at all when there is nothing recorded — a disclosure control
 * that opens onto emptiness is worse than no control.
 */
@Composable
private fun ReadingsSection(series: MetricSeries, metric: MetricEntry) {
    val readings = series.asReadings()
    if (readings.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag(TestTags.Statistics.READINGS_TOGGLE),
        ) {
            Text(stringResource(R.string.statistics_readings, readings.size))
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                // The label beside it already says what this is; a description would make TalkBack repeat it.
                contentDescription = null,
            )
        }

        if (expanded) {
            series.average()?.let { average ->
                ReadingRow(
                    label = stringResource(R.string.statistics_average),
                    value = metric.unit.format(average),
                    testTag = TestTags.Statistics.READINGS_AVERAGE,
                )
            }
            // The Trend row the roadmap puts beside the average (N36, N39). It arrives with the fitted line
            // rather than before it: a slope is a reading of the series, and there was nothing to read.
            series.trend()?.let { trend ->
                ReadingRow(
                    label = stringResource(R.string.statistics_trend),
                    value = stringResource(
                        R.string.statistics_per_week,
                        slopeText(trend.perWeek) { metric.unit.formatRate(it) },
                        stringResource(metric.unit.labelRes),
                    ),
                    testTag = TestTags.Statistics.READINGS_TREND,
                )
            }
            readings.forEachIndexed { index, reading ->
                ReadingRow(
                    label = HistoryFormat.date(reading.at, zone = ZoneId.systemDefault()),
                    value = metric.unit.format(reading.value),
                    testTag = TestTags.Statistics.reading(index),
                )
            }
        }
    }
}

/** One line of the list: what it is on the left, what it reads on the right. */
@Composable
private fun ReadingRow(label: String, value: String, testTag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The series, drawn through the chart every other trend uses. N37 gives it a time axis. */
@Composable
private fun SeriesChart(series: MetricSeries, metric: MetricEntry, goal: Double?) {
    val recorded = series.readings.mapNotNull { it.value }
    if (recorded.size < 2) return
    var period by rememberSaveable { mutableStateOf(DAYS) }

    // `fixedRange` keeps a rating on its own 1–10 scale rather than fitting the axis to a 0.2 wobble.
    val axis = axisBounds(recorded, fromZero = metric.fromZero, goal = goal, fixedRange = metric.fixedRange)
    val firstAt = series.readings.first().at.toEpochMilli().toDouble()
    val timeSpan = series.readings.last().at.toEpochMilli().toDouble() - firstAt

    Column {
        val trend = series.trend()
        val projected = projectByTime(series.readings)
        // The mean is a subset of the same readings, so it shares their places on the axis rather than being
        // projected again — two projections of the same instants would be two chances to disagree.
        val xByInstant = series.readings.mapIndexed { index, reading -> reading.at to projected[index].x }.toMap()
        val mean = series.movingAverage(period).mapNotNull { reading ->
            xByInstant[reading.at]?.let { x -> ChartPoint(x = x, value = reading.value) }
        }
        TrendChartFrame(
            points = projected,
            goal = goal,
            minValue = axis.min,
            maxValue = axis.max,
            testTag = TestTags.Statistics.CHART,
            bars = metric.isBars,
            average = series.average(),
            // The line is evaluated across the same elapsed time the points are placed by (N37), so it leans
            // the way the readings do rather than the way the index would.
            movingAverage = mean,
            trend = trend?.let { fitted ->
                ChartLine(
                    start = fitted.valueAt(fraction = 0.0, first = firstAt, span = timeSpan),
                    end = fitted.valueAt(fraction = 1.0, first = firstAt, span = timeSpan),
                )
            },
        )
        MovingAveragePicker(period = period, onSelect = { period = it })

        // The axis in words (ROADMAP N37). A canvas cannot be read by a screen reader, and even for a
        // reader who can see it, "when did this start and end, and what scale is it" is the first question.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = HistoryFormat.date(firstRecordedAt(series), zone = ZoneId.systemDefault()),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag(TestTags.Statistics.CHART_FIRST_DATE),
            )
            Text(
                text = HistoryFormat.date(lastRecordedAt(series), zone = ZoneId.systemDefault()),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag(TestTags.Statistics.CHART_LAST_DATE),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = metric.unit.format(axis.min),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag(TestTags.Statistics.CHART_MIN),
            )
            Text(
                text = metric.unit.format(axis.max),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag(TestTags.Statistics.CHART_MAX),
            )
        }
    }
}

/** The first moment that recorded something, which is where the axis starts. */
private fun firstRecordedAt(series: MetricSeries): Instant =
    series.readings.first { it.value != null }.at

private fun lastRecordedAt(series: MetricSeries): Instant =
    series.readings.last { it.value != null }.at

/** The ends of the axis, so the chart can be read without the readings list. */



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

/**
 * Which lift an Exercise metric is about (ROADMAP N35).
 *
 * Only shown when the metric needs one, and only the library's names: the same list the library screen
 * offers, because a second way to name a lift would be a second thing to keep in step.
 */
@Composable
private fun LiftPicker(lifts: List<Exercise>, selectedId: String?, onSelectExercise: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = lifts.firstOrNull { it.id == selectedId }

    Column {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.testTag(TestTags.Statistics.LIFT),
        ) {
            Text(selected?.name ?: stringResource(R.string.statistics_choose_lift))
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            lifts.forEach { lift ->
                DropdownMenuItem(
                    text = { Text(lift.name) },
                    onClick = {
                        open = false
                        onSelectExercise(lift.id)
                    },
                    modifier = Modifier.testTag(TestTags.Statistics.lift(lift.id)),
                )
            }
        }
    }
}

/** The screen's bar: a title, and the way to the one screen it pushes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsTopBar(onOpenMeasurements: (() -> Unit)?) {
    TopAppBar(
        title = { Text(stringResource(R.string.tab_statistics)) },
        actions = {
            if (onOpenMeasurements != null) {
                TextButton(
                    onClick = onOpenMeasurements,
                    modifier = Modifier.testTag(TestTags.Statistics.MEASUREMENTS),
                ) {
                    Text(stringResource(R.string.measurements_title))
                }
            }
        },
    )
}

/**
 * The period the trailing mean is taken over (ROADMAP N40).
 *
 * Readings rather than days, stated on the control rather than only in the code: a day window would often hold
 * one reading for a lift trained twice a week, and the mean of one reading is that reading.
 */
@Composable
private fun MovingAveragePicker(period: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.statistics_moving_average),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag(TestTags.Statistics.MOVING_AVERAGE),
        )
        MOVING_AVERAGE_PERIODS.forEach { option ->
            FilterChip(
                selected = period == option,
                onClick = { onSelect(option) },
                label = { Text(option.toString()) },
                modifier = Modifier.testTag(TestTags.Statistics.movingAveragePeriod(option)),
            )
        }
    }
}
