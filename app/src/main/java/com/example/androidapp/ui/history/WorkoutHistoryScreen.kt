package com.example.androidapp.ui.history

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapp.R
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.ui.workout.WorkoutFormat
import java.time.Instant
import java.time.YearMonth

@Composable
fun WorkoutHistoryRoute(
    onOpenWorkout: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutHistoryScreen(
        state = state,
        onOpenWorkout = onOpenWorkout,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    state: WorkoutHistoryUiState,
    onOpenWorkout: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
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
            state.isLoading -> HistoryMessage(
                text = stringResource(R.string.history_loading),
                modifier = Modifier.padding(innerPadding),
            )

            // Distinct from "nothing matched": the user has simply not trained yet.
            state.isEmpty -> HistoryMessage(
                text = stringResource(R.string.history_empty),
                hint = stringResource(R.string.history_empty_hint),
                modifier = Modifier.padding(innerPadding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "month-${group.month}") {
                        MonthHeader(month = group.month)
                    }
                    items(items = group.workouts, key = { it.id }) { workout ->
                        WorkoutRow(workout = workout, onClick = { onOpenWorkout(workout.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, modifier: Modifier = Modifier) {
    Text(
        text = HistoryFormat.month(month),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun WorkoutRow(
    workout: WorkoutSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setCount = pluralStringResource(R.plurals.history_sets, workout.setCount, workout.setCount)

    ListItem(
        headlineContent = { Text(HistoryFormat.date(workout.startedAt)) },
        supportingContent = {
            val duration = workout.duration?.let { WorkoutFormat.elapsed(it) }.orEmpty()
            val volume = stringResource(
                R.string.history_volume,
                Weight.kilograms(workout.volumeGrams),
            )
            Text(listOf(duration, setCount, volume).filter { it.isNotEmpty() }.joinToString(" · "))
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun HistoryMessage(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
// Sample data is the entire point of a preview, so the literals stay literals.
@Suppress("MagicNumber")
private fun WorkoutHistoryScreenPreview() {
    AndroidAppTheme {
        WorkoutHistoryScreen(
            state = WorkoutHistoryUiState(
                isLoading = false,
                groups = listOf(
                    HistoryGroup(
                        month = YearMonth.of(2026, 9),
                        workouts = listOf(
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
                ),
            ),
            onOpenWorkout = {},
            onBack = {},
        )
    }
}
