package com.example.androidapp.ui.adherence

import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.example.androidapp.domain.model.DayOccurrence
import com.example.androidapp.domain.model.OccurrenceState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.model.MonthAdherence
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.components.shortLabel
import com.example.androidapp.ui.history.HistoryFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/** Adherence, reached from Statistics (ROADMAP P3.5). */
@Composable
fun AdherenceRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdherenceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdherenceScreen(
        state = state,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onToggleDeload = viewModel::onToggleDeload,
        onSelectDay = viewModel::onSelectDay,
        onDismissDay = viewModel::onDismissDay,
        onSkipToggle = viewModel::onSetSkipped,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * How often the scheduled days happened, a month of days trained, and the weeks marked as a deload
 * (ROADMAP P3.5, P3.10).
 *
 * A pushed destination rather than a sixth tab (N34): it answers "how is everything going"
 * about one thing, and Statistics is the tab that asks that question.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdherenceScreen(
    state: AdherenceUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleDeload: (String, LocalDate, Boolean) -> Unit = { _, _, _ -> },
    onSelectDay: (LocalDate) -> Unit = {},
    onDismissDay: () -> Unit = {},
    onSkipToggle: (DayOccurrence, Boolean) -> Unit = { _, _ -> },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adherence_title)) },
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MonthHeader(
                month = state.month,
                canGoForward = state.canGoForward,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )

            when {
                state.isLoading -> CenteredMessage(
                    text = stringResource(R.string.adherence_loading),
                    showSpinner = true,
                )

                state.error != null -> CenteredMessage(text = dataErrorMessage(state.error))

                else -> AdherenceBody(
                    state = state,
                    onToggleDeload = onToggleDeload,
                    onSelectDay = onSelectDay,
                )
            }
        }
    }

    // What the tapped day scheduled, with each occurrence's state (ROADMAP P3.13).
    state.day?.let { correction ->
        DayCorrectionDialog(
            correction = correction,
            onSkipToggle = onSkipToggle,
            onDismiss = onDismissDay,
        )
    }
}

/**
 * What one day scheduled, and what can be said about it (ROADMAP P3.13).
 *
 * One row per occurrence, because a day can schedule two (two slots on one Tuesday) and the ratio
 * counts occurrences rather than days (P3.5). A row the app will not correct has no control: a
 * finished session is the record, a future day cannot be skipped, and a deload week is not scored.
 */
