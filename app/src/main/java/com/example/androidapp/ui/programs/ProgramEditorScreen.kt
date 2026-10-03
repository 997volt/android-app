package com.example.androidapp.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.components.shortLabel
import com.example.androidapp.ui.theme.AndroidAppTheme
import java.time.DayOfWeek

/**
 * One program: its name, whether it is the active one, and its ordered slots (ROADMAP P3.3).
 *
 * The order is the feature: a slot's weekday answers "what happens on a Tuesday", and its
 * position answers "which one is next". Both are edited here, and neither is inferred.
 */
@Composable
fun ProgramEditorRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)

    // Deleting — or opening a program that is already gone — leaves the editor, rather than
    // leaving an empty shell behind with a live Delete button.
    LaunchedEffect(deleted, state.notFound) {
        if (deleted || state.notFound) currentOnBack()
    }

    ProgramEditorScreen(
        state = state,
        onRename = viewModel::onRename,
        onSetActive = viewModel::onSetActive,
        onAddSlot = viewModel::onAddSlot,
        onSetSlotWeekday = viewModel::onSetSlotWeekday,
        onMoveSlot = viewModel::onMoveSlot,
        onRemoveSlot = viewModel::onRemoveSlot,
        onDeleteProgram = viewModel::onDeleteProgram,
        onDismissMessage = viewModel::onErrorShown,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramEditorScreen(
    state: ProgramEditorUiState,
    onRename: (String) -> Unit,
    onSetActive: (Boolean) -> Unit,
    onAddSlot: (String) -> Unit,
    onSetSlotWeekday: (String, DayOfWeek?) -> Unit,
    onMoveSlot: (String, Int) -> Unit,
    onRemoveSlot: (String) -> Unit,
    onDeleteProgram: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissMessage: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var pickingTemplate by rememberSaveable { mutableStateOf(false) }
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
            ProgramEditorTopBar(
                name = state.program?.name,
                onBack = onBack,
                onDelete = { confirmingDelete = true },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickingTemplate = true },
                text = { Text(stringResource(R.string.program_add_slot)) },
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.testTag(TestTags.Programs.ADD_SLOT),
            )
        },
    ) { innerPadding ->
        ProgramEditorBody(
            state = state,
            onRename = onRename,
            onSetActive = onSetActive,
            onSetSlotWeekday = onSetSlotWeekday,
            onMoveSlot = onMoveSlot,
            onRemoveSlot = onRemoveSlot,
            modifier = Modifier.padding(innerPadding),
        )
    }

    ProgramEditorDialogs(
        state = state,
        pickingTemplate = pickingTemplate,
        confirmingDelete = confirmingDelete,
        onPick = { templateId ->
            pickingTemplate = false
            onAddSlot(templateId)
        },
        onDismissPicker = { pickingTemplate = false },
        onDismissDelete = { confirmingDelete = false },
        onConfirmDelete = {
            confirmingDelete = false
            onDeleteProgram()
        },
    )
}

/**
 * The two things the editor can be asked to do before it writes (P3.3).
 *
 * Split out because the screen around them is at the length this project allows, and because
 * both are dialogs over the same state: one asks which plan to add, the other asks before
 * deleting.
 */
@Composable
private fun ProgramEditorDialogs(
    state: ProgramEditorUiState,
    pickingTemplate: Boolean,
    confirmingDelete: Boolean,
    onPick: (String) -> Unit,
    onDismissPicker: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    if (pickingTemplate) {
        TemplatePickerDialog(
            templates = state.templates,
            onPick = onPick,
            onDismiss = onDismissPicker,
        )
    }

    if (confirmingDelete) {
        DeleteProgramDialog(onDismiss = onDismissDelete, onConfirm = onConfirmDelete)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramEditorTopBar(
    name: String?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(name ?: stringResource(R.string.program_edit_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                )
            }
        },
        actions = {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag(TestTags.Programs.DELETE),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.program_delete),
                )
            }
        },
    )
}

