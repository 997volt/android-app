package com.example.androidapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.androidapp.R
import com.example.androidapp.domain.model.TenPointScale

/**
 * How an exercise felt — muscle feel and joint pain, each 1–10 (ROADMAP N8).
 *
 * One dialog serves the prompt shown when an exercise is marked done and the edit
 * reached from the workout detail; [isPrompt] only changes the wording and whether
 * the secondary button reads *Skip* or *Cancel*.
 *
 * Deliberately unlabelled ends: the decision recorded in the roadmap is numbers
 * only for now, because an anchor would be a claim about what 3 and 7 mean that the
 * app has no basis for. Both fields are optional — the whole capture is skippable —
 * and a value outside 1–10 keeps Save disabled rather than being clamped, since a
 * silent 11 → 10 would misstate the session.
 */
@Composable
fun ExerciseRatingDialog(
    initialMuscleFeel: Int?,
    initialJointPain: Int?,
    isPrompt: Boolean,
    onDismiss: () -> Unit,
    onSave: (muscleFeel: Int?, jointPain: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var muscleText by rememberSaveable { mutableStateOf(initialMuscleFeel?.toString().orEmpty()) }
    var jointText by rememberSaveable { mutableStateOf(initialJointPain?.toString().orEmpty()) }

    val muscleFeel = muscleText.trim().ifEmpty { null }?.toIntOrNull()
    val jointPain = jointText.trim().ifEmpty { null }?.toIntOrNull()
    // Blank is valid; anything typed has to parse *and* sit on the scale.
    val muscleIsValid = muscleText.isBlank() || (muscleFeel != null && TenPointScale.isValid(muscleFeel))
    val jointIsValid = jointText.isBlank() || (jointPain != null && TenPointScale.isValid(jointPain))

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isPrompt) R.string.rating_prompt_title else R.string.rating_edit_title,
                ),
            )
        },
        text = {
            RatingFields(
                muscleText = muscleText,
                onMuscleChange = { muscleText = it },
                muscleIsValid = muscleIsValid,
                jointText = jointText,
                onJointChange = { jointText = it },
                jointIsValid = jointIsValid,
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(TestTags.RATING_SAVE),
                enabled = muscleIsValid && jointIsValid,
                onClick = { onSave(muscleFeel, jointPain) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(TestTags.RATING_DISMISS),
                onClick = onDismiss,
            ) {
                Text(
                    stringResource(
                        if (isPrompt) R.string.rating_skip else R.string.action_cancel,
                    ),
                )
            }
        },
    )
}

/** The two 1–10 fields, split out so the dialog reads as a dialog. */
@Composable
private fun RatingFields(
    muscleText: String,
    onMuscleChange: (String) -> Unit,
    muscleIsValid: Boolean,
    jointText: String,
    onJointChange: (String) -> Unit,
    jointIsValid: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = muscleText,
            onValueChange = onMuscleChange,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.RATING_MUSCLE_FIELD),
            singleLine = true,
            label = { Text(stringResource(R.string.rating_muscle_label)) },
            supportingText = { Text(stringResource(R.string.rating_hint)) },
            isError = muscleText.isNotBlank() && !muscleIsValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = jointText,
            onValueChange = onJointChange,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.RATING_JOINT_FIELD),
            singleLine = true,
            label = { Text(stringResource(R.string.rating_joint_label)) },
            supportingText = { Text(stringResource(R.string.rating_hint)) },
            isError = jointText.isNotBlank() && !jointIsValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}
