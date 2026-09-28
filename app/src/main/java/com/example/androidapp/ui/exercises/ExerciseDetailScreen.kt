package com.example.androidapp.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.ui.theme.AndroidAppTheme

/** Stateful entry point for the detail destination; reads its id from the route. */
@Composable
fun ExerciseDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseDetailScreen(state = state, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(state.exercise?.name ?: stringResource(R.string.exercise_detail_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.exercise_detail_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val exercise = state.exercise
        when {
            state.isLoading -> CenteredMessage(
                text = stringResource(R.string.exercise_library_loading),
                showSpinner = true,
                modifier = Modifier.padding(innerPadding),
            )

            state.notFound -> CenteredMessage(
                text = stringResource(R.string.exercise_detail_not_found),
                showSpinner = false,
                modifier = Modifier.padding(innerPadding),
            )

            exercise != null -> ExerciseDetails(
                exercise = exercise,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ExerciseDetails(exercise: Exercise, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AttributeRow(
            label = stringResource(R.string.exercise_detail_primary),
            value = exercise.primaryMuscle.label,
        )
        HorizontalDivider()

        AttributeRow(
            label = stringResource(R.string.exercise_detail_secondary),
            value = exercise.secondaryMuscles
                .joinToString(", ") { it.label }
                .ifEmpty { stringResource(R.string.exercise_detail_none) },
        )
        HorizontalDivider()

        AttributeRow(
            label = stringResource(R.string.exercise_detail_equipment),
            value = exercise.equipment.label,
        )
        HorizontalDivider()

        AttributeRow(
            label = stringResource(R.string.exercise_detail_pattern),
            value = exercise.movementPattern.label,
        )
        HorizontalDivider()

        // Honest placeholder: history is the next milestone, not a broken screen.
        Text(
            text = stringResource(R.string.exercise_detail_history_planned),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun AttributeRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    showSpinner: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) CircularProgressIndicator()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseDetailScreenPreview() {
    AndroidAppTheme {
        ExerciseDetailScreen(
            state = ExerciseDetailUiState(
                isLoading = false,
                exercise = Exercise(
                    id = "back-squat",
                    name = "Back Squat",
                    primaryMuscle = MuscleGroup.QUADS,
                    secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.CORE),
                    equipment = Equipment.BARBELL,
                    movementPattern = MovementPattern.SQUAT,
                ),
            ),
            onBack = {},
        )
    }
}
