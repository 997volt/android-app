package com.example.androidapp.ui.workout

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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

    // rememberUpdatedState, because the effect below restarts on `closed`:
    // reading the lambda parameter directly would capture whichever `onDone` was
    // current when the effect last started.
    val currentOnDone by rememberUpdatedState(onDone)

    // Finish/discard leaves the screen rather than stranding the user on a
    // now-empty workout.
    LaunchedEffect(closed) {
        if (closed) currentOnDone()
    }

    ActiveWorkoutScreen(
        state = state,
        onAddExercise = onAddExercise,
        onRemoveExercise = viewModel::onRemoveExercise,
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
    onAddExercise: () -> Unit,
    onRemoveExercise: (String) -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
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
                    TextButton(
                        onClick = onFinish,
                        // Nothing to finish when the session has no exercises.
                        enabled = state.exercises.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.active_workout_finish))
                    }
                },
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
            onRemoveExercise = onRemoveExercise,
            onDiscard = onDiscard,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** The workout itself, split out so the screen composable stays readable. */
@Composable
private fun WorkoutBody(
    state: ActiveWorkoutUiState,
    onRemoveExercise: (String) -> Unit,
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
            state.isLoading -> CenteredMessage(
                text = stringResource(R.string.active_workout_loading),
                showSpinner = true,
            )

            state.hasNoSession -> CenteredMessage(
                text = stringResource(R.string.active_workout_none),
                showSpinner = false,
            )

            state.isEmpty -> {
                WorkoutHeader(startedAt = state.startedAt, elapsed = state.elapsed)
                HorizontalDivider()
                EmptyWorkout(onDiscard = onDiscard)
            }

            else -> {
                WorkoutHeader(startedAt = state.startedAt, elapsed = state.elapsed)
                HorizontalDivider()
                ExerciseRowList(rows = state.exercises, onRemoveExercise = onRemoveExercise)
            }
        }
    }
}

@Composable
private fun ExerciseRowList(
    rows: List<SessionExerciseRow>,
    onRemoveExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Leaves room for the extended FAB so it cannot cover the last row.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(items = rows, key = { it.id }) { row ->
            ListItem(
                headlineContent = { Text(row.name) },
                supportingContent = { Text("${row.muscleLabel} · ${row.equipmentLabel}") },
                trailingContent = {
                    IconButton(onClick = { onRemoveExercise(row.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.active_workout_remove, row.name),
                        )
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun WorkoutHeader(startedAt: String, elapsed: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.active_workout_started, startedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = elapsed, style = MaterialTheme.typography.titleMedium)
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
                elapsed = "12:05",
                exercises = listOf(
                    SessionExerciseRow("a", "back-squat", "Back Squat", "Quads", "Barbell"),
                    SessionExerciseRow("b", "barbell-row", "Barbell Row", "Back", "Barbell"),
                ),
            ),
            onAddExercise = {},
            onRemoveExercise = {},
            onFinish = {},
            onDiscard = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ActiveWorkoutEmptyPreview() {
    AndroidAppTheme {
        ActiveWorkoutScreen(
            state = ActiveWorkoutUiState(
                isLoading = false,
                sessionId = "s1",
                startedAt = "07:42",
                elapsed = "0:03",
            ),
            onAddExercise = {},
            onRemoveExercise = {},
            onFinish = {},
            onDiscard = {},
            onBack = {},
        )
    }
}
