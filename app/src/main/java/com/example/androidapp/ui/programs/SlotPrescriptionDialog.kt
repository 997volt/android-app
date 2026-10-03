package com.example.androidapp.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.androidapp.R
import com.example.androidapp.domain.Load
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.Rpe
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.SlotPrescription
import com.example.androidapp.domain.model.SlotSet
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.repository.SlotSetEdit
import com.example.androidapp.ui.components.SetRoleSelector
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.rpeMarker

/**
 * What one program slot prescribes, per exercise (ROADMAP P3.8).
 *
 * The list you edit in place — add, open, delete — the way a template's planned sets are edited.
 * An exercise the slot says nothing about shows the template's targets standing, which is why the
 * dialog says so and why an empty prescription is a legitimate state rather than a gap to fill.
 */
@Composable
fun SlotPrescriptionDialog(
    editor: SlotPrescriptionEditor,
    onAddSet: (String, SlotSetEdit) -> Unit,
    onUpdateSet: (String, SlotSetEdit) -> Unit,
    onRemoveSet: (String) -> Unit,
    onSetRestCue: (String, Int?, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which set is open, and for which exercise; its absence means the set dialog is closed.
    var editingSet by remember { mutableStateOf<SetBeingEdited?>(null) }
    // Which exercise's rest and cue is open, or null.
    var restCueFor by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(TestTags.Programs.PRESCRIPTION_DIALOG),
        title = { Text(stringResource(R.string.program_prescription_title, editor.templateName)) },
        text = {
            PrescriptionList(
                editor = editor,
                onAddSet = { exerciseId -> editingSet = SetBeingEdited(exerciseId, null) },
                onEditSet = { exerciseId, set -> editingSet = SetBeingEdited(exerciseId, set) },
                onRemoveSet = onRemoveSet,
                onEditRestCue = { exerciseId -> restCueFor = exerciseId },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.Programs.PRESCRIPTION_CLOSE),
            ) {
                Text(stringResource(R.string.action_close))
            }
        },
    )

    editingSet?.let { current ->
        SlotSetDialog(
            initial = current.set?.toEdit() ?: SlotSetEdit(),
            isNew = current.set == null,
            onDismiss = { editingSet = null },
            onSave = { edit ->
                if (current.set == null) {
                    onAddSet(current.exerciseId, edit)
                } else {
                    onUpdateSet(current.set.id, edit)
                }
                editingSet = null
            },
        )
    }

    restCueFor?.let { exerciseId ->
        val prescribed = editor.forExercise(exerciseId)
        SlotRestCueDialog(
            exerciseName = editor.exercises
                .firstOrNull { it.exerciseId == exerciseId }
                ?.exerciseName
                .orEmpty(),
            restSeconds = prescribed?.restSeconds,
            techniqueNote = prescribed?.techniqueNote,
            onSave = { rest, cue ->
                onSetRestCue(exerciseId, rest, cue)
                restCueFor = null
            },
            onDismiss = { restCueFor = null },
        )
    }
}

/** The set whose form is open, and the exercise it belongs to (P3.8). */
private data class SetBeingEdited(val exerciseId: String, val set: SlotSet?)

/** The template's exercises and what the slot prescribes for each, scrolled as one list (P3.8). */
@Composable
private fun PrescriptionList(
    editor: SlotPrescriptionEditor,
    onAddSet: (String) -> Unit,
    onEditSet: (String, SlotSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onEditRestCue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.program_prescription_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        editor.exercises.forEach { exercise ->
            PrescriptionExercise(
                exercise = exercise,
                prescription = editor.forExercise(exercise.exerciseId),
                onAddSet = { onAddSet(exercise.exerciseId) },
                onEditSet = { set -> onEditSet(exercise.exerciseId, set) },
                onRemoveSet = onRemoveSet,
                onEditRestCue = { onEditRestCue(exercise.exerciseId) },
            )
        }
    }
}

