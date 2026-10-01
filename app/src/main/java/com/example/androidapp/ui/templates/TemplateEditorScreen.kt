package com.example.androidapp.ui.templates

import androidx.compose.material3.MaterialTheme
import java.time.DayOfWeek
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.TemplateSet
import com.example.androidapp.domain.repository.TemplateSetEdit
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.shortLabel
import com.example.androidapp.ui.components.TemplatePlanDialog
import com.example.androidapp.ui.components.TemplateSetDialog
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
        onSetWeekday = viewModel::onSetWeekday,
        onRemoveExercise = viewModel::onRemoveExercise,
        onToggleSuperset = viewModel::onToggleSuperset,
        onMoveExercise = viewModel::onMoveExercise,
        onDeleteTemplate = viewModel::onDeleteTemplate,
        onAddExercise = { templateId?.let(onAddExercise) },
        onAddSet = viewModel::onAddSet,
        onUpdateSet = viewModel::onUpdateSet,
        onRemoveSet = viewModel::onRemoveSet,
        onDuplicateSets = viewModel::onDuplicateSets,
        onSaveExercisePlan = viewModel::onSaveExercisePlan,
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
    onSetWeekday: (DayOfWeek?) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onDeleteTemplate: () -> Unit,
    onAddExercise: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissMessage: () -> Unit = {},
    onAddSet: (String, TemplateSetEdit) -> Unit = { _, _ -> },
    onUpdateSet: (String, TemplateSetEdit) -> Unit = { _, _ -> },
    onRemoveSet: (String) -> Unit = {},
    onDuplicateSets: (String) -> Unit = {},
    onToggleSuperset: (String) -> Unit = {},
    onSaveExercisePlan: (String, Int?, String?) -> Unit = { _, _, _ -> },
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
            onSetWeekday = onSetWeekday,
            onRemoveExercise = onRemoveExercise,
            onToggleSuperset = onToggleSuperset,
            onMoveExercise = onMoveExercise,
            onAddSet = onAddSet,
            onUpdateSet = onUpdateSet,
            onRemoveSet = onRemoveSet,
            onDuplicateSets = onDuplicateSets,
            onSaveExercisePlan = onSaveExercisePlan,
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
    onSetWeekday: (DayOfWeek?) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onAddSet: (String, TemplateSetEdit) -> Unit,
    onUpdateSet: (String, TemplateSetEdit) -> Unit,
    onRemoveSet: (String) -> Unit,
    onDuplicateSets: (String) -> Unit,
    onSaveExercisePlan: (String, Int?, String?) -> Unit,
    modifier: Modifier = Modifier,
    onToggleSuperset: (String) -> Unit = {},
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
            WeekdayPicker(
                weekday = state.template?.weekday,
                onSelect = onSetWeekday,
            )
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
                modifier = Modifier.fillMaxSize().testTag(TestTags.TEMPLATE_EXERCISE_LIST),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                itemsIndexed(items = state.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                    TemplateExerciseBlock(
                        exercise = exercise,
                        position = index + 1,
                        isFirst = index == 0,
                        isLast = index == state.exercises.lastIndex,
                        onMoveUp = { onMoveExercise(exercise.id, -1) },
                        onMoveDown = { onMoveExercise(exercise.id, 1) },
                        onRemove = { onRemoveExercise(exercise.id) },
                        onToggleSuperset = { onToggleSuperset(exercise.id) },
                        supersetLabels = state.supersetLabels,
                        onAddSet = { edit -> onAddSet(exercise.id, edit) },
                        onUpdateSet = onUpdateSet,
                        onRemoveSet = onRemoveSet,
                        onDuplicateSets = { onDuplicateSets(exercise.id) },
                        onSavePlan = { rest, cue -> onSaveExercisePlan(exercise.id, rest, cue) },
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

/**
 * One exercise in the editor: its header, the plan's sets, and the rest and cue the
 * plan prescribes (ROADMAP N14).
 *
 * The dialogs live here rather than in the screen, the same shape as the exercise
 * section in a workout: the state that says "this panel is open" belongs next to the
 * row that opens it.
 */
@Composable
private fun TemplateExerciseBlock(
    exercise: TemplateExercise,
    position: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onAddSet: (TemplateSetEdit) -> Unit,
    onUpdateSet: (String, TemplateSetEdit) -> Unit,
    onRemoveSet: (String) -> Unit,
    onDuplicateSets: () -> Unit,
    onSavePlan: (Int?, String?) -> Unit,
    modifier: Modifier = Modifier,
    onToggleSuperset: (() -> Unit)? = null,
    supersetLabels: Map<String, String> = emptyMap(),
) {
    var planOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        TemplateExerciseRow(
            exercise = exercise,
            position = position,
            isFirst = isFirst,
            isLast = isLast,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onRemove = onRemove,
            onToggleSuperset = onToggleSuperset,
            supersetLabels = supersetLabels,
        )
        PlanRow(exercise = exercise, onClick = { planOpen = true })
        RestAndCue(
            exercise = exercise,
            onSave = onSavePlan,
        )
    }

    if (planOpen) {
        TemplatePlanDialog(
            exerciseName = exercise.exerciseName,
            sets = exercise.sets,
            onAddSet = { adding = true },
            onEditSet = { editing = it.id },
            onDeleteSet = onRemoveSet,
            onDuplicate = onDuplicateSets,
            onDismiss = { planOpen = false },
        )
    }

    val edited = exercise.sets.firstOrNull { it.id == editing }
    if (adding || edited != null) {
        TemplateSetDialog(
            initial = edited?.let { it.toEdit() } ?: TemplateSetEdit(),
            isNew = edited == null,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { edit ->
                if (edited == null) onAddSet(edit) else onUpdateSet(edited.id, edit)
                adding = false
                editing = null
            },
        )
    }
}

/** `Planned sets · 3` — the way into the plan for this exercise. */
@Composable
private fun PlanRow(
    exercise: TemplateExercise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.template_plan_row)) },
        supportingContent = {
            Text(
                if (exercise.sets.isEmpty()) {
                    stringResource(R.string.template_plan_none)
                } else {
                    pluralStringResource(
                        R.plurals.template_plan_summary,
                        exercise.sets.size,
                        exercise.sets.size,
                    )
                },
            )
        },
        modifier = modifier
            .testTag(TestTags.TEMPLATE_PLAN_ROW)
            .clickable(onClick = onClick),
    )
}