@Composable
private fun ProgramEditorBody(
    state: ProgramEditorUiState,
    onRename: (String) -> Unit,
    onSetActive: (Boolean) -> Unit,
    onSetSlotWeekday: (String, DayOfWeek?) -> Unit,
    onMoveSlot: (String, Int) -> Unit,
    onRemoveSlot: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        CenteredMessage(
            text = stringResource(R.string.programs_loading),
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        state.program?.let { program ->
            ProgramNameField(
                program = program,
                onRename = onRename,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ActiveSwitch(program = program, onSetActive = onSetActive)
        }
        HorizontalDivider()

        if (state.slots.isEmpty()) {
            CenteredMessage(
                text = stringResource(R.string.program_no_slots),
                hint = stringResource(R.string.program_no_slots_hint),
                modifier = Modifier.testTag(TestTags.Programs.NO_SLOTS),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                itemsIndexed(items = state.slots, key = { _, slot -> slot.id }) { index, slot ->
                    ProgramSlotBlock(
                        slot = slot,
                        isFirst = index == 0,
                        isLast = index == state.slots.lastIndex,
                        onMoveUp = { onMoveSlot(slot.id, -1) },
                        onMoveDown = { onMoveSlot(slot.id, 1) },
                        onRemove = { onRemoveSlot(slot.id) },
                        onSetWeekday = { weekday -> onSetSlotWeekday(slot.id, weekday) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** The program's name, committed deliberately rather than on every keystroke (N3's rule). */
@Composable
private fun ProgramNameField(
    program: WorkoutProgram,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the id, so another program's name can never leak into this field.
    var draft by rememberSaveable(program.id) { mutableStateOf(program.name) }
    val changed = draft.trim() != program.name

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f).testTag(TestTags.Programs.NAME_FIELD),
            singleLine = true,
            label = { Text(stringResource(R.string.program_name_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (changed) onRename(draft) }),
        )
        IconButton(
            onClick = { onRename(draft) },
            enabled = changed,
            modifier = Modifier.testTag(TestTags.Programs.NAME_SAVE),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.program_save_name),
            )
        }
    }
}

/**
 * Whether home follows this program (P3.3).
 *
 * A switch rather than a button because it is a state with two directions: off is the
 * fallback to each plan's own weekday, which is a real answer and not an absence.
 */
@Composable
private fun ActiveSwitch(
    program: WorkoutProgram,
    onSetActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.program_active),
                style = MaterialTheme.typography.titleMedium,
            )
            Switch(
                checked = program.isActive,
                onCheckedChange = onSetActive,
                modifier = Modifier.testTag(TestTags.Programs.ACTIVE),
            )
        }
        Text(
            text = stringResource(R.string.program_active_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** One slot: its template, the order it sits in, and the day it falls on. */
@Composable
private fun ProgramSlotBlock(
    slot: ProgramSlot,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSetWeekday: (DayOfWeek?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text(slot.templateName) },
            supportingContent = {
                Text(
                    pluralStringResource(
                        R.plurals.template_exercises,
                        slot.exerciseCount,
                        slot.exerciseCount,
                    ),
                )
            },
            trailingContent = {
                SlotActions(
                    slot = slot,
                    isFirst = isFirst,
                    isLast = isLast,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onRemove = onRemove,
                )
            },
            modifier = Modifier.testTag(TestTags.Programs.slot(slot.id)),
        )
        SlotWeekdayPicker(slot = slot, onSelect = onSetWeekday)
    }
}

/** The order and the delete controls of one slot, split out so the row stays a row. */
@Composable
private fun SlotActions(
    slot: ProgramSlot,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onMoveUp,
            enabled = !isFirst,
            modifier = Modifier.testTag(TestTags.Programs.move(slot.id, up = true)),
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.program_move_up, slot.templateName),
            )
        }
        IconButton(
            onClick = onMoveDown,
            enabled = !isLast,
            modifier = Modifier.testTag(TestTags.Programs.move(slot.id, up = false)),
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.program_move_down, slot.templateName),
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.testTag(TestTags.Programs.removeSlot(slot.id)),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.program_remove_slot, slot.templateName),
            )
        }
    }
}

/**
 * The day this slot falls on (P3.3).
 *
 * Seven chips with **no** separate "none": a slot with no day is order-only, and that is
 * what tapping the selected chip leaves behind. Adding a "none" chip beside the seven would
 * make "no day" a value someone could mistake for a day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotWeekdayPicker(
    slot: ProgramSlot,
    onSelect: (DayOfWeek?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.template_weekday),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DayOfWeek.entries.forEach { day ->
            val selected = slot.weekday == day
            FilterChip(
                selected = selected,
                // Tapping the chosen day again makes the slot order-only.
                onClick = { onSelect(if (selected) null else day) },
                label = { Text(day.shortLabel()) },
                modifier = Modifier.testTag(TestTags.Programs.slotWeekday(slot.id, day.name)),
            )
        }
    }
}

/** The templates a slot can point at (P3.3): the plan has to exist before it can be ordered. */
@Composable
private fun TemplatePickerDialog(
    templates: List<WorkoutTemplate>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(TestTags.Programs.PICKER),
        title = { Text(stringResource(R.string.program_pick_title)) },
        text = {
            if (templates.isEmpty()) {
                CenteredMessage(
                    text = stringResource(R.string.program_pick_empty),
                    hint = stringResource(R.string.program_pick_empty_hint),
                    modifier = Modifier.testTag(TestTags.Programs.PICKER_EMPTY),
                )
            } else {
                LazyColumn {
                    itemsIndexed(items = templates, key = { _, template -> template.id }) { _, template ->
                        ListItem(
                            headlineContent = { Text(template.name) },
                            supportingContent = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.template_exercises,
                                        template.exerciseCount,
                                        template.exerciseCount,
                                    ),
                                )
                            },
                            modifier = Modifier
                                .testTag(TestTags.Programs.pickTemplate(template.id))
                                .clickable { onPick(template.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DeleteProgramDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.program_delete_confirm_title)) },
        text = { Text(stringResource(R.string.program_delete_confirm_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(TestTags.Programs.DELETE_CONFIRM),
            ) {
                Text(stringResource(R.string.program_delete))
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
private fun ProgramEditorScreenPreview() {
    AndroidAppTheme {
        ProgramEditorScreen(
            state = ProgramEditorUiState(
                isLoading = false,
                program = WorkoutProgram(id = "p1", name = "Upper/Lower", slotCount = 2),
                slots = listOf(
                    ProgramSlot(
                        id = "s1",
                        programId = "p1",
                        templateId = "t1",
                        position = 0,
                        weekday = DayOfWeek.MONDAY,
                        templateName = "Heavy lower",
                        exerciseCount = 4,
                    ),
                    ProgramSlot(
                        id = "s2",
                        programId = "p1",
                        templateId = "t2",
                        position = 1,
                        templateName = "Push",
                        exerciseCount = 5,
                    ),
                ),
            ),
            onRename = {},
            onSetActive = {},
            onAddSlot = {},
            onSetSlotWeekday = { _, _ -> },
            onMoveSlot = { _, _ -> },
            onRemoveSlot = {},
            onDeleteProgram = {},
            onBack = {},
        )
    }
}