/** One exercise of the slot's template, and the sets the slot prescribes for it. */
@Composable
private fun PrescriptionExercise(
    exercise: TemplateExercise,
    prescription: SlotPrescription?,
    onAddSet: () -> Unit,
    onEditSet: (SlotSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onEditRestCue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = exercise.exerciseName,
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(
                onClick = onEditRestCue,
                modifier = Modifier.testTag(TestTags.Programs.prescriptionRestCue(exercise.exerciseId)),
            ) {
                Text(stringResource(R.string.program_prescription_rest_cue))
            }
        }

        val sets = prescription?.sets.orEmpty()
        if (sets.isEmpty()) {
            Text(
                text = stringResource(R.string.program_prescription_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sets.forEachIndexed { index, set ->
            PrescribedSetRow(
                number = index + 1,
                set = set,
                onEdit = { onEditSet(set) },
                onRemove = { onRemoveSet(set.id) },
            )
        }
        TextButton(
            onClick = onAddSet,
            modifier = Modifier.testTag(TestTags.Programs.prescriptionAddSet(exercise.exerciseId)),
        ) {
            Text(stringResource(R.string.program_prescription_add_set))
        }
    }
}

@Composable
private fun PrescribedSetRow(
    number: Int,
    set: SlotSet,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.Programs.prescriptionSet(set.id))
            .clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.template_plan_set, number, set.role.label),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = set.summary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.testTag(TestTags.Programs.prescriptionRemoveSet(set.id)),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.program_prescription_remove_set, number),
            )
        }
    }
}

/**
 * One prescribed set's targets (ROADMAP P3.8).
 *
 * The plan's own fields plus one a template's planned set cannot carry: a percentage of the
 * estimated one-rep max. Every field may be empty, and the load is one number written the way a
 * set writes it — `-20` for assistance (N15).
 */
