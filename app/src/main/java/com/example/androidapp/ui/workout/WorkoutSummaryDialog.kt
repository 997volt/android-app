package com.example.androidapp.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.androidapp.R
import com.example.androidapp.domain.model.PlanComparison
import com.example.androidapp.domain.Weight
import com.example.androidapp.ui.components.TestTags

/**
 * The review a finished workout gets (ROADMAP N20).
 *
 * Finish used to be a dead end: the ratings the user had just given, the readiness note and
 * the totals went nowhere, and a plan was never held up against what was actually lifted.
 * The number that matters here is the last one — prescribed 6×2 at 92.5, performed 6×2 at
 * 92.5, top single 2.5 over plan — because that is the entire payoff for writing a plan.
 *
 * It also says what was *not* done: a prescribed exercise that was skipped is a fact about
 * the session, and a review that omits it is a compliment rather than a record.
 */
@Composable
fun WorkoutSummaryDialog(
    summary: WorkoutSummary,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(TestTags.SUMMARY_DIALOG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.summary_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.summary_totals,
                        summary.totalSets,
                        summary.totalReps,
                        Weight.display(summary.totalVolumeGrams, 0),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                summary.readinessNote?.let {
                    Text(stringResource(R.string.summary_readiness, it))
                }
                summary.note?.let {
                    Text(stringResource(R.string.summary_note, it))
                }
                if (summary.ratings.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.summary_ratings_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    summary.ratings.forEach { rating ->
                        val parts = buildList {
                            rating.muscleFeel?.let { add(stringResource(R.string.summary_rating_feel, it)) }
                            rating.jointPain?.let { add(stringResource(R.string.summary_rating_pain, it)) }
                        }
                        Text("${rating.name} — ${parts.joinToString(", ")}")
                    }
                }
                PlanVersusActual(summary.comparisons)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.SUMMARY_DONE),
            ) {
                Text(stringResource(R.string.summary_done))
            }
        },
    )
}

/** One exercise's plan next to what happened, as a sentence. */
@Composable
private fun PlanComparisonRow(comparison: PlanComparison) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(comparison.name, style = MaterialTheme.typography.bodyMedium)
        val prescribed = prescribedText(comparison)
        val performed = performedText(comparison)
        if (prescribed != null) {
            Text(
                text = stringResource(R.string.summary_prescribed, prescribed),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = stringResource(R.string.summary_performed, performed),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = verdictText(comparison),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** `6×2 at 92.5 kg`, or null when the exercise was not prescribed at all. */
@Composable
private fun prescribedText(comparison: PlanComparison): String? {
    if (!comparison.wasPlanned) return null
    val reps = comparison.prescribedReps
    val weight = comparison.prescribedTopWeightGrams
    val shape = if (reps != null) {
        stringResource(R.string.summary_shape, comparison.prescribedSets, reps)
    } else {
        null
    }
    return when {
        shape != null && weight != null ->
            stringResource(R.string.summary_shape_weight, shape, Weight.display(weight, 0))

        shape != null -> shape
        weight != null -> Weight.display(weight, 0)
        else -> stringResource(R.string.summary_sets_only, comparison.prescribedSets)
    }
}

/** `6×2 at 92.5 kg`, or "not performed" when the user never touched it. */
@Composable
private fun performedText(comparison: PlanComparison): String {
    if (comparison.performedSets == 0) return stringResource(R.string.summary_not_performed)
    val weight = comparison.performedTopWeightGrams
    val shape = stringResource(R.string.summary_shape, comparison.performedSets, comparison.performedReps)
    return if (weight != null && weight > 0L) {
        stringResource(R.string.summary_shape_weight, shape, Weight.display(weight, 0))
    } else {
        shape
    }
}

/** The line the whole screen exists for: how the top set landed. */
@Composable
private fun verdictText(comparison: PlanComparison): String = when {
    !comparison.wasPlanned -> stringResource(R.string.summary_not_planned)
    comparison.performedSets == 0 -> stringResource(R.string.summary_skipped)
    comparison.topSetDeltaGrams == null -> stringResource(R.string.summary_no_weight_to_compare)
    comparison.matchedPlan -> stringResource(R.string.summary_matched)
    comparison.topSetDeltaGrams > 0L ->
        stringResource(R.string.summary_over, Weight.display(comparison.topSetDeltaGrams, 0))
    else ->
        stringResource(R.string.summary_under, Weight.display(-comparison.topSetDeltaGrams, 0))
}

/** The section the screen exists for: the plan, the performance, and the difference (N20, B21). */
@Composable
private fun PlanVersusActual(
    comparisons: List<PlanComparison>,
    modifier: Modifier = Modifier,
) {
    if (comparisons.isEmpty()) return
    Column(modifier = modifier) {
        HorizontalDivider()
        Text(
            text = stringResource(R.string.summary_plan_title),
            style = MaterialTheme.typography.titleSmall,
        )
        // The counts and the rep sums both leave warm-ups out, so the dialog says so rather than
        // leaving the reader to infer it (ROADMAP B21).
        Text(
            text = stringResource(R.string.summary_warmup_note),
            style = MaterialTheme.typography.bodySmall,
        )
        comparisons.forEach { PlanComparisonRow(it) }
    }
}
