package com.example.androidapp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.history.HistoryFormat
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.ui.workout.WorkoutClock
import com.example.androidapp.ui.workout.WorkoutFormat
import java.time.Instant

@Composable
fun WorkoutsHomeRoute(
    onStartWorkout: () -> Unit,
    onStartFromTemplate: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenTemplates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Not unwrapped with `by`: reading it here would rebuild the list every second.
    val clock = viewModel.clock.collectAsStateWithLifecycle()

    WorkoutsHomeScreen(
        state = state,
        clock = clock,
        onStartWorkout = onStartWorkout,
        onStartFromTemplate = onStartFromTemplate,
        onOpenWorkout = onOpenWorkout,
        onOpenHistory = onOpenHistory,
        onOpenLibrary = onOpenLibrary,
        onOpenTemplates = onOpenTemplates,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsHomeScreen(
    state: WorkoutsHomeUiState,
    clock: State<WorkoutClock>,
    onStartWorkout: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenTemplates: () -> Unit,
    modifier: Modifier = Modifier,
    onStartFromTemplate: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            HomeTopBar(
                onOpenLibrary = onOpenLibrary,
                onOpenHistory = onOpenHistory,
                onOpenTemplates = onOpenTemplates,
            )
        },
        floatingActionButton = {
            StartActions(
                activeWorkout = state.activeWorkout,
                clock = clock,
                onStartWorkout = onStartWorkout,
                onStartFromTemplate = onStartFromTemplate,
            )
        },
    ) { innerPadding ->
        HomeContent(
            state = state,
            onOpenWorkout = onOpenWorkout,
            onOpenHistory = onOpenHistory,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** The list body, split out so the screen itself stays a scaffold and a state. */
@Composable
private fun HomeContent(
    state: WorkoutsHomeUiState,
    onOpenWorkout: (String) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
        when {
            state.isLoading -> CenteredMessage(
                text = stringResource(R.string.history_loading),
                modifier = modifier,
            )

            // First run: an empty list with no explanation tells the user nothing.
            state.isFirstRun -> CenteredMessage(
                text = stringResource(R.string.home_first_run),
                hint = stringResource(R.string.home_first_run_hint),
                modifier = modifier.testTag(TestTags.HOME_FIRST_RUN),
            )

            state.recent.isEmpty() -> CenteredMessage(
                text = stringResource(R.string.home_no_recent),
                modifier = modifier.testTag(TestTags.HOME_NO_RECENT),
            )

            else -> LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp), // clear the FAB
            ) {
                item(key = "header") {
                    Text(
                        text = stringResource(R.string.home_recent),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(state.recent.size, key = { state.recent[it].id }) { index ->
                    val workout = state.recent[index]
                    RecentWorkoutRow(workout = workout, onClick = { onOpenWorkout(workout.id) })
                    HorizontalDivider()
                }
                item(key = "see-all") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.home_see_all)) },
                        modifier = Modifier
                            .testTag(TestTags.HOME_SEE_ALL)
                            .clickable(onClick = onOpenHistory),
                    )
                }
            }
        }
}

@Composable
private fun RecentWorkoutRow(
    workout: WorkoutSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setCount = pluralStringResource(R.plurals.history_sets, workout.setCount, workout.setCount)

    ListItem(
        headlineContent = { Text(HistoryFormat.date(workout.startedAt)) },
        supportingContent = {
            val duration = workout.duration?.let { WorkoutFormat.elapsed(it) }.orEmpty()
            val volume = stringResource(R.string.history_volume, Weight.kilograms(workout.volumeGrams))
            Text(listOf(duration, setCount, volume).filter { it.isNotEmpty() }.joinToString(" · "))
        },
        modifier = modifier.testTag(TestTags.HOME_RECENT_ROW).clickable(onClick = onClick),
    )
}

/**
 * The home start action: **Start workout** for an empty session, and — while no
 * workout is open — **Start from template** beneath it (ROADMAP N3).
 *
 * Resuming offers no such choice: there is exactly one workout in progress, so the
 * button means one thing. The pair only appears when the user is actually choosing.
 */
@Composable
private fun StartActions(
    activeWorkout: ActiveWorkoutInfo?,
    clock: State<WorkoutClock>,
    onStartWorkout: () -> Unit,
    onStartFromTemplate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        if (activeWorkout == null) {
            FilledTonalButton(
                onClick = onStartFromTemplate,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag(TestTags.HOME_START_FROM_TEMPLATE),
            ) {
                Text(stringResource(R.string.home_start_from_template))
            }
        }
        StartOrResumeButton(
            activeWorkout = activeWorkout,
            clock = clock,
            onClick = onStartWorkout,
        )
    }
}

/**
 * The home screen's primary action (P1.16, moved here by N1).
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
        // Tagged by state, not caption: which of the two shows is the behaviour
        // under test, and the captions are user-visible text a translation changes.
        modifier = modifier.testTag(
            if (resuming) TestTags.HOME_RESUME else TestTags.HOME_START,
        ),
        onClick = onClick,
        text = {
            Text(
                text = if (resuming) {
                    listOf(
                        stringResource(R.string.library_resume_workout),
                        elapsed,
                        exercises,
                    ).filter { it.isNotEmpty() }.joinToString(" · ")
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

@Preview(showBackground = true)
@Composable
private fun WorkoutsHomeScreenPreview() {
    AndroidAppTheme {
        WorkoutsHomeScreen(
            state = WorkoutsHomeUiState(
                isLoading = false,
                recent = listOf(
                    WorkoutSummary(
                        id = "a",
                        startedAt = Instant.parse("2026-09-28T07:00:00Z"),
                        finishedAt = Instant.parse("2026-09-28T08:05:00Z"),
                        exerciseCount = 4,
                        setCount = 16,
                        volumeGrams = 12_500_000L,
                    ),
                ),
            ),
            clock = remember { mutableStateOf(WorkoutClock()) },
            onStartWorkout = {},
            onStartFromTemplate = {},
            onOpenWorkout = {},
            onOpenHistory = {},
            onOpenLibrary = {},
            onOpenTemplates = {},
        )
    }
}

/**
 * Home's app bar: the title, and navigation to the two things that are *not* home.
 *
 * The library moved here from being the start destination, which is the point of N1
 * — it is somewhere you go, not somewhere you land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    onOpenLibrary: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTemplates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.home_title),
                modifier = Modifier.testTag(TestTags.HOME_TITLE),
            )
        },
        actions = {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.testTag(TestTags.HOME_MENU),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.transfer_more),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exercise_library_title)) },
                    onClick = {
                        menuOpen = false
                        onOpenLibrary()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_title)) },
                    onClick = {
                        menuOpen = false
                        onOpenHistory()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_templates)) },
                    onClick = {
                        menuOpen = false
                        onOpenTemplates()
                    },
                    modifier = Modifier.testTag(TestTags.HOME_TEMPLATES),
                )
            }
        },
    )
}
