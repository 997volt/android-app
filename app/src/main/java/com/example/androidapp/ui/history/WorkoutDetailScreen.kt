package com.example.androidapp.ui.history

import com.example.androidapp.domain.model.zoneIdOrNull
import com.example.androidapp.domain.model.SetType
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.ExerciseRatingSection
import com.example.androidapp.ui.components.SetEditorDialog
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.rpeMarker
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.ui.workout.WorkoutFormat
import java.time.ZoneId
import java.time.Instant

@Composable
fun WorkoutDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
    onOpenExerciseTrends: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()

    // Deleting the workout leaves nothing to look at, so the screen closes itself
    // rather than sitting on "no longer stored".
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(deleted) {
        if (deleted) currentOnBack()
    }

    WorkoutDetailScreen(
        onOpenExerciseTrends = onOpenExerciseTrends,
        state = state,
        onUpdateSet = viewModel::onUpdateSet,
        onDeleteSet = viewModel::onDeleteSet,
        onRateExercise = viewModel::onRateExercise,
        onDeleteWorkout = viewModel::onDeleteWorkout,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onUpdateSet: (String, Int, Long, Int?, String?, SetType, Long) -> Unit,
    onDeleteSet: (String) -> Unit,
    onRateExercise: (String, Int?, Int?, String?) -> Unit,
    onDeleteWorkout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenExerciseTrends: (String) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    state.error?.let { error ->
        val message = dataErrorMessage(error)
        LaunchedEffect(message) { snackbarHostState.showSnackbar(message) }
    }

    var editing by remember { mutableStateOf<HistorySet?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DetailTopBar(
                session = state.session,
                onDelete = { confirmingDelete = true },
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        DetailContent(
            onOpenExerciseTrends = onOpenExerciseTrends,
            state = state,
            onEditSet = { editing = it },
            onDeleteSet = onDeleteSet,
            onRate = onRateExercise,
            modifier = Modifier.padding(innerPadding),
        )
    }

    editing?.let { set ->
        EditSetDialog(set = set, onDismiss = { editing = null }, onUpdateSet = onUpdateSet)
    }

    if (confirmingDelete) {
        DeleteWorkoutDialog(
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                confirmingDelete = false
                onDeleteWorkout()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    session: WorkoutSession?,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = session?.let {
                    HistoryFormat.date(it.startedAt, zone = it.zoneIdOrNull() ?: ZoneId.systemDefault())
                }
                    ?: stringResource(R.string.history_detail_title),
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
            // Only offer deletion once there is something to delete.
            if (session != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.history_delete_workout),
                    )
                }
            }
        },
    )
}

@Composable
private fun DetailContent(
    state: WorkoutDetailUiState,
    onEditSet: (HistorySet) -> Unit,
    onDeleteSet: (String) -> Unit,
    onRate: (String, Int?, Int?, String?) -> Unit,
    onOpenExerciseTrends: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> DetailMessage(
            text = stringResource(R.string.history_loading),
            modifier = modifier,
        )

        state.notFound -> DetailMessage(
            text = stringResource(R.string.history_detail_missing),
            modifier = modifier,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "totals") {
                Totals(state)
                HorizontalDivider()
            }
            // The readiness note rides through history (ROADMAP N4). Only shown
            // when there is one: an empty block on every past workout would be
            // noise, not information.
            state.session?.readinessNote?.let { note ->
                item(key = "readiness") {
                    NoteBlock(
                        title = stringResource(R.string.readiness_label),
                        note = note,
                    )
                    HorizontalDivider()
                }
            }
            // The workout's own comment (ROADMAP N11), on the same terms as the
            // readiness note: shown only when there is one.
            state.session?.notes?.let { comment ->
                item(key = "comment") {
                    NoteBlock(
                        title = stringResource(R.string.workout_note_row),
                        note = comment,
                    )
                    HorizontalDivider()
                }
            }
            items(items = state.exercises, key = { it.id }) { exercise ->
                    ExerciseBlock(
                        onOpenTrends = onOpenExerciseTrends,
                        exercise = exercise,
                        onEditSet = onEditSet,
                        onDeleteSet = { onDeleteSet(it.id) },
                        onRate = onRate,
                    )
                    HorizontalDivider()
                }
        }
    }
}

/**
 * Confirms before a destructive, in-app-irreversible action.
 *
 * The delete is a soft one, so an exported backup would carry the rows — but there
 * is no restore in the UI, so from the user's side this is permanent.
 */
