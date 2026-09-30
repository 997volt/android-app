package com.example.androidapp.ui.workout

import com.example.androidapp.domain.model.SetType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.ui.components.SetEditorDialog
import com.example.androidapp.ui.components.WorkoutNoteDialog
import com.example.androidapp.ui.components.ReadinessNoteDialog
import com.example.androidapp.ui.components.ExerciseRatingDialog
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.ui.components.TestTags
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
        onSaveReadinessNote = viewModel::onSaveReadinessNote,
        onDismissReadinessPrompt = viewModel::onDismissReadinessPrompt,
        onFinishExercise = viewModel::onFinishExercise,
        onRateExercise = viewModel::onRateExercise,
        onUndoFinishExercise = viewModel::onUndoFinishExercise,
        onDismissFinishUndo = viewModel::onDismissFinishUndo,
        onReopenExercise = viewModel::onReopenExercise,
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
    onUpdateSet: (String, Int, Long, Int?, String?, SetType, Long) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onDeleteSet: (String) -> Unit,
    onUndoDelete: () -> Unit,
    onDismissUndo: () -> Unit,
    onSkipRest: () -> Unit,
    onAdjustRest: (Int) -> Unit,
    onSaveReadinessNote: (String?) -> Unit,
    onDismissReadinessPrompt: () -> Unit,
    onFinishExercise: (String, Int?, Int?, String?) -> Unit,
    onRateExercise: (String, Int?, Int?, String?) -> Unit,
    onUndoFinishExercise: () -> Unit,
    onDismissFinishUndo: () -> Unit,
    onReopenExercise: (String) -> Unit,
    onFinish: (String?) -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    WorkoutSnackbars(
        state = state,
        hostState = snackbarHostState,
        onUndoDelete = onUndoDelete,
        onDismissUndo = onDismissUndo,
        onUndoFinishExercise = onUndoFinishExercise,
        onDismissFinishUndo = onDismissFinishUndo,
    )

    // The set being edited, held here so the caller does not have to track it.
    var editing by remember { mutableStateOf<SetRow?>(null) }


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
            onEditSet = { row -> editing = row },
            onDeleteSet = onDeleteSet,
            onSkipRest = onSkipRest,
            onAdjustRest = onAdjustRest,
            onSaveReadinessNote = onSaveReadinessNote,
            onDismissReadinessPrompt = onDismissReadinessPrompt,
            onFinishExercise = onFinishExercise,
            onRateExercise = onRateExercise,
            onReopenExercise = onReopenExercise,
            onDiscard = onDiscard,
            modifier = Modifier.padding(innerPadding),
        )
    }

    SetEditorSection(
        set = editing,
        onUpdateSet = onUpdateSet,
        onDismiss = { editing = null },
    )

}

/**
 * The dialog for one logged set, split out so the screen stays a scaffold.
 *
 * The *open* state stays with the screen, because the body is what opens it; this is
 * only the rendering, which is where the lines were.
 */
