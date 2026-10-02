package com.example.androidapp.ui.home

import com.example.androidapp.domain.DataError
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.transfer.ClearOutcome
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.androidapp.domain.model.zoneIdOrNull
import java.time.ZoneId
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.ui.components.CenteredMessage
import com.example.androidapp.ui.components.ClearEverythingDialog
import com.example.androidapp.ui.components.MessageSnackbar
import com.example.androidapp.ui.components.TestTags
import com.example.androidapp.ui.components.longLabel
import com.example.androidapp.ui.history.HistoryFormat
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.ui.transfer.DataTransferViewModel
import com.example.androidapp.ui.transfer.rememberDataTransferActions
import com.example.androidapp.ui.workout.WorkoutClock
import com.example.androidapp.ui.workout.WorkoutFormat
import java.time.Instant

@Composable
fun WorkoutsHomeRoute(
    onStartWorkout: () -> Unit,
    onStartFromTemplate: () -> Unit,
    onRepeatLast: () -> Unit,
    onStartTemplate: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenTrends: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutsHomeViewModel = hiltViewModel(),
    transferViewModel: DataTransferViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Not unwrapped with `by`: reading it here would rebuild the list every second.
    val clock = viewModel.clock.collectAsStateWithLifecycle()

    // Export and import live here now (ROADMAP B1): they are app-level data
    // management, and the library N1 demoted to a reference screen was the wrong
    // home for them — two overflows deep from where the user starts.
    var message by remember { mutableStateOf<String?>(null) }
    val transferActions = rememberDataTransferActions(transferViewModel) { message = it }
    // The clear is a suspending call whose result is a sentence, so it needs both.
    val scope = rememberCoroutineScope()
    // Resolved at composition, not read from a captured Context: a resource looked up
    // through LocalContext is not configuration-aware (lint's point, and it is right).
    val clearedText = stringResource(R.string.clear_done)
    // `dataErrorMessage` is a composable, so the failure is held as a value and turned
    // into a sentence during composition rather than inside the coroutine.
    var clearFailure by remember { mutableStateOf<DataError?>(null) }
    val clearFailureText = clearFailure?.let { dataErrorMessage(it) }
    LaunchedEffect(clearFailureText) {
        if (clearFailureText != null) {
            message = clearFailureText
            clearFailure = null
        }
    }

    WorkoutsHomeScreen(
        state = state,
        clock = clock,
        onStartWorkout = onStartWorkout,
        onStartFromTemplate = onStartFromTemplate,
        onRepeatLast = onRepeatLast,
        onStartTemplate = onStartTemplate,
        onOpenWorkout = onOpenWorkout,
        onOpenHistory = onOpenHistory,
        onOpenLibrary = onOpenLibrary,
        onOpenTemplates = onOpenTemplates,
        onOpenTrends = onOpenTrends,
        onOpenSettings = onOpenSettings,
        onExportData = transferActions.export,
        onImportData = transferActions.import,
        onClearData = {
            scope.launch {
                when (val outcome = transferViewModel.clearEverything()) {
                    is ClearOutcome.Cleared -> message = clearedText
                    is ClearOutcome.Failed -> clearFailure = outcome.error
                }
            }
        },
        message = message,
        onDismissMessage = { message = null },
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
    onOpenTrends: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    onStartFromTemplate: () -> Unit = {},
    onStartTemplate: (String) -> Unit = {},
    onRepeatLast: () -> Unit = {},
    onExportData: (() -> Unit)? = null,
    onImportData: (() -> Unit)? = null,
    onClearData: (() -> Unit)? = null,
    message: String? = null,
    onDismissMessage: () -> Unit = {},
) {
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    MessageSnackbar(message, snackbarHostState, onDismissMessage)

    if (confirmingClear) {
        ClearEverythingDialog(
            onExport = onExportData,
            onConfirm = {
                confirmingClear = false
                onClearData?.invoke()
            },
            onDismiss = { confirmingClear = false },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeTopBar(
                onOpenSettings = onOpenSettings,
                onOpenLibrary = onOpenLibrary,
                onOpenHistory = onOpenHistory,
                onOpenTemplates = onOpenTemplates,
                onOpenTrends = onOpenTrends,
                onExport = onExportData,
                onImport = onImportData,
                onClear = onClearData?.let { { confirmingClear = true } },
            )
        },
        floatingActionButton = {
            StartActions(
                activeWorkout = state.activeWorkout,
                clock = clock,
                onStartWorkout = onStartWorkout,
                onStartFromTemplate = onStartFromTemplate,
                // No new state: the list the home screen already shows answers this.
                canRepeat = state.canRepeatLast,
                onRepeatLast = onRepeatLast,
            )
        },
    ) { innerPadding ->
        HomeContent(
            state = state,
            onOpenWorkout = onOpenWorkout,
            onOpenHistory = onOpenHistory,
            onStartTemplate = onStartTemplate,
            modifier = Modifier.padding(innerPadding),
        )
    }
}


/**
 * Today's plans and the recent workouts, in one list (ROADMAP N16).
 *
 * Split out of the body because the two lists together are long enough to be their own
 * composable — and because "today" and "recent" are different questions that happen to
 * share a scroll.
 */
@Composable
private fun TodayAndRecent(
    state: WorkoutsHomeUiState,
    onOpenWorkout: (String) -> Unit,
    onStartTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp), // clear the FAB
        ) {
            item(key = "today") {
                Text(
                    text = stringResource(R.string.home_today, state.today.longLabel()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(state.todaysPlans.size, key = { state.todaysPlans[it].id }) { index ->
                val plan = state.todaysPlans[index]
                ListItem(
                    headlineContent = { Text(plan.name) },
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.home_plan_exercises,
                                plan.exerciseCount,
                                plan.exerciseCount,
                            ),
                        )
                    },
                    trailingContent = {
                        TextButton(
                            onClick = { onStartTemplate(plan.id) },
                            modifier = Modifier.testTag(TestTags.homeStartPlan(plan.id)),
                        ) {
                            Text(stringResource(R.string.home_plan_start))
                        }
                    },
                )
                HorizontalDivider()
            }
            if (state.recent.isNotEmpty()) {
                item(key = "recent") {
                    Text(
                        text = stringResource(R.string.home_recent),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(state.recent.size, key = { state.recent[it].id }) { index ->
                    RecentWorkoutRow(
                        workout = state.recent[index],
                        onClick = { onOpenWorkout(state.recent[index].id) },
                    )
                }
            }
        }}

/** The list body, split out so the screen itself stays a scaffold and a state. */
@Composable
private fun HomeContent(
    state: WorkoutsHomeUiState,
    onOpenWorkout: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onStartTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
        when {
            state.isLoading -> CenteredMessage(
                text = stringResource(R.string.history_loading),
                modifier = modifier,
            )

            // First run: an empty list with no explanation tells the user nothing.
            state.todaysPlans.isNotEmpty() -> TodayAndRecent(
                state = state,
                onOpenWorkout = onOpenWorkout,
                onStartTemplate = onStartTemplate,
                modifier = modifier,
            )

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
        headlineContent = {
            // The session's own zone, like history and the workout detail (ROADMAP B33). Omitting it
            // here was a dropped argument rather than missing data, and it made one workout read as
            // two different dates on two screens.
            Text(
                HistoryFormat.date(
                    workout.startedAt,
                    zone = workout.zoneIdOrNull() ?: ZoneId.systemDefault(),
                ),
            )
        },
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
    /** Whether a finished workout exists to repeat (ROADMAP N29). */
    canRepeat: Boolean = false,
    onRepeatLast: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        if (activeWorkout == null) {
            if (canRepeat) {
                TextButton(
                    onClick = onRepeatLast,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(TestTags.HOME_REPEAT_LAST),
                ) {
                    Text(stringResource(R.string.home_repeat_last))
                }
            }
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
    onOpenTrends: () -> Unit,
    onExport: (() -> Unit)?,
    onImport: (() -> Unit)?,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
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
                HomeMenuItems(
                    onOpenLibrary = onOpenLibrary,
                    onOpenHistory = onOpenHistory,
                    onOpenTemplates = onOpenTemplates,
                    onOpenTrends = onOpenTrends,
                    onOpenSettings = onOpenSettings,
                    onExport = onExport,
                    onImport = onImport,
                    onClear = onClear,
                    onDismiss = { menuOpen = false },
                )
            }
        },
    )
}

