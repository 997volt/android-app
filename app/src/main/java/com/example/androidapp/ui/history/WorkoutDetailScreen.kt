package com.example.androidapp.ui.history

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
import com.example.androidapp.ui.components.SetEditorDialog
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.ui.workout.WorkoutFormat
import java.time.Instant

@Composable
fun WorkoutDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
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
        state = state,
        onUpdateSet = viewModel::onUpdateSet,
        onDeleteSet = viewModel::onDeleteSet,
        onDeleteWorkout = viewModel::onDeleteWorkout,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onUpdateSet: (String, Int, Long) -> Unit,
    onDeleteSet: (String) -> Unit,
    onDeleteWorkout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
            state = state,
            onEditSet = { editing = it },
            onDeleteSet = onDeleteSet,
            modifier = Modifier.padding(innerPadding),
        )
    }

    editing?.let { set ->
        SetEditorDialog(
            initialReps = set.reps,
            initialWeightGrams = set.weightGrams,
            onDismiss = { editing = null },
            onSave = { reps, weightGrams ->
                onUpdateSet(set.id, reps, weightGrams)
                editing = null
            },
        )
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
                text = session?.let { HistoryFormat.date(it.startedAt) }
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
                items(items = state.exercises, key = { it.id }) { exercise ->
                    ExerciseBlock(
                        exercise = exercise,
                        onEditSet = onEditSet,
                        onDeleteSet = { onDeleteSet(it.id) },
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
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(R.string.set_edit_action)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = exercise.name, style = MaterialTheme.typography.titleMedium)

        exercise.sets.forEachIndexed { index, set ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.SET_ROW)
                        .clickable(onClickLabel = editLabel) { onEditSet(set) },
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "${index + 1}",
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
                IconButton(onClick = { onDeleteSet(set) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.set_delete),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
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
                ),
                exercises = listOf(
                    HistoryExercise(
                        id = "se1",
                        name = "Back Squat",
                        sets = listOf(
                            HistorySet("1", reps = 5, weightGrams = 100_000),
                            HistorySet("2", reps = 5, weightGrams = 100_000),
                        ),
                    ),
                ),
            ),
            onUpdateSet = { _, _, _ -> },
            onDeleteSet = {},
            onDeleteWorkout = {},
            onBack = {},
        )
    }
}
