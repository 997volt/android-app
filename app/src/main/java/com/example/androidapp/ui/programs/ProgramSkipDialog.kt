package com.example.androidapp.ui.programs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.androidapp.R
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.longLabel

/**
 * The point-of-start question (ROADMAP P3.3).
 *
 * *"You missed Paused Squat on Tuesday. Do it now, or continue with Bench?"* — **Do it
 * now** starts the missed day; **Continue** records a skip for every pending occurrence
 * this week and starts what was asked for.
 *
 * Dismissing cancels the start rather than choosing an answer: a prompt that picks for you
 * when you tap outside it is not a question.
 */
@Composable
fun ProgramSkipDialog(
    prompt: SkipPromptUi,
    onDoItNow: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A string resource cannot be read outside composition, so the weekday's own label is
    // resolved here and folded into the sentence.
    val day = prompt.missedWeekday.longLabel()
    val body = if (prompt.nextLabel == null) {
        stringResource(R.string.program_skip_body_plain, prompt.missedTemplateName, day)
    } else {
        stringResource(R.string.program_skip_body, prompt.missedTemplateName, day, prompt.nextLabel)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(TestTags.Programs.SKIP_PROMPT),
        title = { Text(stringResource(R.string.program_skip_title)) },
        text = {
            Text(
                buildString {
                    append(body)
                    append("\n\n")
                    append(stringResource(R.string.program_skip_note))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDoItNow,
                modifier = Modifier.testTag(TestTags.Programs.SKIP_DO_NOW),
            ) {
                Text(stringResource(R.string.program_skip_do_now))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.testTag(TestTags.Programs.SKIP_CONTINUE),
            ) {
                Text(
                    text = prompt.nextLabel?.let {
                        stringResource(R.string.program_skip_continue_with, it)
                    } ?: stringResource(R.string.program_skip_continue),
                )
            }
        },
    )
}