@Composable
private fun DeleteWorkoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_delete_confirm_title)) },
        text = { Text(stringResource(R.string.history_delete_confirm_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.history_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.history_cancel))
            }
        },
    )
}

/** A free-text note on the workout: its readiness note (N4) or its comment (N11). */
@Composable
private fun NoteBlock(title: String, note: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = note, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Totals(state: WorkoutDetailUiState, modifier: Modifier = Modifier) {
    val sets = pluralStringResource(R.plurals.history_sets, state.setCount, state.setCount)
    val duration = state.duration?.let { WorkoutFormat.elapsed(it) }.orEmpty()

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = duration, style = MaterialTheme.typography.titleMedium)
        Text(text = sets, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.history_volume, Weight.kilograms(state.volumeGrams)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ExerciseBlock(
    exercise: HistoryExercise,
    onEditSet: (HistorySet) -> Unit,
    onDeleteSet: (HistorySet) -> Unit,
    onRate: (String, Int?, Int?, String?) -> Unit,
    onOpenTrends: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(R.string.set_edit_action)
    val trendsLabel = stringResource(R.string.exercise_trends_open)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .testTag(TestTags.historyExerciseTrends(exercise.id))
                .clickable(onClickLabel = trendsLabel) { onOpenTrends(exercise.exerciseId) },
        )

        exercise.sets.forEachIndexed { index, set ->
            HistorySetRow(
                set = set,
                number = index + 1,
                editLabel = editLabel,
                onEditSet = onEditSet,
                onDeleteSet = onDeleteSet,
            )
        }

        ExerciseRatingSection(
            muscleFeel = exercise.muscleFeel,
            jointPain = exercise.jointPain,
            jointPainNote = exercise.jointPainNote,
            onRate = { feel, pain, note -> onRate(exercise.id, feel, pain, note) },
        )
    }
}


/** One logged set on the detail screen: its numbers, its N6 extras, and its delete. */
@Composable
private fun HistorySetRow(
    set: HistorySet,
    number: Int,
    editLabel: String,
    onEditSet: (HistorySet) -> Unit,
    onDeleteSet: (HistorySet) -> Unit,
) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag(TestTags.SET_ROW)
                    .clickable(onClickLabel = editLabel) { onEditSet(set) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "$number",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.set_summary,
                            Weight.display(set.weightGrams, set.assistanceGrams),
                            set.reps,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // The detail is where the full text lives (N6); the workout
                    // row only carries a marker.
                    set.rpeHalves?.let { rpeHalves ->
                        Text(
                            text = rpeMarker(rpeHalves),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                set.note?.let { comment ->
                    Text(
                        text = comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { onDeleteSet(set) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.set_delete),
                )
            }
        }
    
}

@Composable
private fun DetailMessage(text: String, modifier: Modifier = Modifier) {
    CenteredMessage(text = text, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun WorkoutDetailScreenPreview() {
    AndroidAppTheme {
        WorkoutDetailScreen(
            state = WorkoutDetailUiState(
                isLoading = false,
                session = WorkoutSession(
                    id = "a",
                    startedAt = Instant.parse("2026-09-28T07:00:00Z"),
                    finishedAt = Instant.parse("2026-09-28T08:05:00Z"),
                    readinessNote = "Slept badly, legs heavy",
                ),
                exercises = listOf(
                    HistoryExercise(
                        id = "se1",
                        exerciseId = "back-squat",
                        name = "Back Squat",
                        sets = listOf(
                            HistorySet("1", reps = 5, weightGrams = 100_000),
                            HistorySet("2", reps = 5, weightGrams = 100_000),
                        ),
                    ),
                ),
            ),
            onUpdateSet = { _, _, _, _, _, _, _ -> },
            onDeleteSet = {},
            onRateExercise = { _, _, _, _ -> },
            onDeleteWorkout = {},
            onBack = {},
        )
    }
}

/**
 * The editor for one logged set (ROADMAP N6), and the reason it is its own composable: the
 * screen around it is at the length this project allows, and the parameters it must be given
 * are the ones B14 showed matter — the stored role and assistance among them.
 */
@Composable
private fun EditSetDialog(
    set: HistorySet,
    onDismiss: () -> Unit,
    onUpdateSet: (String, Int, Long, Int?, String?, SetType, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    SetEditorDialog(
        modifier = modifier,
        initialReps = set.reps,
        initialWeightGrams = set.weightGrams,
        initialRpe = set.rpeHalves,
        initialNote = set.note,
        initialSetType = set.setType,
        initialAssistanceGrams = set.assistanceGrams,
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
