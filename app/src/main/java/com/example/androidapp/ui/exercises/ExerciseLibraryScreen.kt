package com.example.androidapp.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.ui.theme.AndroidAppTheme

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
    modifier: Modifier = Modifier,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseLibraryScreen(
        state = state,
        title = stringResource(R.string.exercise_library_title),
        onQueryChange = viewModel::onQueryChange,
        onExerciseClick = onExerciseClick,
        onStartWorkout = onStartWorkout,
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
    title: String,
    onQueryChange: (String) -> Unit,
    onExerciseClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onStartWorkout: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            )
        },
        floatingActionButton = {
            if (onStartWorkout != null) {
                ExtendedFloatingActionButton(
                    onClick = onStartWorkout,
                    text = { Text(stringResource(R.string.library_start_workout)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.exercise_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.exercise_search_clear),
                            )
                        }
                    }
                },
            )

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
            title = "Exercise library",
            onQueryChange = {},
            onExerciseClick = {},
        )
    }
}
