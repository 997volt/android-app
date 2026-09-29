package com.example.androidapp.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.theme.AndroidAppTheme

/** Stateful entry point for the detail destination; reads its id from the route. */
@Composable
fun ExerciseDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = viewModel::onEdit,
        onCancelEdit = viewModel::onCancelEdit,
        onSave = viewModel::onSave,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onSave: (ExerciseEdit) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(state.exercise?.name ?: stringResource(R.string.exercise_detail_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.exercise_detail_back),
                        )
                    }
                },
                actions = {
                    // Only a custom exercise is editable here (ROADMAP N2), and the
                    // action disappears while the form is already open.
                    if (state.canEdit) {
                        TextButton(
                            onClick = onEdit,
                            modifier = Modifier.testTag(TestTags.EXERCISE_EDIT),
                        ) {
                            Text(stringResource(R.string.exercise_edit))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        ExerciseDetailBody(
            state = state,
            onCancelEdit = onCancelEdit,
            onSave = onSave,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * What the screen shows once it has a state: a spinner, a failure, "no such
 * exercise", the edit form, or the exercise itself.
 *
 * Split out because the screen crossed the method limit when B4 added the failure
 * branch — and because the branch order *is* the behaviour worth reading in one
 * piece: a failure must be named before "not found", or a broken read would be
 * reported as a missing exercise.
 */
@Composable
private fun ExerciseDetailBody(
    state: ExerciseDetailUiState,
    onCancelEdit: () -> Unit,
    onSave: (ExerciseEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = state.exercise
    when {
        state.isLoading -> CenteredMessage(
            text = stringResource(R.string.exercise_library_loading),
            showSpinner = true,
            modifier = modifier,
        )

        // A failed read says so, where the exercise would have been (B4).
        state.error != null && !state.isEditing -> CenteredMessage(
            text = dataErrorMessage(state.error),
            showSpinner = false,
            modifier = modifier.testTag(TestTags.EXERCISE_READ_ERROR),
        )

        state.notFound -> CenteredMessage(
            text = stringResource(R.string.exercise_detail_not_found),
            showSpinner = false,
            modifier = modifier,
        )

        exercise != null && state.isEditing -> ExerciseEditForm(
            exercise = exercise,
            error = state.error,
            onCancel = onCancelEdit,
            onSave = onSave,
            modifier = modifier,
        )

        exercise != null -> ExerciseDetails(
            exercise = exercise,
            modifier = modifier,
        )
    }
}

@Composable
private fun ExerciseDetails(exercise: Exercise, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AttributeRow(
            label = stringResource(R.string.exercise_detail_primary),
            value = exercise.primaryMuscle.label,
        )
        HorizontalDivider()

        AttributeRow(
            label = stringResource(R.string.exercise_detail_secondary),
            value = exercise.secondaryMuscles
                .joinToString(", ") { it.label }
                .ifEmpty { stringResource(R.string.exercise_detail_none) },
        )
        HorizontalDivider()

        AttributeRow(
            label = stringResource(R.string.exercise_detail_equipment),
            value = exercise.equipment.label,
        )
        HorizontalDivider()

        AttributeRow(
            label = stringResource(R.string.exercise_detail_pattern),
            value = exercise.movementPattern.label,
        )
        HorizontalDivider()

        AttributeRow(
            label = stringResource(R.string.exercise_detail_rest),
            // Unset means "use the app default", shown as such rather than as a
            // dash: the user needs to know what will actually happen (N5).
            value = exercise.restSeconds?.let { RestTimer.format(it) }
                ?: stringResource(
                    R.string.exercise_detail_rest_default,
                    RestTimer.format(RestTimer.DEFAULT_SECONDS),
                ),
        )
        HorizontalDivider()

        AttributeRow(
            label = stringResource(R.string.exercise_detail_cue),
            value = exercise.techniqueNote ?: stringResource(R.string.exercise_detail_none),
        )
        HorizontalDivider()

        // Honest placeholder: history is the next milestone, not a broken screen.
        Text(
            text = stringResource(R.string.exercise_detail_history_planned),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * The edit form's working copy (ROADMAP N2, N5).
 *
 * The rest is held as raw text, not as an `Int?`: a half-typed value must not be
 * silently coerced into a rest the user did not type. Empty means "the app
 * default", and anything else has to parse as a positive number of seconds.
 */
private data class ExerciseDraft(
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val movementPattern: MovementPattern,
    val restText: String,
    val techniqueNote: String,
) {
    val restSeconds: Int? get() = restText.trim().ifEmpty { null }?.toIntOrNull()

    val restIsValid: Boolean
        get() {
            if (restText.isBlank()) return true
            val seconds = restSeconds ?: return false
            return seconds > 0
        }

    val canSave: Boolean get() = name.isNotBlank() && restIsValid

    fun toEdit(): ExerciseEdit = ExerciseEdit(
        name = name.trim(),
        primaryMuscle = primaryMuscle,
        equipment = equipment,
        movementPattern = movementPattern,
        restSeconds = restSeconds,
        techniqueNote = techniqueNote.trim().ifEmpty { null },
    )
}

private fun Exercise.toDraft() = ExerciseDraft(
    name = name,
    primaryMuscle = primaryMuscle,
    equipment = equipment,
    movementPattern = movementPattern,
    restText = restSeconds?.toString().orEmpty(),
    techniqueNote = techniqueNote.orEmpty(),
)

/**
 * The edit form behind the detail screen (ROADMAP N2, N5).
 *
 * The draft lives here rather than in the ViewModel: these are transient field
 * values, and keeping them local means a recomposition caused by an incoming
 * error cannot discard what the user typed.
 */
@Composable
private fun ExerciseEditForm(
    exercise: Exercise,
    error: DataError?,
    onCancel: () -> Unit,
    onSave: (ExerciseEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keys on the exercise id so switching to another exercise resets the draft
    // rather than carrying the previous one's values over.
    var draft by remember(exercise.id) { mutableStateOf(exercise.toDraft()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExerciseEditFields(draft = draft, onDraftChange = { draft = it })

        ExercisePrescriptionFields(draft = draft, onDraftChange = { draft = it })

        error?.let { failure ->
            Text(
                text = dataErrorMessage(failure),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        ExerciseEditActions(
            // A name is required; everything else may stay unset, and a rest that
            // was typed but does not parse keeps Save disabled rather than being
            // rounded to a number the user did not enter.
            saveEnabled = draft.canSave,
            onCancel = onCancel,
            onSave = { onSave(draft.toEdit()) },
        )
    }
}

/** The identity attributes — name and taxonomy (ROADMAP N2). */
@Composable
private fun ExerciseEditFields(
    draft: ExerciseDraft,
    onDraftChange: (ExerciseDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.EXERCISE_EDIT_NAME),
            singleLine = true,
            label = { Text(stringResource(R.string.exercise_name_label)) },
        )

        AttributeSelector(
            label = stringResource(R.string.exercise_detail_primary),
            selected = draft.primaryMuscle,
            options = MuscleGroup.entries,
            optionLabel = { it.label },
            testTag = TestTags.EXERCISE_EDIT_MUSCLE,
            onSelect = { onDraftChange(draft.copy(primaryMuscle = it)) },
        )

        AttributeSelector(
            label = stringResource(R.string.exercise_detail_equipment),
            selected = draft.equipment,
            options = Equipment.entries,
            optionLabel = { it.label },
            testTag = TestTags.EXERCISE_EDIT_EQUIPMENT,
            onSelect = { onDraftChange(draft.copy(equipment = it)) },
        )

        AttributeSelector(
            label = stringResource(R.string.exercise_detail_pattern),
            selected = draft.movementPattern,
            options = MovementPattern.entries,
            optionLabel = { it.label },
            testTag = TestTags.EXERCISE_EDIT_PATTERN,
            onSelect = { onDraftChange(draft.copy(movementPattern = it)) },
        )
    }
}

/**
 * The prescription attributes — rest and the technique cue (ROADMAP N5).
 *
 * Separate from the identity fields because they are a different kind of fact:
 * the identity says *what* the movement is, this says *how* to do it.
 */
@Composable
private fun ExercisePrescriptionFields(
    draft: ExerciseDraft,
    onDraftChange: (ExerciseDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.restText,
            onValueChange = { onDraftChange(draft.copy(restText = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.EXERCISE_EDIT_REST),
            singleLine = true,
            label = { Text(stringResource(R.string.exercise_detail_rest)) },
            supportingText = { Text(stringResource(R.string.exercise_edit_rest_hint)) },
            isError = draft.restText.isNotBlank() && !draft.restIsValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        OutlinedTextField(
            value = draft.techniqueNote,
            onValueChange = { onDraftChange(draft.copy(techniqueNote = it)) },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.EXERCISE_EDIT_CUE),
            label = { Text(stringResource(R.string.exercise_detail_cue)) },
            minLines = 2,
        )
    }
}

@Composable
private fun ExerciseEditActions(
    saveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag(TestTags.EXERCISE_EDIT_CANCEL),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.testTag(TestTags.EXERCISE_EDIT_SAVE),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

/**
 * A labelled dropdown over one enum's values.
 *
 * A `DropdownMenu` behind a button rather than an exposed-dropdown text field:
 * the value is always one of a fixed set, so there is nothing to type, and the
 * button's label reads the current choice to a screen reader without extra work.
 */
@Composable
private fun <T> AttributeSelector(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    testTag: String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    // Boxed so the menu anchors to its button: a composable emitting two siblings
    // at the top level has no defined anchor for the popup.
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = optionLabel(selected), style = MaterialTheme.typography.bodyLarge)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun AttributeRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    showSpinner: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) CircularProgressIndicator()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseDetailScreenPreview() {
    AndroidAppTheme {
        ExerciseDetailScreen(
            state = ExerciseDetailUiState(
                isLoading = false,
                exercise = Exercise(
                    id = "back-squat",
                    name = "Back Squat",
                    primaryMuscle = MuscleGroup.QUADS,
                    secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.CORE),
                    equipment = Equipment.BARBELL,
                    movementPattern = MovementPattern.SQUAT,
                ),
            ),
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseDetailEditPreview() {
    AndroidAppTheme {
        ExerciseDetailScreen(
            state = ExerciseDetailUiState(
                isLoading = false,
                isEditing = true,
                exercise = Exercise(
                    id = "custom-1",
                    name = "Sled Push",
                    primaryMuscle = MuscleGroup.OTHER,
                    equipment = Equipment.OTHER,
                    movementPattern = MovementPattern.OTHER,
                    isCustom = true,
                ),
            ),
            onBack = {},
        )
    }
}
