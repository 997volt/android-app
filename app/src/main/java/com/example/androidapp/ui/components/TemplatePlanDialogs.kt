package com.example.androidapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.example.androidapp.domain.model.TemplateSet
import com.example.androidapp.domain.repository.TemplateSetEdit

/**
 * The planned sets of one template exercise (ROADMAP N14).
 *
 * A list you edit in place — add, open, delete, duplicate — rather than a form with
 * one Save: each row writes as it is confirmed, which is how the rest of the template
 * editor already behaves. The rest and cue the plan prescribes are edited in the
 * editor behind this dialog, so this stays one job.
 */
@Composable
fun TemplatePlanDialog(
    exerciseName: String,
    sets: List<TemplateSet>,
    onAddSet: () -> Unit,
    onEditSet: (TemplateSet) -> Unit,
    onDeleteSet: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_plan_title, exerciseName)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // A long plan scrolls rather than pushing the buttons off screen.
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (sets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.template_plan_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(TestTags.TEMPLATE_PLAN_EMPTY),
                    )
                }
                sets.forEachIndexed { index, set ->
                    PlanSetRow(
                        number = index + 1,
                        set = set,
                        onEdit = { onEditSet(set) },
                        onDelete = { onDeleteSet(set.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.TEMPLATE_PLAN_CLOSE),
            ) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onDuplicate,
                    modifier = Modifier.testTag(TestTags.TEMPLATE_PLAN_DUPLICATE),
                ) {
                    Text(stringResource(R.string.template_plan_duplicate))
                }
                TextButton(
                    onClick = onAddSet,
                    modifier = Modifier.testTag(TestTags.TEMPLATE_PLAN_ADD),
                ) {
                    Text(stringResource(R.string.template_plan_add))
                }
            }
        },
    )
}

@Composable
private fun PlanSetRow(
    number: Int,
    set: TemplateSet,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.templatePlanSet(set.id))
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
            onClick = onDelete,
            modifier = Modifier.testTag(TestTags.templatePlanRemove(set.id)),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(
                    R.string.template_plan_remove,
                    number,
                ),
            )
        }
    }
}

/**
 * One planned set's targets (ROADMAP N14).
 *
 * Every field is optional, and the dialog says so by leaving them empty: a plan that
 * says "work up to a heavy single" has no weight to write down, and a zero would be a
 * claim the app cannot check. Reps are a range because a plan writes `(max 2)` and
 * means only the upper bound.
 */
/**
 * One planned set's targets (ROADMAP N14).
 *
 * Every field is optional, and the dialog says so by leaving them empty: a plan that
 * says "work up to a heavy single" has no weight to write down, and a zero would be a
 * claim the app cannot check. Reps are a range because a plan writes `(max 2)` and
 * means only the upper bound.
 */
