package com.example.androidapp.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.ui.exercises.ExerciseLibraryScreen

/**
 * The exercise picker reuses the library screen wholesale — same search, same
 * rows — because choosing an exercise from the library and choosing one to add
 * to a workout are the same decision, differing only in what a tap does.
 */
@Composable
fun ExercisePickerRoute(
    onAddExercise: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val added by viewModel.added.collectAsStateWithLifecycle()

    // rememberUpdatedState, because the effect restarts on `added`: reading the
    // lambda parameter directly would capture a stale callback.
    val currentOnAddExercise by rememberUpdatedState(onAddExercise)

    LaunchedEffect(added) {
        if (added) currentOnAddExercise()
    }

    ExerciseLibraryScreen(
        state = state,
        // The picker has no start/resume button, so its clock never ticks.
        clock = remember { mutableStateOf(WorkoutClock()) },
        title = stringResource(R.string.picker_title),
        onQueryChange = viewModel::onQueryChange,
        onExerciseClick = viewModel::onExerciseSelected,
        onBack = onBack,
        modifier = modifier,
    )
}
