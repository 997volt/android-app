package com.example.androidapp.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.theme.AndroidAppTheme

/**
 * One template: its name and its ordered exercises (ROADMAP N3).
 *
 * v1 is deliberately just exercises and their order. Target sets and rep ranges,
 * per-exercise rest, supersets and drop sets are the P3.1 half of the backlog, and
 * they arrive once templates are in use — this screen is the seam they land in.
 */
@Composable
fun TemplateEditorRoute(
    onAddExercise: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplateEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val templateId = state.template?.id

    // Deleting — or opening a template that is already gone — leaves the editor,
    // rather than leaving an empty shell behind with a live Delete button.
    LaunchedEffect(deleted, state.notFound) {
        if (deleted || state.notFound) currentOnBack()
    }

    TemplateEditorScreen(
        state = state,
        onRename = viewModel::onRename,
        onRemoveExercise = viewModel::onRemoveExercise,
        onMoveExercise = viewModel::onMoveExercise,
        onDeleteTemplate = viewModel::onDeleteTemplate,
        onAddExercise = { templateId?.let(onAddExercise) },
        onDismissMessage = viewModel::onErrorShown,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    state: TemplateEditorUiState,
    onRename: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onDeleteTemplate: () -> Unit,
    onAddExercise: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissMessage: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val currentOnDismissMessage by rememberUpdatedState(onDismissMessage)

    state.error?.let { failure ->
        val message = dataErrorMessage(failure)
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            currentOnDismissMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TemplateEditorTopBar(
                name = state.template?.name,
                onBack = onBack,
                onDelete = { confirmingDelete = true },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExercise,
                text = { Text(stringResource(R.string.template_add_exercise)) },
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.testTag(TestTags.TEMPLATE_ADD_EXERCISE),
            )
        },
    ) { innerPadding ->
        TemplateEditorBody(
            state = state,
            onRename = onRename,
            onRemoveExercise = onRemoveExercise,
            onMoveExercise = onMoveExercise,
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (confirmingDelete) {
        DeleteTemplateDialog(
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                confirmingDelete = false
                onDeleteTemplate()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditorTopBar(
    name: String?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = name ?: stringResource(R.string.template_edit_title),
                modifier = Modifier.testTag(TestTags.TEMPLATE_EDIT_TITLE),
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                )
            }
        },
        actions = {
            IconButton(onClick = onDelete, modifier = Modifier.testTag(TestTags.TEMPLATE_DELETE)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.template_delete),
                )
            }
        },
    )
}

@Composable
private fun TemplateEditorBody(
    state: TemplateEditorUiState,
    onRename: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        CenteredMessage(
            text = stringResource(R.string.templates_loading),
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        state.template?.let { template ->
            TemplateNameField(
                template = template,
                onRename = onRename,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        HorizontalDivider()

        if (state.exercises.isEmpty()) {
            CenteredMessage(
                text = stringResource(R.string.template_no_exercises),
                hint = stringResource(R.string.template_no_exercises_hint),
                modifier = Modifier.testTag(TestTags.TEMPLATE_NO_EXERCISES),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                itemsIndexed(items = state.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                    TemplateExerciseRow(
                        exercise = exercise,
                        position = index + 1,
                        isFirst = index == 0,
                        isLast = index == state.exercises.lastIndex,
                        onMoveUp = { onMoveExercise(exercise.id, -1) },
                        onMoveDown = { onMoveExercise(exercise.id, 1) },
                        onRemove = { onRemoveExercise(exercise.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * The template's name, committed deliberately rather than on every keystroke.
 *
 * A rename per character would write a row per character and, worse, could store a
 * half-typed name if the app died mid-word — and the template list shows that name.
 */
@Composable
private fun TemplateNameField(
    template: WorkoutTemplate,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the id, so another template's name can never leak into this field.
    var draft by rememberSaveable(template.id) { mutableStateOf(template.name) }
    val changed = draft.trim() != template.name

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f).testTag(TestTags.TEMPLATE_NAME_FIELD),
            singleLine = true,
            label = { Text(stringResource(R.string.template_name_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (changed) onRename(draft) }),
        )
        IconButton(
            onClick = { onRename(draft) },
            enabled = changed,
            modifier = Modifier.testTag(TestTags.TEMPLATE_NAME_SAVE),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.template_save_name),
            )
        }
    }
}

@Composable
private fun TemplateExerciseRow(
    exercise: TemplateExercise,
    position: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text("$position. ${exercise.exerciseName}") },
        supportingContent = {
            Text("${exercise.primaryMuscle.label} · ${exercise.equipment.label}")
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.testTag(TestTags.templateMoveUp(exercise.id)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(
                            R.string.template_move_up,
                            exercise.exerciseName,
                        ),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.testTag(TestTags.templateMoveDown(exercise.id)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(
                            R.string.template_move_down,
                            exercise.exerciseName,
                        ),
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(TestTags.templateRemove(exercise.id)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(
                            R.string.template_remove_exercise,
                            exercise.exerciseName,
                        ),
                    )
                }
            }
        },
        modifier = modifier.testTag(TestTags.templateExerciseRow(exercise.id)),
    )
}

@Composable
private fun DeleteTemplateDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_delete_confirm_title)) },
        text = { Text(stringResource(R.string.template_delete_confirm_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(TestTags.TEMPLATE_DELETE_CONFIRM),
            ) {
                Text(stringResource(R.string.template_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun TemplateEditorScreenPreview() {
    AndroidAppTheme {
        TemplateEditorScreen(
            state = TemplateEditorUiState(
                isLoading = false,
                template = WorkoutTemplate(id = "a", name = "Push day", exerciseCount = 2),
                exercises = listOf(
                    TemplateExercise(
                        id = "te1",
                        templateId = "a",
                        exerciseId = "bench-press",
                        position = 0,
                        exerciseName = "Bench Press",
                        primaryMuscle = MuscleGroup.CHEST,
                        equipment = Equipment.BARBELL,
                    ),
                    TemplateExercise(
                        id = "te2",
                        templateId = "a",
                        exerciseId = "overhead-press",
                        position = 1,
                        exerciseName = "Overhead Press",
                        primaryMuscle = MuscleGroup.SHOULDERS,
                        equipment = Equipment.BARBELL,
                    ),
                ),
            ),
            onRename = {},
            onRemoveExercise = {},
            onMoveExercise = { _, _ -> },
            onDeleteTemplate = {},
            onAddExercise = {},
            onBack = {},
        )
    }
}