@Composable
fun TemplateSetDialog(
    initial: TemplateSetEdit,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (TemplateSetEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `remember`, not `rememberSaveable`: a data class is not something a Bundle can
    // hold, and registering one throws when the dialog opens. The set editor's own
    // draft is held the same way for the same reason.
    var draft by remember { mutableStateOf(TemplateSetDraft(initial)) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isNew) R.string.template_set_title_new else R.string.template_set_title,
                ),
            )
        },
        text = { TargetFields(draft = draft, onChange = { draft = it }) },
        confirmButton = {
            TextButton(
                enabled = draft.isValid,
                modifier = Modifier.testTag(TestTags.TEMPLATE_SET_SAVE),
                onClick = { onSave(draft.toEdit()) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.TEMPLATE_SET_CANCEL),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * The draft, as text, with what it parses to.
 *
 * A holder rather than five `remember`s in the dialog: the parsing and the validation
 * are the same thing read twice, and keeping them together is what stops the Save
 * button enabling on a number the repository would refuse.
 */
data class TemplateSetDraft(
    val role: SetType = SetType.NORMAL,
    val weightText: String = "",
    val repsMinText: String = "",
    val repsMaxText: String = "",
    val rpeText: String = "",
    val note: String = "",
) {
    constructor(edit: TemplateSetEdit) : this(
        role = edit.role,
        weightText = if (edit.targetWeightGrams != null || edit.targetAssistanceGrams != null) {
            Weight.display(edit.targetWeightGrams ?: 0L, edit.targetAssistanceGrams ?: 0L)
        } else {
            ""
        },
        repsMinText = edit.targetRepsMin?.toString().orEmpty(),
        repsMaxText = edit.targetRepsMax?.toString().orEmpty(),
        rpeText = edit.targetRpeHalves?.let(Rpe::format).orEmpty(),
        note = edit.note.orEmpty(),
    )

    // The plan writes assistance the same way a set does: -20 in the weight field
    // (ROADMAP N15).
    val load: Load? get() = Weight.parseLoad(weightText)
    val weight: Long? get() = load?.weightGrams
    val assistanceGrams: Long? get() = load?.assistanceGrams
    val repsMin: Int? get() = repsMinText.trim().ifEmpty { null }?.toIntOrNull()
    val repsMax: Int? get() = repsMaxText.trim().ifEmpty { null }?.toIntOrNull()
    val rpeHalves: Int? get() = rpeText.trim().ifEmpty { null }?.let(Rpe::parse)

    // Blank is allowed everywhere; anything typed has to be a usable number, and a
    // range that runs backwards is refused rather than silently swapped.
    val weightIsValid: Boolean get() = weightText.isBlank() || load != null
    val repsAreValid: Boolean
        get() {
            // Read once into locals: a computed property has a custom getter, so Kotlin
            // will not smart-cast it at the use site.
            val min = repsMin
            val max = repsMax
            return (repsMinText.isBlank() || (min != null && min >= 1)) &&
                (repsMaxText.isBlank() || (max != null && max >= 1)) &&
                !(min != null && max != null && min > max)
        }
    val rpeIsValid: Boolean get() = rpeText.isBlank() || rpeHalves != null
    val isValid: Boolean get() = weightIsValid && repsAreValid && rpeIsValid

    fun toEdit() = TemplateSetEdit(
        role = role,
        targetWeightGrams = weight,
        targetAssistanceGrams = assistanceGrams?.takeIf { it > 0L },
        targetRepsMin = repsMin,
        targetRepsMax = repsMax,
        targetRpeHalves = rpeHalves,
        note = note.trim().ifEmpty { null },
    )
}

@Composable
private fun TargetFields(
    draft: TemplateSetDraft,
    onChange: (TemplateSetDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetRoleSelector(
            role = draft.role,
            onSelect = { onChange(draft.copy(role = it)) },
            testTag = TestTags.TEMPLATE_SET_ROLE,
            optionTag = TestTags::templateSetRole,
        )
        OutlinedTextField(
            value = draft.weightText,
            onValueChange = { onChange(draft.copy(weightText = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.TEMPLATE_SET_WEIGHT),
            singleLine = true,
            isError = !draft.weightIsValid,
            label = { Text(stringResource(R.string.template_set_weight)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.repsMinText,
                onValueChange = { onChange(draft.copy(repsMinText = it)) },
                modifier = Modifier.weight(1f).testTag(TestTags.TEMPLATE_SET_REPS_MIN),
                singleLine = true,
                isError = !draft.repsAreValid,
                label = { Text(stringResource(R.string.template_set_reps_min)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = draft.repsMaxText,
                onValueChange = { onChange(draft.copy(repsMaxText = it)) },
                modifier = Modifier.weight(1f).testTag(TestTags.TEMPLATE_SET_REPS_MAX),
                singleLine = true,
                isError = !draft.repsAreValid,
                label = { Text(stringResource(R.string.template_set_reps_max)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        OutlinedTextField(
            value = draft.rpeText,
            onValueChange = { onChange(draft.copy(rpeText = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.TEMPLATE_SET_RPE),
            singleLine = true,
            isError = !draft.rpeIsValid,
            label = { Text(stringResource(R.string.set_rpe_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = draft.note,
            onValueChange = { onChange(draft.copy(note = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.TEMPLATE_SET_NOTE),
            label = { Text(stringResource(R.string.template_set_note)) },
            minLines = 2,
        )
    }
}

/** `100 kg × 3`, `× 3–5`, `RPE 8` — whatever the plan actually wrote, in one line. */
@Composable
private fun TemplateSet.summary(): String {
    val weight = if (targetWeightGrams != null || targetAssistanceGrams != null) {
        stringResource(
            R.string.template_set_weight_value,
            Weight.display(targetWeightGrams ?: 0L, targetAssistanceGrams ?: 0L),
        )
    } else {
        null
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
    return listOfNotNull(weight, reps, rpeHalves, note).joinToString(" · ")
}
