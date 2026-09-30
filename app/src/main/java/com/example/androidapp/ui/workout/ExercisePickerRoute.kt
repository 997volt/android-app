package com.example.androidapp.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.ui.components.NewExerciseDialog
import com.example.androidapp.ui.components.dataErrorMessage
import com.example.androidapp.ui.exercises.ExerciseLibraryScreen

/**
 * The exercise picker reuses the library screen wholesale — same search, same
 * rows — because choosing an exercise from the library and choosing one to add
 * to a workout are the same decision, differing only in what a tap does.
 *
 * It adds one thing the standalone library does not have: the offer to create a
 * custom exercise here, where the gap is actually felt (ROADMAP N2).
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
    val error by viewModel.error.collectAsStateWithLifecycle()

    // rememberUpdatedState, because the effect restarts on `added`: reading the
    // lambda parameter directly would capture a stale callback.
    val currentOnAddExercise by rememberUpdatedState(onAddExercise)

    LaunchedEffect(added) {
        if (added) currentOnAddExercise()
    }

    // Whether the naming dialog is up. Held here rather than in the ViewModel
    // because it is transient UI state: a process death that loses it loses
    // nothing the user typed before pressing Add.
    var naming by remember { mutableStateOf(false) }

    ExerciseLibraryScreen(
        state = state,
        title = stringResource(R.string.picker_title),
        onQueryChange = viewModel::onQueryChange,
        onExerciseClick = viewModel::onExerciseSelected,
        onBack = onBack,
        onNewExercise = { naming = true },
        // A create failure is shown inside the dialog, which is on top of the
        // snackbar. Anything else — a tap whose append failed — has no dialog, so
        // it takes the snackbar.
        message = if (!naming) error?.let { dataErrorMessage(it) } else null,
        onDismissMessage = viewModel::onErrorShown,
        modifier = modifier,
    )

    if (naming) {
        NewExerciseDialog(
            error = error,
            onDismiss = {
                naming = false
                viewModel.onErrorShown()
            },
            onCreate = viewModel::onCreateExercise,
        )
    }
}
