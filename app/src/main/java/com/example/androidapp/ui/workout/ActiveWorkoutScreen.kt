package com.example.androidapp.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.ui.theme.AndroidAppTheme

@Composable
fun ActiveWorkoutRoute(
    onAddExercise: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()

    // Kept as a State object rather than unwrapped with `by`: reading it here would
    // recompose this composable — and everything below it — once a second, which is
    // the whole point of F16. Only the header and the rest bar read it.
    val clock = viewModel.clock.collectAsStateWithLifecycle()

    // The rest alert needs two permissions that were declared but never requested
    // (ROADMAP F13). Asked for on the first logged set, where the reason is obvious.
    RestAlertPermissions(enabled = state.exercises.any { it.sets.isNotEmpty() })

    // rememberUpdatedState, because the effect below restarts on `closed`:
    // reading the lambda parameter directly would capture whichever `onDone` was
    // current when the effect last started.
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(closed) {
        if (closed) currentOnDone()
    }

    ActiveWorkoutScreen(
        state = state,
        clock = clock,
        onAddExercise = onAddExercise,
        onLogSet = viewModel::onLogSet,
        onUpdateSet = viewModel::onUpdateSet,
        onRemoveExercise = viewModel::onRemoveExercise,
        onDeleteSet = viewModel::onDeleteSet,
        onUndoDelete = viewModel::onUndoDelete,
        onDismissUndo = viewModel::onDismissUndo,
        onSkipRest = viewModel::onSkipRest,
        onAdjustRest = viewModel::onAdjustRest,
        onFinish = viewModel::onFinish,
        onDiscard = viewModel::onDiscard,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    clock: State<WorkoutClock>,
    onAddExercise: () -> Unit,
    onLogSet: (String) -> Unit,
    onUpdateSet: (String, Int, Long) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onDeleteSet: (String) -> Unit,
    onUndoDelete: () -> Unit,
    onDismissUndo: () -> Unit,
    onSkipRest: () -> Unit,
    onAdjustRest: (Int) -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ShowUndoSnackbar(state.pendingUndo, snackbarHostState, onUndoDelete, onDismissUndo)

    // The set being edited, held here so the caller does not have to track it.
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            WorkoutTopBar(
                canFinish = state.exercises.any { it.sets.isNotEmpty() },
                onFinish = onFinish,
                onBack = onBack,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExercise,
                text = { Text(stringResource(R.string.active_workout_add_exercise)) },
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
            )
        },
    ) { innerPadding ->
        WorkoutBody(
            state = state,
            clock = clock,
            onLogSet = onLogSet,
            onRemoveExercise = onRemoveExercise,
            onEditSet = { row -> editing = EditTarget(row) },
            onDeleteSet = onDeleteSet,
            onSkipRest = onSkipRest,
            onAdjustRest = onAdjustRest,
            onDiscard = onDiscard,
            modifier = Modifier.padding(innerPadding),
        )
    }

    editing?.let { target ->
        SetEditDialog(
            target = target,
            onDismiss = { editing = null },
            onSave = { reps, weightGrams ->
                onUpdateSet(target.row.id, reps, weightGrams)
                editing = null
            },
        )
    }
}

@Composable
private fun ShowUndoSnackbar(
    pendingUndo: SetEntry?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = stringResource(R.string.set_deleted)
    val undoLabel = stringResource(R.string.set_undo)
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(pendingUndo) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = hostState.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) currentOnUndo() else currentOnDismiss()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTopBar(canFinish: Boolean, onFinish: () -> Unit, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.active_workout_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                )
            }
        },
        actions = {
            // Nothing to finish until at least one set is logged.
            TextButton(onClick = onFinish, enabled = canFinish) {
                Text(stringResource(R.string.active_workout_finish))
            }
        },
    )
}

@Composable
private fun WorkoutBody(
    state: ActiveWorkoutUiState,
    clock: State<WorkoutClock>,
    onLogSet: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onEditSet: (SetRow) -> Unit,
    onDeleteSet: (String) -> Unit,
    onSkipRest: () -> Unit,
    onAdjustRest: (Int) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { error ->
            Text(
                text = errorMessage(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        when {
            state.isLoading -> CenteredMessage(stringResource(R.string.active_workout_loading), showSpinner = true)

            state.hasNoSession -> CenteredMessage(stringResource(R.string.active_workout_none), showSpinner = false)

            else -> {
                WorkoutHeader(startedAt = state.startedAt, clock = clock)
                RestBar(clock = clock, onSkip = onSkipRest, onAdjust = onAdjustRest)
                HorizontalDivider()

                if (state.isEmpty) {
                    EmptyWorkout(onDiscard = onDiscard)
                } else {
                    ExerciseList(
                        rows = state.exercises,
                        onLogSet = onLogSet,
                        onRemoveExercise = onRemoveExercise,
                        onEditSet = onEditSet,
                        onDeleteSet = onDeleteSet,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestBar(
    clock: State<WorkoutClock>,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Deciding whether to show this bar in the *parent* would recompose the exercise
    // list once a second. Returning early here keeps the tick inside this composable.
    val remaining = clock.value.restSecondsRemaining
    if (remaining <= 0) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.rest_remaining, RestTimer.format(remaining)),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onAdjust(-RestTimer.ADJUST_STEP_SECONDS) }) {
                    Text(stringResource(R.string.rest_subtract))
                }
                TextButton(onClick = { onAdjust(RestTimer.ADJUST_STEP_SECONDS) }) {
                    Text(stringResource(R.string.rest_add))
                }
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.rest_skip))
                }
            }
        }
    }
}

