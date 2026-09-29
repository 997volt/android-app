package com.example.androidapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.androidapp.R
import com.example.androidapp.domain.DataError

/**
 * Names a new custom exercise (ROADMAP N2).
 *
 * Creation asks for the name only — the taxonomy fields are non-nullable on disk,
 * so an unedited custom exercise is stored as unspecified and filled in later
 * from the detail screen. The hint says that rather than leaving the user to
 * wonder why there is one field.
 *
 * Add stays disabled until the name is non-blank: a nameless library row would be
 * unusable, and there is no sensible "unspecified" name to fall back to.
 */
@Composable
fun NewExerciseDialog(
    error: DataError?,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable, so rotating the device mid-type does not throw the name away.
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exercise_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.NEW_EXERCISE_NAME),
                    singleLine = true,
                    label = { Text(stringResource(R.string.exercise_name_label)) },
                )
                Text(
                    text = stringResource(R.string.exercise_new_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { failure ->
                    Text(
                        text = dataErrorMessage(failure),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(TestTags.NEW_EXERCISE_SAVE),
                enabled = trimmed.isNotEmpty(),
                onClick = { onCreate(trimmed) },
            ) {
                Text(stringResource(R.string.exercise_new_add))
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(TestTags.NEW_EXERCISE_CANCEL),
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
