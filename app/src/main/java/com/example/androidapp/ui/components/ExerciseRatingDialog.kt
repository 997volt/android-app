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
 * The ends are labelled (ROADMAP N12): N8 shipped them unlabelled on purpose, because
 * what 3 or 7 mean is a claim the app has no basis for, and left the anchors as the
 * obvious refinement. Only the ends are named, and only here — the number is chosen
 * in this dialog, and the workout detail keeps reading a bare "Muscle feel 8" rather
 * than repeating the vocabulary on every past workout.
 *
 * Both fields are optional — the whole capture is skippable, which is what the Skip
 * button says — and a value outside 1–10 keeps Save disabled rather than being
 * clamped, since a silent 11 → 10 would misstate the session.
 */
@Composable
fun ExerciseRatingDialog(
    initialMuscleFeel: Int?,
    initialJointPain: Int?,
    initialJointPainNote: String,
    isPrompt: Boolean,
    onDismiss: () -> Unit,
    onSave: (muscleFeel: Int?, jointPain: Int?, jointPainNote: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var muscleText by rememberSaveable { mutableStateOf(initialMuscleFeel?.toString().orEmpty()) }
    var jointText by rememberSaveable { mutableStateOf(initialJointPain?.toString().orEmpty()) }
    var noteText by rememberSaveable { mutableStateOf(initialJointPainNote) }

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
                noteText = noteText,
                onNoteChange = { noteText = it },
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(TestTags.RATING_SAVE),
                enabled = muscleIsValid && jointIsValid,
                onClick = { onSave(muscleFeel, jointPain, noteText.trim().ifEmpty { null }) },
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
    noteText: String,
    onNoteChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = muscleText,
            onValueChange = onMuscleChange,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.RATING_MUSCLE_FIELD),
            singleLine = true,
            label = { Text(stringResource(R.string.rating_muscle_label)) },
            // The anchors, not "Optional": the Skip button already says the rating
            // can be left alone, and this line is the only place the scale can be
            // explained while the number is being picked (ROADMAP N12).
            supportingText = { Text(stringResource(R.string.rating_muscle_anchors)) },
            isError = muscleText.isNotBlank() && !muscleIsValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = jointText,
            onValueChange = onJointChange,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.RATING_JOINT_FIELD),
            singleLine = true,
            label = { Text(stringResource(R.string.rating_joint_label)) },
            supportingText = { Text(stringResource(R.string.rating_joint_anchors)) },
            isError = jointText.isNotBlank() && !jointIsValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        // N9's location box, under the rating it explains. Free text with no
        // validation: which joints hurt is not a set of values the app can check,
        // and a rating of 4 means more a month later if it says where.
        OutlinedTextField(
            value = noteText,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.RATING_JOINT_NOTE_FIELD),
            singleLine = true,
            label = { Text(stringResource(R.string.rating_joint_note_label)) },
            supportingText = { Text(stringResource(R.string.rating_joint_note_hint)) },
        )
    }
}
