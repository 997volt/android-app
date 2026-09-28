package com.example.androidapp.ui.history

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.WorkoutSession
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
    WorkoutDetailScreen(state = state, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.session
                            ?.let { HistoryFormat.date(it.startedAt) }
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
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> Text(
                text = stringResource(R.string.history_loading),
                modifier = Modifier.padding(innerPadding).padding(24.dp),
            )

            state.notFound -> Text(
                text = stringResource(R.string.history_detail_missing),
                modifier = Modifier.padding(innerPadding).padding(24.dp),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "totals") {
                    Totals(state)
                    HorizontalDivider()
                }
                items(items = state.exercises, key = { it.id }) { exercise ->
                    ExerciseBlock(exercise)
                    HorizontalDivider()
                }
            }
        }
    }
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
private fun ExerciseBlock(exercise: HistoryExercise, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = exercise.name, style = MaterialTheme.typography.titleMedium)

        exercise.sets.forEachIndexed { index, set ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
        }
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
            onBack = {},
        )
    }
}
