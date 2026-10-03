package com.example.androidapp.ui.adherence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.androidapp.R
import com.example.androidapp.domain.model.ExerciseAdherence
import com.example.androidapp.domain.model.MonthlyRatio
import com.example.androidapp.domain.model.SlotAdherence
import com.example.androidapp.domain.model.Streak
import com.example.androidapp.ui.components.ChartPoint
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.TrendChartFrame
import com.example.androidapp.ui.history.HistoryFormat
import java.time.ZoneId

/**
 * The month's parts, and the run (ROADMAP P3.14, P3.15).
 *
 * Their own file because the screen is at the function count this project allows, and because these
 * are one subject: the counts the month is made of, read per slot, per lift, and in a row.
 */

/** One slot's share of the month (ROADMAP P3.14). */
@Composable
internal fun SlotBreakdown(rows: List<SlotAdherence>) {
    if (rows.isEmpty()) return
    BreakdownTitle(R.string.adherence_by_slot, TestTags.Adherence.BY_SLOT_TITLE)
    rows.forEach { slot ->
        BreakdownRow(
            tag = TestTags.Adherence.slotBreakdown(slot.slotId),
            name = slot.templateName,
            done = slot.done,
            skipped = slot.skipped,
            missed = slot.missed,
        )
    }
}

/** One lift's share of the month, over the slots that prescribe it (ROADMAP P3.14). */
@Composable
internal fun ExerciseBreakdown(rows: List<ExerciseAdherence>) {
    if (rows.isEmpty()) return
    BreakdownTitle(R.string.adherence_by_lift, TestTags.Adherence.BY_EXERCISE_TITLE)
    rows.forEach { exercise ->
        BreakdownRow(
            tag = TestTags.Adherence.exerciseBreakdown(exercise.exerciseId),
            name = exercise.exerciseName,
            done = exercise.done,
            skipped = exercise.skipped,
            missed = exercise.missed,
        )
    }
}

@Composable
internal fun BreakdownTitle(titleRes: Int, tag: String) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag(tag),
    )
}

/**
 * One breakdown row: the name, and the three counts.
 *
 * Counts rather than a percentage across the row (P3.14): two of three is not 67% of anything worth
 * printing, and the counts are what says *which* day keeps being skipped. Three labelled figures
 * rather than a sentence, for [Count]'s reason above: "1 done" is a plural decision this row does
 * not need to make.
 */
@Composable
internal fun BreakdownRow(tag: String, name: String, done: Int, skipped: Int, missed: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        BreakdownCount(R.string.adherence_done, done)
        BreakdownCount(R.string.adherence_skipped, skipped)
        BreakdownCount(R.string.adherence_missed, missed)
    }
}

@Composable
internal fun BreakdownCount(labelRes: Int, value: Int) {
    Column(
        modifier = Modifier.width(BREAKDOWN_COLUMN_WIDTH),
        horizontalAlignment = Alignment.End,
    ) {
        Text(value.toString(), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Wide enough for a two-digit count over its label, and no wider in a list of rows. */
internal val BREAKDOWN_COLUMN_WIDTH = 56.dp

/** The run of scheduled occurrences done in a row, and when it began (ROADMAP P3.15). */
@Composable
internal fun StreakSummary(streak: Streak) {
    val zone = ZoneId.systemDefault()
    Column(modifier = Modifier.testTag(TestTags.Adherence.STREAK)) {
        Text(
            text = if (streak.count == 0) {
                stringResource(R.string.adherence_streak_none)
            } else {
                pluralStringResource(R.plurals.adherence_streak, streak.count, streak.count)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        streak.startedOn?.let { start ->
            Text(
                text = stringResource(
                    R.string.adherence_streak_since,
                    HistoryFormat.date(start.atStartOfDay(zone).toInstant(), zone),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TestTags.Adherence.STREAK_START),
            )
        }
    }
}

/**
 * One point per month, over the same aggregate the grid draws (ROADMAP P3.16).
 *
 * The same `TrendChart` the trends screen uses, for the reason it exists: a null value is a gap in
 * the line rather than a point at the floor, so a month with nothing scored reads as *not scored*
 * instead of as a failure. Points are placed by month, which is why the x here is a plain index —
 * months are equal lengths, unlike the readings on N37's time axis.
 *
 * Hidden below two scored months: one point is not a history, and the ratio above it already says
 * what that month was.
 */
@Composable
internal fun RatioHistory(rows: List<MonthlyRatio>) {
    if (rows.count { it.ratio != null } < 2) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.adherence_history),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(TestTags.Adherence.HISTORY_TITLE),
        )
        TrendChartFrame(
            points = rows.mapIndexed { index, row ->
                ChartPoint(x = index.toFloat() / (rows.size - 1), value = row.ratio)
            },
            minValue = 0.0,
            maxValue = 1.0,
            testTag = TestTags.Adherence.HISTORY_CHART,
        )
        Text(
            text = stringResource(R.string.adherence_history_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TestTags.Adherence.HISTORY_CAPTION),
        )
    }
}
