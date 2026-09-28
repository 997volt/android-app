package com.example.androidapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.set_weight_label)) },
                )
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.set_reps_label)) },
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