@Composable
private fun ExerciseList(
    rows: List<SessionExerciseRow>,
    onLogSet: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onEditSet: (SetRow) -> Unit,
    onDeleteSet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Leaves room for the extended FAB so it cannot cover the last row.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(items = rows, key = { it.id }) { row ->
            ExerciseSection(
                row = row,
                onLogSet = { onLogSet(row.id) },
                onRemoveExercise = { onRemoveExercise(row.id) },
                onEditSet = onEditSet,
                onDeleteSet = onDeleteSet,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ExerciseSection(
    row: SessionExerciseRow,
    onLogSet: () -> Unit,
    onRemoveExercise: () -> Unit,
    onEditSet: (SetRow) -> Unit,
    onDeleteSet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${row.muscleLabel} · ${row.equipmentLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.lastTime?.let { last ->
                    Text(
                        text = stringResource(
                            R.string.set_last_time,
                            stringResource(R.string.set_summary, Weight.kilograms(last.weightGrams), last.reps),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemoveExercise) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.active_workout_remove, row.name),
                )
            }
        }

        row.sets.forEach { set ->
            SetLine(
                set = set,
                onEdit = { onEditSet(set) },
                onDelete = { onDeleteSet(set.id) },
            )
        }

        FilledTonalButton(
            onClick = onLogSet,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.set_log,
                    stringResource(
                        R.string.set_summary,
                        Weight.kilograms(row.suggestion.weightGrams),
                        row.suggestion.reps,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun SetLine(
    set: SetRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tapping the line opens the editor; the number is shown 1-based.
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onEdit),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.set_number, set.number),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.set_summary,
                    Weight.kilograms(set.weightGrams),
                    set.reps,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                // Names the action, not the outcome: a screen reader should
                // announce "Delete set", not the confirmation that follows it.
                contentDescription = stringResource(R.string.set_delete),
            )
        }
    }
}

/** Which set the edit dialog is open for, and its current values. */
private data class EditTarget(val row: SetRow)

@Composable
private fun SetEditDialog(
    target: EditTarget,
    onDismiss: () -> Unit,
    onSave: (reps: Int, weightGrams: Long) -> Unit,
) {
    var weightText by remember { mutableStateOf(Weight.kilograms(target.row.weightGrams)) }
    var repsText by remember { mutableStateOf(target.row.reps.toString()) }

    // Null for an unusable entry, so Save stays disabled rather than writing a
    // silently-wrong value.
    val parsedWeight = Weight.parseKilograms(weightText)
    val parsedReps = repsText.toIntOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.set_weight_label)) },
                )
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.set_reps_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(parsedReps ?: 0, parsedWeight ?: 0L) },
                enabled = parsedWeight != null && parsedReps != null,
            ) {
                Text(stringResource(R.string.set_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.set_cancel)) }
        },
    )
}

@Composable
private fun WorkoutHeader(
    startedAt: String,
    clock: State<WorkoutClock>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.active_workout_started, startedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The only place the elapsed time is read, so a tick stops here.
        Text(text = clock.value.elapsed, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EmptyWorkout(onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.active_workout_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.active_workout_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onDiscard, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.active_workout_discard))
        }
    }
}

@Composable
private fun CenteredMessage(text: String, showSpinner: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) CircularProgressIndicator()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Maps a storage failure to something the user can act on. */
@Composable
private fun errorMessage(error: DataError): String = when (error) {
    DataError.NotFound -> stringResource(R.string.workout_error_not_found)
    // Already written for the user, so it is shown verbatim.
    is DataError.Invalid -> error.message
    is DataError.Storage -> stringResource(R.string.workout_error_storage)
}

@Preview(showBackground = true)
@Composable
private fun ActiveWorkoutScreenPreview() {
    AndroidAppTheme {
        ActiveWorkoutScreen(
            state = ActiveWorkoutUiState(
                isLoading = false,
                sessionId = "s1",
                startedAt = "07:42",
                exercises = listOf(
                    SessionExerciseRow(
                        id = "a",
                        exerciseId = "back-squat",
                        name = "Back Squat",
                        muscleLabel = "Quads",
                        equipmentLabel = "Barbell",
                        sets = listOf(
                            SetRow("s1", 1, reps = 8, weightGrams = 60_000),
                            SetRow("s2", 2, reps = 8, weightGrams = 60_000),
                        ),
                        suggestion = SetSuggestion(reps = 8, weightGrams = 60_000),
                        lastTime = SetRow("p1", 1, reps = 8, weightGrams = 57_500),
                    ),
                ),
            ),
            clock = remember {
                mutableStateOf(WorkoutClock(elapsed = "12:05", restSecondsRemaining = 83))
            },
            onAddExercise = {},
            onLogSet = {},
            onUpdateSet = { _, _, _ -> },
            onRemoveExercise = {},
            onDeleteSet = {},
            onUndoDelete = {},
            onDismissUndo = {},
            onSkipRest = {},
            onAdjustRest = {},
            onFinish = {},
            onDiscard = {},
            onBack = {},
        )
    }
}
