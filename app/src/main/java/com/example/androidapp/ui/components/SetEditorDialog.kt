package com.example.androidapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.androidapp.R
import com.example.androidapp.domain.Weight

/**
 * Edits one set's reps and weight.
 *
 * Shared between the live workout screen and the history detail screen (P1.7),
 * because correcting a set you just logged and correcting one from last week are
 * the same edit. It was private to the workout screen until history needed it.
 *
 * Save stays disabled while either field is not a usable value — [Weight.parseKilograms]
 * returns null rather than guessing, so a typo cannot be written as a real set.
 */
@Composable
fun SetEditorDialog(
    initialReps: Int,
    initialWeightGrams: Long,
    onDismiss: () -> Unit,
    onSave: (reps: Int, weightGrams: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var repsText by remember { mutableStateOf(initialReps.toString()) }
    var weightText by remember { mutableStateOf(Weight.kilograms(initialWeightGrams)) }

    val parsedReps = repsText.toIntOrNull()?.takeIf { it > 0 }
    val parsedWeight = Weight.parseKilograms(weightText)

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberStepper(
                    label = stringResource(R.string.set_weight_label),
                    testTag = TestTags.SET_WEIGHT_FIELD,
                    value = weightText,
                    onValueChange = { weightText = it },
                    keyboardType = KeyboardType.Decimal,
                    // Wired to Weight.step, which had tests and no callers: the
                    // clamp at zero lives there rather than being re-implemented.
                    onStep = { delta ->
                        val from = parsedWeight ?: 0L
                        weightText = Weight.kilograms(
                            Weight.step(from, delta * Weight.DEFAULT_STEP_GRAMS),
                        )
                    },
                )
                NumberStepper(
                    label = stringResource(R.string.set_reps_label),
                    testTag = TestTags.SET_REPS_FIELD,
                    value = repsText,
                    onValueChange = { repsText = it },
                    keyboardType = KeyboardType.Number,
                    // A set of zero reps is not a set, so this floor is 1 rather
                    // than 0 — unlike weight, where 0 is meaningful (bodyweight).
                    onStep = { delta ->
                        repsText = ((parsedReps ?: 1) + delta).coerceAtLeast(1).toString()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedReps != null && parsedWeight != null,
                onClick = { onSave(parsedReps ?: 0, parsedWeight ?: 0L) },
            ) {
                Text(stringResource(R.string.set_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.set_cancel)) }
        },
    )
}

/**
 * A labelled number field with a −/+ pair either side (ROADMAP P1.3a).
 *
 * The text is the single source of truth: the steppers rewrite it rather than
 * holding a parallel numeric state, so a value the user typed and a value they
 * stepped cannot disagree. Typing stays unrestricted, which is why the keyboard is
 * a *hint* (`KeyboardType`) and validation happens on save.
 *
 * Both buttons are text glyphs carrying an explicit `contentDescription`: an
 * `Icon(…, contentDescription = …)` inside a merged `IconButton` did not answer to
 * `onNodeWithContentDescription`, and material-icons-core ships no `Remove` glyph
 * anyway. Two characters beat an icon-artifact dependency.
 */
@Composable
private fun NumberStepper(
    label: String,
    testTag: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val decreaseDescription = stringResource(R.string.set_stepper_decrease, label)
    val increaseDescription = stringResource(R.string.set_stepper_increase, label)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(glyph = "\u2212", description = decreaseDescription, onClick = { onStep(-1) })
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).testTag(testTag),
            singleLine = true,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        StepButton(glyph = "+", description = increaseDescription, onClick = { onStep(1) })
    }
}

@Composable
private fun StepButton(glyph: String, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}
