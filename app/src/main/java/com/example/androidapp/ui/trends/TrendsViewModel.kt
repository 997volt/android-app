package com.example.androidapp.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.TrendMetric
import com.example.androidapp.domain.model.TrendPoint
import com.example.androidapp.domain.model.averageValue
import com.example.androidapp.domain.model.latestValue
import com.example.androidapp.domain.model.recordedCount
import com.example.androidapp.domain.model.valuesOf
import com.example.androidapp.domain.repository.TrendsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One metric's series, ready for the screen to draw and caption (ROADMAP N13).
 *
 * An average over sessions rather than over sets: a workout with nine rated sets
 * should not shout louder than one with a single rated set.
 */
data class TrendSection(
    val metric: TrendMetric,
    /** Oldest first, null where that workout did not record this metric. */
    val values: List<Double?>,
    val latest: Double?,
    val average: Double?,
    /** How many of the window's workouts recorded it at all. */
    val recorded: Int,
) {
    /**
     * One point is a number, not a trend, and the screen says so rather than drawing a
     * line through a single dot.
     */
    val hasLine: Boolean get() = recorded >= 2
}

data class TrendsUiState(
    val isLoading: Boolean = true,
    /** How many workouts the window covers, for the screen's context line. */
    val windowSize: Int = 0,
    val sections: List<TrendSection> = emptyList(),
    /** The read failed (ROADMAP B4): shown in place of the charts. */
    val error: DataError? = null,
) {
    /** Nothing recorded at all — different from "the read failed". */
    val hasNothingRecorded: Boolean get() = !isLoading && error == null && sections.all { it.recorded == 0 }
}

/**
 * Reads the collected signals back (ROADMAP N13).
 *
 * The series are built here rather than in the composable so the arithmetic — which
 * workouts count, what an average is over, what "not recorded" does to a line — is
 * unit-testable without a device.
 */
@HiltViewModel
class TrendsViewModel @Inject constructor(
    repository: TrendsRepository,
) : ViewModel() {

    val uiState: StateFlow<TrendsUiState> = repository.observeTrends()
        .map { result ->
            when (result) {
                is DataResult.Success -> TrendsUiState(
                    isLoading = false,
                    windowSize = result.data.size,
                    sections = TrendMetric.entries.map { metric -> result.data.sectionFor(metric) },
                )

                is DataResult.Failure -> TrendsUiState(isLoading = false, error = result.error)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TrendsUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun List<TrendPoint>.sectionFor(metric: TrendMetric): TrendSection {
    val values = valuesOf(metric)
    return TrendSection(
        metric = metric,
        values = values,
        latest = values.latestValue(),
        average = values.averageValue(),
        recorded = values.recordedCount(),
    )
}
