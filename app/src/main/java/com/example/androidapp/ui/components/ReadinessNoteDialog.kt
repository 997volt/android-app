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

/**
 * The session's readiness note (ROADMAP N4).
 *
 * One dialog serves both the prompt a new workout opens with and the edit reached
 * from the workout header; [isPrompt] only changes the wording and whether the
 * secondary button reads *Skip* or *Cancel*. Both close without writing, because a
 * skippable prompt is the whole point — the field stays reachable from the header,
 * so skipping is not a dead end.
 */
@Composable
fun ReadinessNoteDialog(
    initialNote: String,
    isPrompt: Boolean,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable, so a rotation mid-sentence does not throw the note away.
    var note by rememberSaveable { mutableStateOf(initialNote) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isPrompt) R.string.readiness_prompt_title else R.string.readiness_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isPrompt) {
                    Text(
                        text = stringResource(R.string.readiness_prompt_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.READINESS_NOTE),
                    label = { Text(stringResource(R.string.readiness_label)) },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(TestTags.READINESS_SAVE),
                onClick = { onSave(note.trim().ifEmpty { null }) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(TestTags.READINESS_DISMISS),
                onClick = onDismiss,
            ) {
                Text(
                    stringResource(
                        if (isPrompt) R.string.readiness_skip else R.string.action_cancel,
                    ),
                )
            }
        },
    )
}
