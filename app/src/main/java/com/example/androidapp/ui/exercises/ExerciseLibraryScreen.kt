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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.MessageSnackbar
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.components.TestTags
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
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ExerciseLibraryScreen(
        state = state,
        title = stringResource(R.string.exercise_library_title),
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onExerciseClick = onExerciseClick,
        onOpenHistory = onOpenHistory,
        modifier = modifier,
    )
}

/**
 * The library list.
 *
 * [onBack] and [onNewExercise] are optional so the same composable serves both
 * the standalone library destination and the in-workout exercise picker, which
 * differs in its title, what a tap does, and — for the picker — the offer to
 * create a new exercise (ROADMAP N2).
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
    onOpenHistory: (() -> Unit)? = null,
    message: String? = null,
    onDismissMessage: () -> Unit = {},
    onNewExercise: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageSnackbar(message, snackbarHostState, onDismissMessage)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Only the picker offers creation: the library is a reference you
            // navigate to, while the gap is felt mid-workout (ROADMAP N2).
            if (onNewExercise != null) {
                ExtendedFloatingActionButton(
                    onClick = onNewExercise,
                    text = { Text(stringResource(R.string.exercise_new)) },
                    icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier.testTag(TestTags.LIBRARY_NEW_EXERCISE),
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.testTag(TestTags.LIBRARY_TITLE)) },
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
                    // Only the real library offers this menu; the picker reuses the
                    // screen with no callback, so no menu appears there. Export and
                    // import are *not* here any more: they belong with the app's
                    // other data management at home (ROADMAP B1).
                    if (onOpenHistory != null) {
                        LibraryMenu(onOpenHistory = onOpenHistory)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LibrarySearchField(query = state.query, onQueryChange = onQueryChange)

            when {
                state.isLoading -> LoadingState()

                // A failed read is shown here, where the list would have been (B4):
                // an empty list would misreport what is on the device.
                state.error != null -> CenteredMessage(
                    text = dataErrorMessage(state.error),
                    modifier = Modifier.testTag(TestTags.LIBRARY_READ_ERROR),
                )

                state.isEmpty -> EmptyState(
                    query = state.query,
                    libraryIsEmpty = state.libraryIsEmpty,
                )
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
private fun EmptyState(
    query: String,
    libraryIsEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    // An empty library and a search with no hits look the same unless the screen
    // distinguishes them, and the user's next move differs: one wants the search
    // cleared, the other is not about the search at all.
    if (libraryIsEmpty) {
        CenteredMessage(
            text = stringResource(R.string.exercise_library_no_exercises),
            hint = stringResource(R.string.exercise_library_no_exercises_hint),
            modifier = modifier.testTag(TestTags.LIBRARY_EMPTY_LIBRARY),
        )
    } else {
        CenteredMessage(
            text = stringResource(R.string.exercise_library_empty, query),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = modifier.testTag(TestTags.LIBRARY_NO_MATCH),
        )
    }
}

/** The library's overflow. History only: the data menu moved home (ROADMAP B1). */
@Composable
private fun LibraryMenu(
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    // Boxed so the menu anchors to the button: a composable emitting two siblings
    // at the top level has no defined anchor for the popup.
    Box(modifier = modifier) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.testTag(TestTags.LIBRARY_MENU),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.transfer_more),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_title)) },
                onClick = {
                    open = false
                    onOpenHistory()
                },
                modifier = Modifier.testTag(TestTags.LIBRARY_HISTORY),
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(TestTags.LIBRARY_SEARCH_FIELD),
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
                // Null while an unedited custom exercise has no taxonomy: the row
                // shows its name alone rather than "Other · Other" (N2).
                supportingContent = item.subtitle?.let { subtitle -> { Text(subtitle) } },
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
                    ExerciseListItem("back-squat", "Back Squat", "Quads · Barbell"),
                    ExerciseListItem("bench-press", "Barbell Bench Press", "Chest · Barbell"),
                    ExerciseListItem("my-lift", "Sled Push", null),
                ),
            ),
            title = "Exercise library",
            onQueryChange = {},
            onExerciseClick = {},
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
