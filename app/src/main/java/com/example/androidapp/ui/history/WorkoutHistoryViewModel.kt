package com.example.androidapp.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One month's worth of workouts, newest month first. */
data class HistoryGroup(
    val month: YearMonth,
    val workouts: List<WorkoutSummary>,
)

data class WorkoutHistoryUiState(
    val isLoading: Boolean = true,
    val groups: List<HistoryGroup> = emptyList(),
) {
    /** Logged nothing yet, as opposed to "nothing matched a filter" (P1.1a's lesson). */
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}

/**
 * The history list (ROADMAP P1.6).
 *
 * Totals arrive pre-aggregated from SQL, so this only groups and sorts — which is
 * also why a year of training costs one query rather than one per workout.
 */
@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
) : ViewModel() {

    val uiState: StateFlow<WorkoutHistoryUiState> = workoutRepository.observeHistory()
        .map { workouts ->
            WorkoutHistoryUiState(isLoading = false, groups = groupByMonth(workouts))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = WorkoutHistoryUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Groups workouts by the local month they started in, newest first.
 *
 * Pure, so the ordering rules are unit-tested rather than eyeballed. Grouping on
 * `startedAt` rather than `finishedAt` is deliberate: a workout that begins at
 * 23:50 and ends after midnight belongs to the day the user started it.
 */
internal fun groupByMonth(
    workouts: List<WorkoutSummary>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<HistoryGroup> = workouts
    .groupBy { YearMonth.from(it.startedAt.atZone(zone)) }
    .toSortedMap(compareByDescending { it })
    .map { (month, inMonth) ->
        HistoryGroup(
            month = month,
            workouts = inMonth.sortedByDescending { it.startedAt },
        )
    }
