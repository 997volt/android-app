package com.example.androidapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/**
 * The confirmation for starting over (ROADMAP N18).
 *
 * Three things are deliberate. The **export comes first and is part of the dialog**, not a
 * courtesy beside it: platform backup is off, so a file the user makes is the only thing
 * that can survive this action. The confirmation is **typed**, not a second button —
 * "Delete everything" is pressed by accident exactly once, and a word is the difference
 * between a decision and a slip. And what cannot be undone is **said before the button is
 * enabled**, rather than in the aftermath.
 */
@Composable
fun ClearEverythingDialog(
    onExport: (() -> Unit)?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val required = stringResource(R.string.clear_confirm_word)
    var typed by rememberSaveable { mutableStateOf("") }
    // Case-insensitive and trimmed: the point is a deliberate act, not a spelling test.
    val confirmed = typed.trim().equals(required, ignoreCase = true)

    AlertDialog(
        modifier = modifier.testTag(TestTags.HOME_CLEAR_DATA),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.clear_body))
                Text(
                    text = stringResource(R.string.clear_cannot_undo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (onExport != null) {
                    TextButton(
                        onClick = onExport,
                        modifier = Modifier.testTag(TestTags.CLEAR_EXPORT_FIRST),
                    ) {
                        Text(stringResource(R.string.clear_export_first))
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.testTag(TestTags.CLEAR_CONFIRM_FIELD),
                    singleLine = true,
                    label = { Text(stringResource(R.string.clear_confirm_label, required)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmed,
                modifier = Modifier.testTag(TestTags.CLEAR_CONFIRM_ACTION),
            ) {
                Text(
                    text = stringResource(R.string.clear_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.CLEAR_CANCEL),
            ) {
                Text(stringResource(R.string.clear_cancel))
            }
        },
    )
}