/**
 * The rest and cue this exercise's plan prescribes (N14), over the library's (N5).
 *
 * Both blank means "use the library's", which is the state a plan is in until someone
 * writes one — so the fields are empty rather than zero.
 */
@Composable
private fun RestAndCue(
    exercise: TemplateExercise,
    onSave: (Int?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rest by rememberSaveable(exercise.id) {
        mutableStateOf(exercise.restSeconds?.toString().orEmpty())
    }
    var cue by rememberSaveable(exercise.id) { mutableStateOf(exercise.techniqueNote.orEmpty()) }
    val restSeconds = rest.trim().ifEmpty { null }?.toIntOrNull()
    val restIsValid = rest.isBlank() || (restSeconds != null && restSeconds > 0)
    val changed = restSeconds != exercise.restSeconds || cue.trim().ifEmpty { null } != exercise.techniqueNote

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = rest,
            onValueChange = { rest = it },
            modifier = Modifier.width(120.dp).testTag(TestTags.TEMPLATE_REST_FIELD),
            singleLine = true,
            isError = !restIsValid,
            label = { Text(stringResource(R.string.template_rest_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = cue,
            onValueChange = { cue = it },
            modifier = Modifier.weight(1f).testTag(TestTags.TEMPLATE_CUE_FIELD),
            singleLine = true,
            label = { Text(stringResource(R.string.template_cue_label)) },
        )
        IconButton(
            onClick = { onSave(restSeconds, cue.trim().ifEmpty { null }) },
            enabled = changed && restIsValid,
            modifier = Modifier.testTag(TestTags.TEMPLATE_REST_CUE_SAVE),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.template_rest_cue_save),
            )
        }
    }
}

private fun TemplateSet.toEdit() = TemplateSetEdit(
    role = role,
    targetWeightGrams = targetWeightGrams,
    targetAssistanceGrams = targetAssistanceGrams,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    targetRpeHalves = targetRpeHalves,
    note = note,
)

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
    onToggleSuperset: (() -> Unit)? = null,
    supersetLabels: Map<String, String> = emptyMap(),
) {
    ListItem(
        headlineContent = {
            Text("$position. ${superscriptLabel(exercise, supersetLabels)}${exercise.exerciseName}")
        },
        supportingContent = {
            Text("${exercise.primaryMuscle.label} · ${exercise.equipment.label}")
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToggleSuperset != null) {
                    PlanSupersetToggle(exercise = exercise, onToggle = onToggleSuperset)
                }
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.testTag(TestTags.templateMove(exercise.id, up = true)),
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
                    modifier = Modifier.testTag(TestTags.templateMove(exercise.id, up = false)),
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
            onSetWeekday = {},
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

/**
 * The weekday this plan belongs to (ROADMAP N16).
 *
 * A row of chips rather than a dropdown: there are seven values and the current one is
 * what a person checks at a glance. "Not scheduled" is a value, not an empty state —
 * a plan with no day is a plan you start by hand, which is the common case.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayPicker(
    weekday: DayOfWeek?,
    onSelect: (DayOfWeek?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.template_weekday),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = weekday == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.template_weekday_none)) },
                modifier = Modifier.testTag(TestTags.templateWeekday("NONE")),
            )
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = weekday == day,
                    onClick = { onSelect(day) },
                    label = { Text(day.shortLabel()) },
                    modifier = Modifier.testTag(TestTags.templateWeekday(day.name)),
                )
            }
        }
    }
}

/** `A1 · ` for a grouped planned exercise, or nothing (ROADMAP B16). */
private fun superscriptLabel(exercise: TemplateExercise, labels: Map<String, String>): String =
    labels[exercise.id]?.let { "$it · " }.orEmpty()

/**
 * Planning an exercise into the superset above, or out of it (ROADMAP B16).
 *
 * Its own composable because the row around it is at the length this project allows, and
 * because the word changes with the state: "pair" when it stands alone, "leave" when it does not.
 */
@Composable
private fun PlanSupersetToggle(
    exercise: TemplateExercise,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onToggle,
        modifier = modifier.testTag(TestTags.supersetToggle(exercise.id)),
    ) {
        Text(
            stringResource(
                if (exercise.supersetGroup == null) {
                    R.string.superset_pair
                } else {
                    R.string.superset_unpair
                },
            ),
        )
    }
}
