package com.example.androidapp.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.androidapp.R
import com.example.androidapp.ui.components.TestTags

/**
 * The target line, and the one place a target is set (ROADMAP N39).
 *
 * Beside the average and the trend because it is the same kind of thing — a line about the series rather than
 * a reading in it — and editable here because a target nobody can set is a decoration. The value is typed in
 * the metric's displayed unit and parsed into its stored one, so a target of 80 kilograms is 80000 grams like
 * every other weight in the app.
 */
@Composable
fun GoalRow(
    goal: Double?,
    metric: MetricEntry,
    onSetGoal: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.Statistics.GOAL),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.statistics_goal),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = {
                typed = goal?.let { metric.unit.format(it) }.orEmpty()
                editing = true
            },
            modifier = Modifier.testTag(TestTags.Statistics.GOAL_SET),
        ) {
            Text(
                text = goal?.let { metric.unit.format(it) }
                    ?: stringResource(R.string.statistics_goal_none),
            )
        }
    }

    if (editing) {
        GoalDialog(
            metric = metric,
            goal = goal,
            onSet = { value ->
                onSetGoal(value)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * The dialog a target is typed into (ROADMAP N39).
 *
 * Its own state, seeded from the current target: the text is about this editing session, and keeping it here
 * is what lets the row stay a row.
 */
@Composable
private fun GoalDialog(metric: MetricEntry, goal: Double?, onSet: (Double?) -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf(goal?.let { metric.unit.format(it) }.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.statistics_goal)) },
        text = {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                singleLine = true,
                suffix = { Text(stringResource(metric.unit.labelRes)) },
                modifier = Modifier.testTag(TestTags.Statistics.GOAL_FIELD),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // A number that cannot be read sets nothing rather than a zero: the field keeps what was
                    // typed so the mistake is visible instead of silently becoming a target.
                    metric.unit.parse(typed)?.let(onSet)
                },
                modifier = Modifier.testTag(TestTags.Statistics.GOAL_CONFIRM),
            ) {
                Text(stringResource(R.string.statistics_goal_confirm))
            }
        },
        dismissButton = {
            if (goal != null) {
                TextButton(
                    onClick = { onSet(null) },
                    modifier = Modifier.testTag(TestTags.Statistics.GOAL_CLEAR),
                ) {
                    Text(stringResource(R.string.statistics_goal_clear))
                }
            }
        },
    )
}
