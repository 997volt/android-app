package com.example.androidapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.TenPointScale

/**
 * Edits one set: reps, weight, and the optional RPE and comment (ROADMAP N6).
 *
 * Shared between the live workout screen and the history detail screen (P1.7),
 * because correcting a set you just logged and correcting one from last week are
 * the same edit. It was private to the workout screen until history needed it.
 *
 * The set's **role** is here too (ROADMAP N14): `SetType` has carried warm-up, drop
 * and failure since the beginning with no way to reach them, and a plan that can say
 * "top set" while a logged set cannot is two vocabularies for one idea.
 *
 * RPE and the comment are always shown but may be left empty — the one-tap
 * **Log set** path writes neither, so an empty field is the normal case. Save
 * stays disabled while a field is not a usable value; in particular a typed RPE
 * outside 1–10 is refused rather than clamped, because a silent 11 → 10 would be
 * a lie about the set.
 */
@Composable
fun SetEditorDialog(
    initialReps: Int,
    initialWeightGrams: Long,
    onDismiss: () -> Unit,
    onSave: (SetEdit) -> Unit,
    modifier: Modifier = Modifier,
    initialRpe: Int? = null,
    initialNote: String? = null,
    initialSetType: SetType = SetType.NORMAL,
) {
    var draft by remember {
        mutableStateOf(
            SetDraft(
                repsText = initialReps.toString(),
                weightText = Weight.kilograms(initialWeightGrams),
                rpeText = initialRpe?.toString().orEmpty(),
                noteText = initialNote.orEmpty(),
                setType = initialSetType,
            ),
        )
    }

    SetEditorDialogContent(
        draft = draft,
        onDraftChange = { draft = it },
        onDismiss = onDismiss,
        onSave = onSave,
        modifier = modifier,
    )
}

/**
 * The role picker: the same values a planned set offers (ROADMAP N14).
 *
 * A dropdown rather than a row of chips because five options with words on them do not
 * fit a phone's dialog width, and the chosen one is the only one that needs to be read.
 */
@Composable
private fun SetRoleSelector(role: SetType, onSelect: (SetType) -> Unit) {
    var open by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.testTag(TestTags.SET_ROLE),
        ) {
            Text(stringResource(R.string.set_role, role.label))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SetType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    modifier = Modifier.testTag(TestTags.setRole(option.name)),
                )
            }
        }
    }
}

/** The editor's raw field text, kept together so the dialog body stays readable. */
private data class SetDraft(
    val repsText: String,
    val weightText: String,
    val rpeText: String,
    val noteText: String,
    val setType: SetType = SetType.NORMAL,
)

@Composable
private fun SetEditorDialogContent(
    draft: SetDraft,
    onDraftChange: (SetDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: (SetEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedReps = draft.repsText.toIntOrNull()?.takeIf { it > 0 }
    val parsedWeight = Weight.parseKilograms(draft.weightText)
    val parsedRpe = draft.rpeText.trim().ifEmpty { null }?.toIntOrNull()
    // Blank is valid; anything typed has to parse *and* sit on the scale.
    val rpeIsValid = draft.rpeText.isBlank() || (parsedRpe != null && TenPointScale.isValid(parsedRpe))

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetRoleSelector(
                    role = draft.setType,
                    onSelect = { onDraftChange(draft.copy(setType = it)) },
                )
                SetEditorNumbers(draft = draft, onDraftChange = onDraftChange)
                RpeAndNoteFields(
                    draft = draft,
                    onDraftChange = onDraftChange,
                    rpeIsValid = rpeIsValid,
                )
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(TestTags.SET_SAVE),
                enabled = parsedReps != null && parsedWeight != null && rpeIsValid,
                onClick = {
                    onSave(
                        SetEdit(
                            reps = parsedReps ?: 0,
                            weightGrams = parsedWeight ?: 0L,
                            setType = draft.setType,
                            // Out of range is already excluded by `enabled`.
                            rpe = parsedRpe?.takeIf { TenPointScale.isValid(it) },
                            note = draft.noteText.trim().ifEmpty { null },
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.set_save))
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag(TestTags.SET_CANCEL),
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.set_cancel))
            }
        },
    )
}

@Composable
private fun SetEditorNumbers(draft: SetDraft, onDraftChange: (SetDraft) -> Unit) {
    val parsedWeight = Weight.parseKilograms(draft.weightText)
    val parsedReps = draft.repsText.toIntOrNull()?.takeIf { it > 0 }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberStepper(
            label = stringResource(R.string.set_weight_label),
            testTag = TestTags.SET_WEIGHT_FIELD,
            increaseTag = TestTags.SET_INCREASE_WEIGHT,
            decreaseTag = TestTags.SET_DECREASE_WEIGHT,
            value = draft.weightText,
            onValueChange = { onDraftChange(draft.copy(weightText = it)) },
            keyboardType = KeyboardType.Decimal,
            // Wired to Weight.step, which had tests and no callers: the clamp at
            // zero lives there rather than being re-implemented.
            onStep = { delta ->
                onDraftChange(
                    draft.copy(
                        weightText = Weight.kilograms(
                            Weight.step(parsedWeight ?: 0L, delta * Weight.DEFAULT_STEP_GRAMS),
                        ),
                    ),
                )
            },
        )
        NumberStepper(
            label = stringResource(R.string.set_reps_label),
            testTag = TestTags.SET_REPS_FIELD,
            increaseTag = TestTags.SET_INCREASE_REPS,
            decreaseTag = TestTags.SET_DECREASE_REPS,
            value = draft.repsText,
            onValueChange = { onDraftChange(draft.copy(repsText = it)) },
            keyboardType = KeyboardType.Number,
            // A set of zero reps is not a set, so this floor is 1 rather than 0 —
            // unlike weight, where 0 is meaningful (bodyweight).
            onStep = { delta ->
                onDraftChange(
                    draft.copy(repsText = ((parsedReps ?: 1) + delta).coerceAtLeast(1).toString()),
                )
            },
        )
    }
}

@Composable
private fun RpeAndNoteFields(
    draft: SetDraft,
    onDraftChange: (SetDraft) -> Unit,
    rpeIsValid: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.rpeText,
            onValueChange = { onDraftChange(draft.copy(rpeText = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.SET_RPE_FIELD),
            singleLine = true,
            label = { Text(stringResource(R.string.set_rpe_label)) },
            supportingText = { Text(stringResource(R.string.set_rpe_hint)) },
            isError = draft.rpeText.isNotBlank() && !rpeIsValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = draft.noteText,
            onValueChange = { onDraftChange(draft.copy(noteText = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.SET_NOTE_FIELD),
            label = { Text(stringResource(R.string.set_note_label)) },
            minLines = 2,
        )
    }
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
    increaseTag: String,
    decreaseTag: String,
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
        StepButton(
            glyph = "\u2212",
            description = decreaseDescription,
            testTag = decreaseTag,
            onClick = { onStep(-1) },
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).testTag(testTag),
            singleLine = true,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        StepButton(
            glyph = "+",
            description = increaseDescription,
            testTag = increaseTag,
            onClick = { onStep(1) },
        )
    }
}

@Composable
private fun StepButton(
    glyph: String,
    description: String,
    testTag: String,
    onClick: () -> Unit,
) {
    IconButton(modifier = Modifier.testTag(testTag), onClick = onClick) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}