/**
 * The app's data actions, together and in the order that matters (ROADMAP B1, N18).
 *
 * Export first, then import, then the one that cannot be undone — and that one is last
 * and coloured, because it is the only entry here that can cost the user something.
 */
@Composable
private fun DataActions(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transfer_export)) },
                    onClick = {
                        onDismiss()
                        onExport()
                    },
                    modifier = Modifier.testTag(TestTags.DATA_EXPORT),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transfer_import)) },
                    onClick = {
                        onDismiss()
                        onImport()
                    },
                    modifier = Modifier.testTag(TestTags.DATA_IMPORT),
                )
                if (onClear != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.clear_menu),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            onDismiss()
                            onClear()
                        },
                        modifier = Modifier.testTag(TestTags.HOME_CLEAR_DATA),
                    )
                }
    }
}

/**
 * The menu's entries, split out so the app bar stays a title and a button.
 *
 * The data actions are the newest members (ROADMAP B1, N18) and are null on a preview or
 * a screen test that does not exercise them — so the menu is exactly as long as it has
 * something to offer.
 */
@Composable
private fun HomeMenuItems(
    onOpenLibrary: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenTrends: () -> Unit,
    onExport: (() -> Unit)?,
    onImport: (() -> Unit)?,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    Column {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.exercise_library_title)) },
            onClick = {
                onDismiss()
                onOpenLibrary()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.history_title)) },
            onClick = {
                onDismiss()
                onOpenHistory()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_templates)) },
            onClick = {
                onDismiss()
                onOpenTemplates()
            },
            modifier = Modifier.testTag(TestTags.HOME_TEMPLATES),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.home_trends)) },
            onClick = {
                onDismiss()
                onOpenTrends()
            },
            modifier = Modifier.testTag(TestTags.HOME_TRENDS),
        )
        if (onOpenSettings != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_menu)) },
                onClick = {
                    onDismiss()
                    onOpenSettings()
                },
                modifier = Modifier.testTag(TestTags.HOME_SETTINGS),
            )
        }
        // Export and import, moved down from the library (ROADMAP B1).
        if (onExport != null && onImport != null) {
            DataActions(
                onExport = onExport,
                onImport = onImport,
                onClear = onClear,
                onDismiss = onDismiss,
            )
        }
    }
}

