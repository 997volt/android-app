package com.example.androidapp.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.ui.transfer.DataTransferViewModel
import com.example.androidapp.ui.workout.WorkoutClock
import com.example.androidapp.ui.transfer.rememberDataTransferActions

/**
 * Stateful entry point: wires the ViewModel to the stateless screen.
 *
 * Keeping the split means [ExerciseLibraryScreen] can be driven by a fixed
 * state in a UI test with no Hilt container and no repository.
 */
@Composable
fun ExerciseLibraryRoute(
    onExerciseClick: (String) -> Unit,
    onStartWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
    transferViewModel: DataTransferViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Not unwrapped with `by`: reading it here would rebuild the list every second.
    val clock = viewModel.clock.collectAsStateWithLifecycle()

    // Held here rather than in the screen so the screen stays stateless and
    // testable with a fixed string.
    var transferMessage by remember { mutableStateOf<String?>(null) }
    val transferActions = rememberDataTransferActions(transferViewModel) { transferMessage = it }

    ExerciseLibraryScreen(
        state = state,
        title = stringResource(R.string.exercise_library_title),
        clock = clock,
        onQueryChange = viewModel::onQueryChange,
        onExerciseClick = onExerciseClick,
        onStartWorkout = onStartWorkout,
        onOpenHistory = onOpenHistory,
        onExportData = transferActions.export,
        onImportData = transferActions.import,
        transferMessage = transferMessage,
        onDismissTransferMessage = { transferMessage = null },
        modifier = modifier,
    )
}

/**
 * The library list.
 *
 * [onBack] and [onStartWorkout] are optional so the same composable serves both
 * the standalone library destination and the in-workout exercise picker, which
 * differs only in its title and what a tap does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    state: ExerciseLibraryUiState,
    clock: State<WorkoutClock>,
    title: String,
    onQueryChange: (String) -> Unit,
    onExerciseClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onStartWorkout: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onExportData: (() -> Unit)? = null,
    onImportData: (() -> Unit)? = null,
    transferMessage: String? = null,
    onDismissTransferMessage: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ShowTransferMessage(transferMessage, snackbarHostState, onDismissTransferMessage)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back),
                            )
                        }
                    }
                },
                actions = {
                    // Only the real library offers the data menu; the picker reuses
                    // this screen with neither callback, so no menu appears there.
                    if (onExportData != null && onImportData != null) {
                        LibraryDataMenu(
                            onOpenHistory = onOpenHistory,
                            onExport = onExportData,
                            onImport = onImportData,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (onStartWorkout != null) {
                StartOrResumeButton(
                    activeWorkout = state.activeWorkout,
                    clock = clock,
                    onClick = onStartWorkout,
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LibrarySearchField(query = state.query, onQueryChange = onQueryChange)

            when {
                state.isLoading -> LoadingState()
                state.isEmpty -> EmptyState(query = state.query)
                else -> ExerciseList(items = state.items, onExerciseClick = onExerciseClick)
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.exercise_library_loading),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun EmptyState(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.exercise_library_empty, query),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * Shows the transfer result once, then clears it.
 *
 * `LaunchedEffect` on the message rather than a one-shot event channel: the
 * message is already state the route owns, and showing a snackbar is idempotent
 * for a given string.
 */
@Composable
private fun ShowTransferMessage(
    message: String?,
    hostState: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        hostState.showSnackbar(message)
        currentOnDismiss()
    }
}

/** Export/import, behind an overflow so the app bar stays quiet (P1.12). */
@Composable
private fun LibraryDataMenu(
    onOpenHistory: (() -> Unit)?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    // Boxed so the menu anchors to the button: a composable emitting two siblings
    // at the top level has no defined anchor for the popup.
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.transfer_more),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onOpenHistory != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_title)) },
                    onClick = {
                        open = false
                        onOpenHistory()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.transfer_export)) },
                onClick = {
                    open = false
                    onExport()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.transfer_import)) },
                onClick = {
                    open = false
                    onImport()
                },
            )
        }
    }
}

/**
 * The library's primary action (ROADMAP P1.16).
 *
 * Reads the clock — and is the only composable here that does, so the one-second
 * tick stops at this button instead of rebuilding the list beneath it.
 */
@Composable
private fun StartOrResumeButton(
    activeWorkout: ActiveWorkoutInfo?,
    clock: State<WorkoutClock>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resuming = activeWorkout != null
    val elapsed = if (resuming) clock.value.elapsed else ""
    val exercises = if (resuming) {
        pluralStringResource(
            R.plurals.library_exercises,
            activeWorkout.exerciseCount,
            activeWorkout.exerciseCount,
        )
    } else {
        ""
    }

    ExtendedFloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        text = {
            Text(
                text = if (resuming) {
                    listOf(
                        stringResource(R.string.library_resume_workout),
                        elapsed,
                        exercises,
                    ).filter { it.isNotEmpty() }.joinToString(" \u00b7 ")
                } else {
                    stringResource(R.string.library_start_workout)
                },
            )
        },
        icon = {
            Icon(
                imageVector = if (resuming) Icons.Filled.PlayArrow else Icons.Filled.Add,
                contentDescription = null,
            )
        },
    )
}

/** The library's search box, split out so the screen composable stays readable. */
@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        label = { Text(stringResource(R.string.exercise_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.exercise_search_clear),
                    )
                }
            }
        },
    )
}

@Composable
private fun ExerciseList(
    items: List<ExerciseListItem>,
    onExerciseClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Leaves room for the "start workout" button so it cannot cover the last row.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(items = items, key = { it.id }) { item ->
            ListItem(
                headlineContent = { Text(item.name) },
                supportingContent = { Text("${item.muscleLabel} · ${item.equipmentLabel}") },
                modifier = Modifier.clickable { onExerciseClick(item.id) },
            )
            HorizontalDivider()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseLibraryScreenPreview() {
    AndroidAppTheme {
        ExerciseLibraryScreen(
            state = ExerciseLibraryUiState(
                query = "",
                isLoading = false,
                items = listOf(
                    ExerciseListItem("back-squat", "Back Squat", "Quads", "Barbell"),
                    ExerciseListItem("bench-press", "Barbell Bench Press", "Chest", "Barbell"),
                ),
            ),
            clock = remember { mutableStateOf(WorkoutClock()) },
            title = "Exercise library",
            onQueryChange = {},
            onExerciseClick = {},
            onStartWorkout = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseLibraryEmptyPreview() {
    AndroidAppTheme {
        ExerciseLibraryScreen(
            state = ExerciseLibraryUiState(query = "zzz", isLoading = false, items = emptyList()),
            clock = remember { mutableStateOf(WorkoutClock()) },
            title = "Exercise library",
            onQueryChange = {},
            onExerciseClick = {},
        )
    }
}