@Composable
private fun SlotSetDialog(
    initial: SlotSetEdit,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (SlotSetEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(SlotSetDraft(initial)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(TestTags.Programs.PRESCRIPTION_SET_DIALOG),
        title = {
            Text(
                stringResource(
                    if (isNew) R.string.program_set_title_new else R.string.program_set_title,
                ),
            )
        },
        text = { SlotSetFields(draft = draft, onChange = { draft = it }) },
        confirmButton = {
            TextButton(
                enabled = draft.isValid,
                modifier = Modifier.testTag(TestTags.Programs.PRESCRIPTION_SET_SAVE),
                onClick = { onSave(draft.toEdit()) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.Programs.PRESCRIPTION_SET_CANCEL),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * The draft, as text, with what it parses to (P3.8).
 *
 * A holder rather than six `remember`s for the reason the template's own draft is: the parsing
 * and the validation are the same thing read twice, and keeping them together is what stops the
 * Save button enabling on a number the repository would refuse.
 */
private data class SlotSetDraft(
    val role: SetType = SetType.NORMAL,
    val weightText: String = "",
    val repsMinText: String = "",
    val repsMaxText: String = "",
    val rpeText: String = "",
    val percentText: String = "",
    val note: String = "",
) {
    constructor(edit: SlotSetEdit) : this(
        role = edit.role,
        weightText = if (edit.targetWeightGrams != null || edit.targetAssistanceGrams != null) {
            Weight.display(edit.targetWeightGrams ?: 0L, edit.targetAssistanceGrams ?: 0L)
        } else {
            ""
        },
        repsMinText = edit.targetRepsMin?.toString().orEmpty(),
        repsMaxText = edit.targetRepsMax?.toString().orEmpty(),
        rpeText = edit.targetRpeHalves?.let(Rpe::format).orEmpty(),
        percentText = edit.targetPercentOf1Rm?.toString().orEmpty(),
        note = edit.note.orEmpty(),
    )

    val load: Load? get() = Weight.parseLoad(weightText)
    val repsMin: Int? get() = repsMinText.trim().ifEmpty { null }?.toIntOrNull()
    val repsMax: Int? get() = repsMaxText.trim().ifEmpty { null }?.toIntOrNull()
    val rpeHalves: Int? get() = rpeText.trim().ifEmpty { null }?.let(Rpe::parse)
    val percent: Int? get() = percentText.trim().ifEmpty { null }?.toIntOrNull()

    val weightIsValid: Boolean get() = weightText.isBlank() || load != null
    val repsAreValid: Boolean
        get() {
            val min = repsMin
            val max = repsMax
            return (repsMinText.isBlank() || (min != null && min >= 1)) &&
                (repsMaxText.isBlank() || (max != null && max >= 1)) &&
                !(min != null && max != null && min > max)
        }
    val rpeIsValid: Boolean get() = rpeText.isBlank() || rpeHalves != null
    val percentIsValid: Boolean
        get() = percentText.isBlank() || (percent != null && percent in 1..MAX_PERCENT)
    val isValid: Boolean get() = weightIsValid && repsAreValid && rpeIsValid && percentIsValid

    fun toEdit() = SlotSetEdit(
        role = role,
        targetWeightGrams = load?.weightGrams,
        targetAssistanceGrams = load?.assistanceGrams?.takeIf { it > 0L },
        targetRepsMin = repsMin,
        targetRepsMax = repsMax,
        targetRpeHalves = rpeHalves,
        targetPercentOf1Rm = percent,
        note = note.trim().ifEmpty { null },
    )
}

@Composable
private fun SlotSetFields(
    draft: SlotSetDraft,
    onChange: (SlotSetDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SlotSetLoadFields(draft = draft, onChange = onChange)
        SlotSetEffortFields(draft = draft, onChange = onChange)
    }
}

/** The role and the two ways to name a load: kilograms, or a percentage of the estimate (P3.8). */
@Composable
private fun SlotSetLoadFields(
    draft: SlotSetDraft,
    onChange: (SlotSetDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetRoleSelector(
            role = draft.role,
            onSelect = { onChange(draft.copy(role = it)) },
            testTag = TestTags.Programs.PRESCRIPTION_SET_ROLE,
            optionTag = TestTags.Programs::prescriptionSetRole,
        )
        OutlinedTextField(
            value = draft.weightText,
            onValueChange = { onChange(draft.copy(weightText = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.Programs.PRESCRIPTION_SET_WEIGHT),
            singleLine = true,
            isError = !draft.weightIsValid,
            label = { Text(stringResource(R.string.template_set_weight)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = draft.percentText,
            onValueChange = { onChange(draft.copy(percentText = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.Programs.PRESCRIPTION_SET_PERCENT),
            singleLine = true,
            isError = !draft.percentIsValid,
            label = { Text(stringResource(R.string.program_set_percent)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

/** The reps, the RPE target and the note — how hard, rather than how heavy (P3.8, N14). */
@Composable
private fun SlotSetEffortFields(
    draft: SlotSetDraft,
    onChange: (SlotSetDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.repsMinText,
                onValueChange = { onChange(draft.copy(repsMinText = it)) },
                modifier = Modifier.weight(1f).testTag(TestTags.Programs.PRESCRIPTION_SET_REPS_MIN),
                singleLine = true,
                isError = !draft.repsAreValid,
                label = { Text(stringResource(R.string.template_set_reps_min)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = draft.repsMaxText,
                onValueChange = { onChange(draft.copy(repsMaxText = it)) },
                modifier = Modifier.weight(1f).testTag(TestTags.Programs.PRESCRIPTION_SET_REPS_MAX),
                singleLine = true,
                isError = !draft.repsAreValid,
                label = { Text(stringResource(R.string.template_set_reps_max)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        OutlinedTextField(
            value = draft.rpeText,
            onValueChange = { onChange(draft.copy(rpeText = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.Programs.PRESCRIPTION_SET_RPE),
            singleLine = true,
            isError = !draft.rpeIsValid,
            label = { Text(stringResource(R.string.set_rpe_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = draft.note,
            onValueChange = { onChange(draft.copy(note = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.Programs.PRESCRIPTION_SET_NOTE),
            label = { Text(stringResource(R.string.template_set_note)) },
            minLines = 2,
        )
    }
}

/**
 * The rest and cue a slot prescribes for one exercise, over the template's (ROADMAP P3.8, N14).
 *
 * Both blank means "use the template's, then the library's", which is the state a slot is in until
 * someone writes one — so the fields are empty rather than zero.
 */
@Composable
private fun SlotRestCueDialog(
    exerciseName: String,
    restSeconds: Int?,
    techniqueNote: String?,
    onSave: (Int?, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rest by rememberSaveable(exerciseName) {
        mutableStateOf(restSeconds?.toString().orEmpty())
    }
    var cue by rememberSaveable(exerciseName) { mutableStateOf(techniqueNote.orEmpty()) }
    val seconds = rest.trim().ifEmpty { null }?.toIntOrNull()
    val restIsValid = rest.isBlank() || (seconds != null && seconds > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(TestTags.Programs.REST_CUE_DIALOG),
        title = { Text(stringResource(R.string.program_rest_cue_title, exerciseName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rest,
                    onValueChange = { rest = it },
                    modifier = Modifier
                        .width(160.dp)
                        .testTag(TestTags.Programs.REST_FIELD),
                    singleLine = true,
                    isError = !restIsValid,
                    label = { Text(stringResource(R.string.template_rest_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = cue,
                    onValueChange = { cue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.Programs.CUE_FIELD),
                    singleLine = true,
                    label = { Text(stringResource(R.string.template_cue_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = restIsValid,
                onClick = { onSave(seconds, cue.trim().ifEmpty { null }) },
                modifier = Modifier.testTag(TestTags.Programs.REST_CUE_SAVE),
            ) {
                Text(stringResource(R.string.template_rest_cue_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.Programs.REST_CUE_CANCEL),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** `100 kg × 3`, `× 3–5`, `85%`, `RPE 8` — whatever the slot wrote, in one line. */
@Composable
private fun SlotSet.summary(): String {
    val weight = if (targetWeightGrams != null || targetAssistanceGrams != null) {
        stringResource(
            R.string.template_set_weight_value,
            Weight.display(targetWeightGrams ?: 0L, targetAssistanceGrams ?: 0L),
        )
    } else {
        null
    }
    val percent = targetPercentOf1Rm?.let {
        stringResource(R.string.program_set_percent_value, it)
    }
    val reps = when {
        targetRepsMin != null && targetRepsMax != null ->
            pluralStringResource(
                R.plurals.template_reps_range,
                targetRepsMax,
                targetRepsMin,
                targetRepsMax,
            )

        targetRepsMin != null -> pluralStringResource(
            R.plurals.template_reps_min_only,
            targetRepsMin,
            targetRepsMin,
        )

        targetRepsMax != null -> pluralStringResource(
            R.plurals.template_reps_max_only,
            targetRepsMax,
            targetRepsMax,
        )

        else -> null
    }
    val rpeHalves = targetRpeHalves?.let { rpeMarker(it) }
    return listOfNotNull(weight, percent, reps, rpeHalves, note).joinToString(" · ")
}

/** The stored targets of one prescribed set, as its form edits them. */
private fun SlotSet.toEdit() = SlotSetEdit(
    role = role,
    targetWeightGrams = targetWeightGrams,
    targetAssistanceGrams = targetAssistanceGrams,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    targetRpeHalves = targetRpeHalves,
    targetPercentOf1Rm = targetPercentOf1Rm,
    note = note,
)

/** Above this, a "percentage of the max" is no longer a percentage of a max (P3.8). */
private const val MAX_PERCENT = 100
