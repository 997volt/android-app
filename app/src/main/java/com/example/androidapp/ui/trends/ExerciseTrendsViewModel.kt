package com.example.androidapp.ui.trends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.ExerciseTrendPoint
import com.example.androidapp.domain.model.averageValue
import com.example.androidapp.domain.model.latestValue
import com.example.androidapp.domain.model.recordedCount
import com.example.androidapp.domain.model.valuesOf
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.domain.repository.TREND_WINDOW
import com.example.androidapp.domain.repository.TrendsRepository
import com.example.androidapp.ui.components.RATING_AXIS_MAX
import com.example.androidapp.ui.components.RATING_AXIS_MIN
import com.example.androidapp.ui.navigation.ExerciseTrends
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One metric of one exercise, ready for the chart (ROADMAP N17). */
data class ExerciseTrendSection(
    val metric: ExerciseTrendMetric,
    /** Oldest first, null where that session did not record this metric. */
    val values: List<Double?>,
    val latest: Double?,
    val average: Double?,
    /** How many of the window's sessions recorded it at all. */
    val recorded: Int,
) {
    /** One point is a number, not a trend, so a single dot is not drawn as a line. */
    val hasLine: Boolean get() = values.count { it != null } > 1

    /**
     * The chart's axis. A load runs from zero to its own maximum, because a kilogram is
     * a quantity; a rating keeps the shared 1–10 axis so the three can be read against
     * each other (and so a 0.2 change cannot look like a cliff).
     */
    val axisMin: Double get() = if (metric.isLoad) 0.0 else RATING_AXIS_MIN
    val axisMax: Double
        get() = if (metric.isLoad) (values.filterNotNull().maxOrNull() ?: 1.0) else RATING_AXIS_MAX
}

data class ExerciseTrendsUiState(
    val isLoading: Boolean = true,
    val exerciseName: String = "",
    val sessions: Int = 0,
    val sections: List<ExerciseTrendSection> = emptyList(),
    val error: DataError? = null,
) {
    /** Nothing recorded is a normal answer for a lift only just started. */
    val isEmpty: Boolean get() = !isLoading && sessions == 0 && error == null
}

/**
 * One exercise's own trends (ROADMAP N17).
 *
 * The name is read from the library rather than passed in the route, so a rename shows
 * here without the caller having to thread it through; the series come from the same
 * repository N13 uses, narrowed by exercise.
 */
@HiltViewModel
class ExerciseTrendsViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository,
    trendsRepository: TrendsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: String = savedStateHandle.toRoute<ExerciseTrends>().exerciseId
    private val name = MutableStateFlow("")

    val uiState: StateFlow<ExerciseTrendsUiState> =
        combine(
            trendsRepository.observeExerciseTrends(exerciseId, TREND_WINDOW),
            name,
        ) { result, exerciseName ->
            when (result) {
                is DataResult.Success -> ExerciseTrendsUiState(
                    isLoading = false,
                    exerciseName = exerciseName,
                    sessions = result.data.size,
                    sections = result.data.toSections(),
                )

                is DataResult.Failure -> ExerciseTrendsUiState(
                    isLoading = false,
                    exerciseName = exerciseName,
                    error = result.error,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ExerciseTrendsUiState(),
        )

    init {
        viewModelScope.launch {
            name.value = (exerciseRepository.getExercise(exerciseId) as? DataResult.Success)
                ?.data
                ?.name
                .orEmpty()
        }
    }

    private companion object {
        /** Keeps the upstream flow warm across a configuration change. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Every metric this platform can draw, in the order it is worth reading.
 *
 * Assistance is last: it is only ever recorded on a machine that assists, so for most
 * lifts its section is absent entirely.
 */
private fun List<ExerciseTrendPoint>.toSections(): List<ExerciseTrendSection> =
    ExerciseTrendMetric.entries.map { metric ->
        ExerciseTrendSection(
            metric = metric,
            values = valuesOf(metric),
            latest = latestValue(metric),
            average = averageValue(metric),
            recorded = recordedCount(metric),
        )
    }