@Composable
private fun SetEditorSection(
    set: SetRow?,
    onUpdateSet: (String, Int, Long, Int?, String?, SetType, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    if (set == null) return

    SetEditorDialog(
        initialReps = set.reps,
        initialWeightGrams = set.weightGrams,
        initialRpe = set.rpeHalves,
        initialNote = set.note,
        onDismiss = onDismiss,
        onSave = { edit ->
            onUpdateSet(
                set.id,
                edit.reps,
                edit.weightGrams,
                edit.rpeHalves,
                edit.note,
                edit.setType,
                edit.assistanceGrams,
            )
            onDismiss()
        },
    )
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

/**
 * Both undo snackbars, and the rule that keeps them honest (ROADMAP B3).
 *
 * An undo is offered only while the thing it refers to is still in the session. The
 * two share one [SnackbarHostState], so without that rule a deleted set's Undo can
 * outlive its exercise: the row is gone, the button stays, and the write it fires is
 * refused. Filtering here is the dismissal; the ViewModel reports if a tap still
 * races through.
 */
@Composable
private fun WorkoutSnackbars(
    state: ActiveWorkoutUiState,
    hostState: SnackbarHostState,
    onUndoDelete: () -> Unit,
    onDismissUndo: () -> Unit,
    onUndoFinishExercise: () -> Unit,
    onDismissFinishUndo: () -> Unit,
) {
    ShowUndoSnackbar(
        pendingUndo = state.undoableSet,
        hostState = hostState,
        onUndo = onUndoDelete,
        onDismiss = onDismissUndo,
    )
    ShowFinishSnackbar(
        state = state,
        hostState = hostState,
        onUndo = onUndoFinishExercise,
        onDismiss = onDismissFinishUndo,
    )
}

/**
 * Undo for the exercise just marked done (ROADMAP N7).
 *
 * The same argument as the deleted-set snackbar: "Done" is accident protection, so
 * it needs a way back — and the exercise is only dimmed, never removed, so the undo
 * is a plain reopen rather than a re-insert.
 */
@Composable
private fun ShowFinishSnackbar(
    state: ActiveWorkoutUiState,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Null once the exercise is gone, which keys the effect below to dismiss the
    // snackbar rather than leave an Undo for a row that no longer exists (B3).
    val finishedId = state.undoableFinishedExerciseId
    val name = state.exercises.firstOrNull { it.id == finishedId }?.name
    val message = if (name == null) {
        stringResource(R.string.active_workout_exercise_done)
    } else {
        stringResource(R.string.active_workout_exercise_done_named, name)
    }
    val undoLabel = stringResource(R.string.set_undo)
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(finishedId) {
        if (finishedId == null) return@LaunchedEffect
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
private fun WorkoutTopBar(canFinish: Boolean, onFinish: (String?) -> Unit, onBack: () -> Unit) {
    // Whether the finish prompt is up. Dismissing it finishes without a comment
    // (ROADMAP N11): the user asked to finish, and the comment is optional.
    var commenting by remember { mutableStateOf(false) }

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
            TextButton(
                onClick = { commenting = true },
                enabled = canFinish,
                modifier = Modifier.testTag(TestTags.ACTIVE_WORKOUT_FINISH),
            ) {
                Text(stringResource(R.string.active_workout_finish))
            }
        },
    )

    if (commenting) {
        WorkoutNoteDialog(
            onDismiss = {
                commenting = false
                onFinish(null)
            },
            onSave = { note ->
                commenting = false
                onFinish(note)
            },
        )
    }
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
    onSaveReadinessNote: (String?) -> Unit,
    onDismissReadinessPrompt: () -> Unit,
    onFinishExercise: (String, Int?, Int?, String?) -> Unit,
    onRateExercise: (String, Int?, Int?, String?) -> Unit,
    onReopenExercise: (String) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { error ->
            Text(
                text = dataErrorMessage(error),
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
                ReadinessSection(
                    note = state.readinessNote,
                    promptVisible = state.isReadinessPromptVisible,
                    onDismissPrompt = onDismissReadinessPrompt,
                    onSave = onSaveReadinessNote,
                )
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
                        onFinishExercise = onFinishExercise,
                        onRateExercise = onRateExercise,
                        onReopenExercise = onReopenExercise,
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

/**
 * The readiness line and the dialog behind it (ROADMAP N4).
 *
 * "A form is open" is transient UI state, so it lives here next to the row that
 * opens it. The prompt flag still comes from the session, which is why a brand-new
 * workout opens straight into the dialog while a resumed one does not.
 */
@Composable
private fun ReadinessSection(
    note: String?,
    promptVisible: Boolean,
    onDismissPrompt: () -> Unit,
    onSave: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }

    ReadinessRow(note = note, onEdit = { editing = true }, modifier = modifier)
    if (promptVisible || editing) {
        ReadinessNoteDialog(
            initialNote = note.orEmpty(),
            isPrompt = promptVisible,
            onDismiss = {
                editing = false
                onDismissPrompt()
            },
            onSave = { written ->
                editing = false
                onSave(written)
            },
        )
    }
}

/**
 * The readiness note in the workout header (ROADMAP N4).
 *
 * Always present, so the field the prompt introduces stays reachable after the
 * prompt is skipped — a passive field nobody can find again is the failure that
 * made this a prompt in the first place.
 */
@Composable
private fun ReadinessRow(note: String?, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val editLabel = stringResource(R.string.readiness_edit_action)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.READINESS_ROW)
            .clickable(onClickLabel = editLabel, onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.readiness_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = note ?: stringResource(R.string.readiness_add),
            style = MaterialTheme.typography.bodyMedium,
            color = if (note == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
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

@Preview(showBackground = true)
@Composable
private fun ActiveWorkoutScreenPreview() {
    AndroidAppTheme {
        ActiveWorkoutScreen(
            state = ActiveWorkoutUiState(
                isLoading = false,
                sessionId = "s1",
                startedAt = "07:42",
                readinessNote = "Shoulders still sore from Monday",
                exercises = listOf(
                    SessionExerciseRow(
                        id = "a",
                        exerciseId = "back-squat",
                        name = "Back Squat",
                        subtitle = "Quads · Barbell",
                        techniqueNote = "Brace, sit back, drive the floor away",
                        restSeconds = 180,
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
            onUpdateSet = { _, _, _, _, _, _, _ -> },
            onRemoveExercise = {},
            onDeleteSet = {},
            onUndoDelete = {},
            onDismissUndo = {},
            onSkipRest = {},
            onAdjustRest = {},
            onSaveReadinessNote = {},
            onDismissReadinessPrompt = {},
            onFinishExercise = { _, _, _, _ -> },
            onRateExercise = { _, _, _, _ -> },
            onUndoFinishExercise = {},
            onDismissFinishUndo = {},
            onReopenExercise = {},
            onFinish = {},
            onDiscard = {},
            onBack = {},
        )
    }
}