@Composable
private fun DayCorrectionDialog(
    correction: DayCorrection,
    onSkipToggle: (DayOccurrence, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(TestTags.Adherence.DAY_DIALOG),
        title = {
            Text(HistoryFormat.date(correction.date.atStartOfDay(zone).toInstant(), zone))
        },
        text = {
            if (correction.occurrences.isEmpty()) {
                Text(
                    text = stringResource(R.string.adherence_day_nothing),
                    modifier = Modifier.testTag(TestTags.Adherence.DAY_EMPTY),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    correction.occurrences.forEach { occurrence ->
                        DayOccurrenceRow(occurrence = occurrence, onSkipToggle = onSkipToggle)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.Adherence.DAY_CLOSE),
            ) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun DayOccurrenceRow(
    occurrence: DayOccurrence,
    onSkipToggle: (DayOccurrence, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.Adherence.dayOccurrence(occurrence.slotId)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(occurrence.templateName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(occurrence.state.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (occurrence.canCorrect) {
            FilterChip(
                selected = occurrence.state == OccurrenceState.SKIPPED,
                onClick = { onSkipToggle(occurrence, occurrence.state != OccurrenceState.SKIPPED) },
                label = { Text(stringResource(R.string.adherence_skipped)) },
                modifier = Modifier.testTag(TestTags.Adherence.daySkipped(occurrence.slotId)),
            )
        }
    }
}

/** How one occurrence's state reads on the correction row (ROADMAP P3.13). */
private fun OccurrenceState.labelRes(): Int = when (this) {
    OccurrenceState.DONE -> R.string.adherence_state_done
    OccurrenceState.SKIPPED -> R.string.adherence_state_skipped
    OccurrenceState.MISSED -> R.string.adherence_state_missed
    OccurrenceState.PENDING -> R.string.adherence_state_pending
    OccurrenceState.DELOAD -> R.string.adherence_state_deload
}

/** The month and the two ways out of it. Forward stops at the current month. */
@Composable
private fun MonthHeader(
    month: YearMonth,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPreviousMonth,
            modifier = Modifier.testTag(TestTags.Adherence.PREVIOUS_MONTH),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.adherence_previous_month),
            )
        }
        Text(
            text = HistoryFormat.month(month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(TestTags.Adherence.MONTH),
        )
        IconButton(
            onClick = onNextMonth,
            // No month after this one has happened, so there is nothing there to see.
            enabled = canGoForward,
            modifier = Modifier.testTag(TestTags.Adherence.NEXT_MONTH),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.adherence_next_month),
            )
        }
    }
}

/**
 * The ratio, or the honest sentence that stands in for it.
 *
 * Null is two different facts — no program was followed, or the program scheduled nothing
 * elapsed this month — and they are worded apart because only one of them is the user's to fix.
 */
@Composable
private fun AdherenceBody(
    state: AdherenceUiState,
    onToggleDeload: (String, LocalDate, Boolean) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
) {
    val ratio = state.adherence.ratio
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (ratio == null) {
            Text(
                text = stringResource(
                    if (state.hasActiveProgram) {
                        R.string.adherence_nothing_scheduled
                    } else {
                        R.string.adherence_no_program
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(TestTags.Adherence.NO_RATIO),
            )
        } else {
            RatioSummary(adherence = state.adherence, ratio = ratio)
        }

        // A number with its start, never a nudge: the app has no notifications, and a streak that
        // pushes is a coach (ROADMAP P3.15).
        state.streak?.let { StreakSummary(streak = it) }

        Calendar(month = state.month, adherence = state.adherence, onSelectDay = onSelectDay)

        // The same counts, read per slot and per lift (ROADMAP P3.14). Empty with no active
        // program, for the ratio's reason: the pins carry no skip record to break down.
        SlotBreakdown(rows = state.adherence.bySlot)
        ExerciseBreakdown(rows = state.adherence.byExercise)

        DeloadWeeks(
            programs = state.programs,
            weeks = state.markableWeeks,
            deloadWeeks = state.deloadWeeks,
            onToggle = onToggleDeload,
        )
    }
}

/**
 * The started weeks of the shown month, each markable as a deload (ROADMAP P3.10).
 *
 * One chip per active program, labelled by name when there is more than one: a deload is keyed by
 * program and week, so with two programs followed the toggle has to say whose week it is. A week
 * that has not started is not offered at all, because the app has no forward view to hang it on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeloadWeeks(
    programs: List<WorkoutProgram>,
    weeks: List<LocalDate>,
    deloadWeeks: Map<String, Set<LocalDate>>,
    onToggle: (String, LocalDate, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (programs.isEmpty() || weeks.isEmpty()) return

    val zone = ZoneId.systemDefault()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.adherence_deload_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(TestTags.Adherence.DELOAD_TITLE),
        )
        Text(
            text = stringResource(R.string.adherence_deload_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        R.string.adherence_deload_week,
                        HistoryFormat.date(week.atStartOfDay(zone).toInstant(), zone),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    programs.forEach { program ->
                        val marked = week in deloadWeeks[program.id].orEmpty()
                        FilterChip(
                            selected = marked,
                            onClick = { onToggle(program.id, week, !marked) },
                            label = {
                                Text(
                                    if (programs.size == 1) {
                                        stringResource(R.string.adherence_deload_mark)
                                    } else {
                                        program.name
                                    },
                                )
                            },
                            modifier = Modifier.testTag(
                                TestTags.Adherence.deloadWeek(week.toString(), program.id),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** The weekday headings and the month, kept together because neither means anything alone. */
@Composable
private fun Calendar(month: YearMonth, adherence: MonthAdherence, onSelectDay: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WeekdayHeader()
        CalendarGrid(month = month, adherence = adherence, onSelectDay = onSelectDay)
    }
}

/** The number the feature exists for, and the three counts it is made of. */
@Composable
private fun RatioSummary(adherence: MonthAdherence, ratio: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.adherence_ratio, (ratio * PERCENT).roundToInt()),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(TestTags.Adherence.RATIO),
        )
        Text(
            text = stringResource(R.string.adherence_summary, adherence.scored),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(TestTags.Adherence.SUMMARY),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Count(R.string.adherence_done, adherence.done, TestTags.Adherence.DONE)
            Count(R.string.adherence_skipped, adherence.skipped, TestTags.Adherence.SKIPPED)
            Count(R.string.adherence_missed, adherence.missed, TestTags.Adherence.MISSED)
        }
    }
}

@Composable
private fun Count(labelRes: Int, value: Int, testTag: String) {
    Column {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.shortLabel(),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The month, one row of seven at a time. */
@Composable
private fun CalendarGrid(month: YearMonth, adherence: MonthAdherence, onSelectDay: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        calendarCells(month, adherence).chunked(DAYS_IN_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        onSelect = onSelectDay,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One day: trained, scheduled, or rest.
 *
 * The two marks are independent, because a scheduled day can have been trained and a rest day
 * can have been trained anyway. The accessible label says which, since a filled circle is not
 * something a screen reader can report.
 */
@Composable
private fun DayCell(day: CalendarDay?, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    if (day == null) {
        // A blank pad: it keeps the columns aligned and carries nothing to announce.
        Box(modifier = modifier.aspectRatio(1f))
        return
    }

    val zone = ZoneId.systemDefault()
    val dateLabel = HistoryFormat.date(day.date.atStartOfDay(zone).toInstant(), zone)
    val description = stringResource(
        when {
            day.trained -> R.string.adherence_day_trained
            day.scheduled -> R.string.adherence_day_scheduled
            else -> R.string.adherence_day_rest
        },
        dateLabel,
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
                if (day.trained) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .then(
                if (day.scheduled && !day.trained) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                } else {
                    Modifier
                },
            )
            .testTag(TestTags.Adherence.dayCell(day.date.toString()))
            // A scheduled day is the way into the correction dialog; a rest day has nothing to
            // correct, so it is not offered as a control (ROADMAP P3.13).
            .clickable(enabled = day.scheduled) { onSelect(day.date) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** A ratio as the percentage the screen shows: hundredths, so `0.75` reads as `75`. */
private const val PERCENT = 100
