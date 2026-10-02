package com.example.androidapp.ui.statistics

import com.example.androidapp.ui.navigation.Statistics
import com.example.androidapp.domain.model.ExerciseTrendMetric
import androidx.navigation.toRoute
import androidx.lifecycle.SavedStateHandle
import com.example.androidapp.domain.model.Exercise
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
import com.example.androidapp.domain.repository.SettingsRepository
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

/**
 * What the picker starts on (ROADMAP N35).
 *
 * Arriving with a lift — "how is my bench going", from the library or from the lift just performed —
 * selects that lift and a strength metric: an Exercise series means nothing until a lift is chosen, and
 * bodyweight would answer a question nobody asked. Estimated 1RM rather than the heaviest set, because it
 * accounts for the reps a heavy single and a hard set of five differ by.
 *
 * Without a lift, bodyweight: the series a person checks most often.
 */
fun initialSelection(exerciseId: String?): StatisticsSelection =
    if (exerciseId == null) {
        StatisticsSelection()
    } else {
        StatisticsSelection(
            metric = MetricKey.Exercise(ExerciseTrendMetric.ESTIMATED_1RM),
            exerciseId = exerciseId,
        )
    }

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
    /** The library, for choosing the lift an Exercise metric is about. */
    val lifts: List<Exercise> = emptyList(),
    /** The target set for the chosen metric, in its own units, or null when there is none (ROADMAP N39). */
    val goal: Double? = null,
) {
    /**
     * True when the chosen metric needs a lift and none is chosen yet.
     *
     * The screen asks rather than drawing nothing: an empty chart and a question look different, and only
     * one of them is true.
     */
    val needsExercise: Boolean
        get() = metricNeedsExercise && selection.exerciseId == null

    /**
     * True when the chosen metric is about a lift at all.
     *
     * Separate from [needsExercise] because the two drive different things, and conflating them made the
     * picker unreachable: the screen gated the *picker* on "nothing is chosen yet", so the only way to change
     * lift was to arrive with none selected — and every entry point selects one. This is "the metric needs a
     * lift"; that is "and there is not one yet", which is about the prompt instead.
     */
    val metricNeedsExercise: Boolean
        get() = MetricRegistry.entryFor(selection.metric).needsExercise
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
    private val repositories: StatisticsRepositories,
    private val timeSource: TimeSource,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val selection = MutableStateFlow(
        initialSelection(savedStateHandle.toRoute<Statistics>().exerciseId),
    )

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
        val lifts: List<Exercise>,
    )

    /**
     * The two reads that are not a series: the workouts the overview totals, and the library the lift picker
     * offers. Combined first because the typed `combine` stops at five flows and this is the pair that has
     * nothing to do with the selected metric.
     */
    private val library = combine(
        repositories.statistics.observeWorkoutSummaries(),
        repositories.exercises.observeExercises(),
    ) { summaries, result ->
        summaries to (result as? DataResult.Success)?.data.orEmpty()
    }

    private val sources = combine(
        range,
        selection,
        library,
        repositories.trends.observeTrends(WINDOW),
        repositories.measurements.observeAll(),
    ) { range, selection, library, points, body ->
        Sources(range, selection, library.first, points, body, library.second)
    }

    /** The chosen lift's own series, or nothing when the metric does not need one. */
    private val exercisePoints = selection.flatMapLatest { chosen ->
        val metric = chosen.metric
        val exerciseId = chosen.exerciseId
        if (metric is MetricKey.Exercise && exerciseId != null) {
            repositories.trends.observeExerciseTrends(exerciseId, WINDOW)
        } else {
            flowOf(DataResult.Success(emptyList()))
        }
    }

    /** How many records the window contains. Its own flow because it is a suspend read, not a stream. */
    private val records = range.mapLatest { current ->
        val bounds = current.window(today(), currentZone()) ?: return@mapLatest null
        (repositories.statistics.countRecordsIn(bounds.from, bounds.toExclusive) as? DataResult.Success)?.data
    }

    val uiState: StateFlow<StatisticsUiState> =
        combine(sources, exercisePoints, records, settings.observeGoals()) { sources, points, records, goals ->
            val workoutPoints = sources.workoutPoints
            if (workoutPoints is DataResult.Failure) {
                StatisticsUiState(
                    isLoading = false,
                    range = sources.range,
                    selection = sources.selection,
                    error = workoutPoints.error,
                    lifts = sources.lifts,
                    goal = goals[sources.selection.metric.id],
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
                    zone = currentZone(),
                )
                StatisticsUiState(
                    isLoading = false,
                    range = sources.range,
                    selection = sources.selection,
                    overview = statisticsOverview(
                        range = sources.range,
                        today = today(),
                        sessions = sources.summaries,
                        zone = currentZone(),
                        personalRecords = records,
                    ),
                    series = MetricSeries(key = sources.selection.metric, readings = body),
                    lifts = sources.lifts,
                    goal = goals[sources.selection.metric.id],
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = StatisticsUiState(),
        )

    /**
     * Sets or clears the chosen metric's target (ROADMAP N39).
     *
     * Written straight through rather than buffered: a target is one number the user typed and expects to be
     * remembered, so a failure has to be visible — the rule every other write in this app follows.
     */
    fun onSetGoal(value: Double?) {
        viewModelScope.launch { settings.setGoal(selection.value.metric.id, value) }
    }

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
        Instant.ofEpochMilli(timeSource.nowEpochMillis()).atZone(currentZone()).toLocalDate()

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
        /**
         * The zone "today" is measured in, read when it is asked for.
         *
         * A function rather than a `val`: a companion-object `val` is evaluated once at class load, so a
         * process that outlives a timezone change would keep computing today's date in the old zone. See
         * DECISIONS.md — a range is a window in the current zone.
         */
        fun currentZone(): ZoneId = ZoneId.systemDefault()
    }
}
