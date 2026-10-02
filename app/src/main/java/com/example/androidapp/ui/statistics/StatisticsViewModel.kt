package com.example.androidapp.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.BodyMeasurement
import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.domain.model.TrendPoint
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.model.window
import com.example.androidapp.domain.nowEpochMillis
import com.example.androidapp.domain.repository.MeasurementRepository
import com.example.androidapp.domain.repository.SettingsRepository
import com.example.androidapp.domain.repository.StatisticsRepository
import com.example.androidapp.domain.repository.TrendsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The metric the picker is on, and the lift when that metric needs one (ROADMAP N35). */
data class StatisticsSelection(
    /** Bodyweight, because it is the series a person checks most often. */
    val metric: MetricKey = MetricKey.Body(BodyMetric.WEIGHT),
    val exerciseId: String? = null,
)

/** What the Statistics screen shows (ROADMAP N35). */
data class StatisticsUiState(
    val isLoading: Boolean = true,
    val range: StatisticsRange = StatisticsRange(),
    val selection: StatisticsSelection = StatisticsSelection(),
    val overview: StatisticsOverview = StatisticsOverview(),
    val series: MetricSeries? = null,
    /** The read failed, shown in place of the chart (the same shape the trends screen uses). */
    val error: DataError? = null,
) {
    /**
     * True when the chosen metric needs a lift and none is chosen yet.
     *
     * The screen asks rather than drawing nothing: an empty chart and a question look different, and only
     * one of them is true.
     */
    val needsExercise: Boolean
        get() = MetricRegistry.entryFor(selection.metric).needsExercise && selection.exerciseId == null
}

/**
 * Everything the Statistics screen reads (ROADMAP N35).
 *
 * One screen over three sources, which is the point of the round: the registry says how to show each of the
 * twenty-one series, `metricSeries` says how to read one, and this says which of them to read and for when.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val statistics: StatisticsRepository,
    private val trends: TrendsRepository,
    private val measurements: MeasurementRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val selection = MutableStateFlow(StatisticsSelection())

    /**
     * The range the screen is showing.
     *
     * From the store rather than from local state, so a range chosen here is the range it comes back to —
     * and so the screen and the store cannot disagree about which one is current.
     */
    private val range = settings.observeStatisticsRange()

    /** The four sources, gathered before they are combined: five flows is the typed combine's limit. */
    private data class Sources(
        val range: StatisticsRange,
        val selection: StatisticsSelection,
        val summaries: List<WorkoutSummary>,
        val workoutPoints: DataResult<List<TrendPoint>>,
        val measurements: List<BodyMeasurement>,
    )

    private val sources = combine(
        range,
        selection,
        statistics.observeWorkoutSummaries(),
        trends.observeTrends(WINDOW),
        measurements.observeAll(),
    ) { range, selection, summaries, points, body ->
        Sources(range, selection, summaries, points, body)
    }

    /** The chosen lift's own series, or nothing when the metric does not need one. */
    private val exercisePoints = selection.flatMapLatest { chosen ->
        val metric = chosen.metric
        val exerciseId = chosen.exerciseId
        if (metric is MetricKey.Exercise && exerciseId != null) {
            trends.observeExerciseTrends(exerciseId, WINDOW)
        } else {
            flowOf(DataResult.Success(emptyList()))
        }
    }

    /** How many records the window contains. Its own flow because it is a suspend read, not a stream. */
    private val records = range.mapLatest { current ->
        val bounds = current.window(today(), ZONE) ?: return@mapLatest null
        (statistics.countRecordsIn(bounds.from, bounds.toExclusive) as? DataResult.Success)?.data
    }

    val uiState: StateFlow<StatisticsUiState> =
        combine(sources, exercisePoints, records) { sources, points, records ->
            val workoutPoints = sources.workoutPoints
            if (workoutPoints is DataResult.Failure) {
                StatisticsUiState(
                    isLoading = false,
                    range = sources.range,
                    selection = sources.selection,
                    error = workoutPoints.error,
                )
            } else {
                val body = sources.range.inWindow(
                    readings = metricSeries(
                        key = sources.selection.metric,
                        workouts = (workoutPoints as DataResult.Success).data,
                        exercises = (points as? DataResult.Success)?.data.orEmpty(),
                        measurements = sources.measurements,
                    ).readings,
                    today = today(),
                    zone = ZONE,
                )
                StatisticsUiState(
                    isLoading = false,
                    range = sources.range,
                    selection = sources.selection,
                    overview = statisticsOverview(
                        range = sources.range,
                        today = today(),
                        sessions = sources.summaries,
                        zone = ZONE,
                        personalRecords = records,
                    ),
                    series = MetricSeries(key = sources.selection.metric, readings = body),
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = StatisticsUiState(),
        )

    fun onSelectMetric(metric: MetricKey) {
        selection.value = selection.value.copy(metric = metric)
    }

    fun onSelectExercise(exerciseId: String?) {
        selection.value = selection.value.copy(exerciseId = exerciseId)
    }

    /** Stored, not held: the range has to mean the same thing the next time the tab is opened. */
    fun onSelectRange(range: StatisticsRange) {
        viewModelScope.launch { settings.setStatisticsRange(range) }
    }

    private fun today(): LocalDate =
        Instant.ofEpochMilli(timeSource.nowEpochMillis()).atZone(ZONE).toLocalDate()

    private companion object {
        /**
         * How many sessions the training series are asked for.
         *
         * The trends queries window by session, not by time, so a range is honoured by asking for more
         * sessions than the range can hold and then filtering by date. Five hundred is years of training for
         * anyone, and a chart cannot draw more points than that legibly anyway.
         */
        const val WINDOW = 500
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val ZONE: ZoneId = ZoneId.systemDefault()
    }
}
