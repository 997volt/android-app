package com.example.androidapp.ui.statistics

import androidx.lifecycle.SavedStateHandle
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.DataError
import kotlinx.coroutines.launch
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.BodyMeasurement
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.ExerciseTrendPoint
import com.example.androidapp.domain.model.RangeKind
import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.domain.model.TrendMetric
import com.example.androidapp.domain.model.TrendPoint
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.repository.MeasurementRepository
import com.example.androidapp.domain.repository.SettingsRepository
import com.example.androidapp.domain.repository.StatisticsRepository
import com.example.androidapp.domain.repository.TrendsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What the Statistics screen reads (ROADMAP N35).
 *
 * The interesting assertions are the wiring: which source each metric comes from, that the range reaches
 * both the totals and the series, and that a metric needing a lift says so rather than drawing nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-10-02T09:00:00Z")
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = now.atZone(zone).toLocalDate()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun daysAgo(days: Long): Instant = today.minusDays(days).atTime(12, 0).atZone(zone).toInstant()

    @Test
    fun theRangeComesFromTheStore_andBodyweightIsTheDefaultMetric() = runTest(dispatcher) {
        val viewModel = viewModel(range = StatisticsRange(RangeKind.LAST_MONTH))
        observe(viewModel)
        advanceUntilIdle()

        assertEquals(RangeKind.LAST_MONTH, viewModel.uiState.value.range.kind)
        assertEquals(MetricKey.Body(BodyMetric.WEIGHT), viewModel.uiState.value.selection.metric)
    }

    @Test
    fun theOverview_countsTheWorkoutsInTheRange() = runTest(dispatcher) {
        val viewModel = viewModel(
            range = StatisticsRange(RangeKind.LAST_7_DAYS),
            sessions = listOf(
                summary(daysAgo(1), volume = 10_000L),
                summary(daysAgo(3), volume = 5_000L),
                // Two months old: outside the window, so it must not be counted.
                summary(daysAgo(60), volume = 999_000L),
            ),
            records = 3,
        )
        observe(viewModel)
        advanceUntilIdle()

        val overview = viewModel.uiState.value.overview
        assertEquals(2, overview.workouts)
        assertEquals(15_000L, overview.volumeGrams)
        assertEquals("the record count comes from its own query", 3, overview.personalRecords)
    }

    @Test
    fun theSeries_isTheSelectedMetric_filteredToTheRange() = runTest(dispatcher) {
        // Two weigh-ins inside the window and one long before it.
        val viewModel = viewModel(
            range = StatisticsRange(RangeKind.LAST_7_DAYS),
            body = listOf(
                measurement(daysAgo(1), weight = 82_000L),
                measurement(daysAgo(4), weight = 83_000L),
                measurement(daysAgo(90), weight = 90_000L),
            ),
        )
        observe(viewModel)
        advanceUntilIdle()

        val series = viewModel.uiState.value.series
        assertEquals("only the readings inside the window", 2, series?.readings?.size)
        assertEquals(83_000.0, series?.readings?.first()?.value)
    }

    @Test
    fun aWorkoutMetric_readsTheWorkoutsOwnPoints() = runTest(dispatcher) {
        val viewModel = viewModel(
            range = StatisticsRange(RangeKind.ALL),
            points = listOf(TrendPoint(startedAt = daysAgo(1), averageRpe = 8.0)),
        )
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onSelectMetric(MetricKey.Workout(TrendMetric.RPE))
        advanceUntilIdle()

        assertEquals(8.0, viewModel.uiState.value.series?.readings?.single()?.value)
    }

    @Test
    fun anExerciseMetric_readsTheChosenLiftsPoints() = runTest(dispatcher) {
        val trends = FakeTrendsRepository(
            exercisePoints = listOf(
                ExerciseTrendPoint(startedAt = daysAgo(2), volumeGrams = 4_000_000L),
            ),
        )
        val viewModel = viewModel(range = StatisticsRange(RangeKind.ALL), trends = trends)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onSelectMetric(MetricKey.Exercise(ExerciseTrendMetric.VOLUME))
        advanceUntilIdle()
        assertTrue("with no lift chosen yet, the screen says so", viewModel.uiState.value.needsExercise)

        viewModel.onSelectExercise("back-squat")
        advanceUntilIdle()

        assertEquals("back-squat", trends.askedFor)
        assertEquals(4_000_000.0, viewModel.uiState.value.series?.readings?.single()?.value)
        assertEquals(false, viewModel.uiState.value.needsExercise)
    }

    @Test
    fun choosingARange_storesIt() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(range = StatisticsRange(), settings = settings)
        observe(viewModel)
        advanceUntilIdle()

        val custom = StatisticsRange(RangeKind.CUSTOM, today.minusDays(30), today)
        viewModel.onSelectRange(custom)
        advanceUntilIdle()

        assertEquals(custom, settings.stored.value)
    }

    /** A measurement entry, with only the fields these tests care about. */
    private fun measurement(at: Instant, weight: Long) = BodyMeasurement(
        id = "m-$at",
        measuredAt = at,
        weightGrams = weight,
        tape = emptyMap<TapeSite, Long>(),
    )

    @Test
    fun arrivingWithALift_preselectsIt_andAStrengthMetric() {
        // What "how is my bench going" arrives with, from the library or from the lift just performed: an
        // Exercise series means nothing until a lift is chosen, so the screen must not land on bodyweight.
        val selection = initialSelection("bench-press")

        assertEquals("bench-press", selection.exerciseId)
        assertTrue(selection.metric is MetricKey.Exercise)
    }

    @Test
    fun arrivingWithNoLift_startsOnBodyweight() {
        assertEquals(StatisticsSelection(), initialSelection(null))
    }

    @Test
    fun theLibrary_reachesTheState_forTheLiftPicker() = runTest(dispatcher) {
        // The picker offers the library's names and nothing else: a second way to name a lift would be a
        // second thing to keep in step with it.
        val viewModel = viewModel(
            range = StatisticsRange(RangeKind.ALL),
            lifts = listOf(lift("back-squat", "Back Squat"), lift("bench-press", "Barbell Bench Press")),
        )
        observe(viewModel)
        advanceUntilIdle()

        assertEquals(
            listOf("Back Squat", "Barbell Bench Press"),
            viewModel.uiState.value.lifts.map { it.name },
        )
    }

    private fun lift(id: String, name: String) = Exercise(
        id = id,
        name = name,
        primaryMuscle = MuscleGroup.QUADS,
        secondaryMuscles = emptyList(),
        equipment = Equipment.BARBELL,
        movementPattern = MovementPattern.SQUAT,
        isCustom = false,
    )

    private fun summary(at: Instant, volume: Long) = WorkoutSummary(
        id = "s-$at",
        startedAt = at,
        finishedAt = at.plusSeconds(3_600),
        exerciseCount = 1,
        setCount = 3,
        volumeGrams = volume,
        zoneOffsetMinutes = 0,
        repeatableExerciseCount = 1,
    )

    private fun viewModel(
        range: StatisticsRange,
        sessions: List<WorkoutSummary> = emptyList(),
        points: List<TrendPoint> = emptyList(),
        body: List<BodyMeasurement> = emptyList(),
        records: Int? = null,
        lifts: List<Exercise> = emptyList(),
        // Defaults may reference earlier parameters, which is what keeps `points` from being a parameter
        // nobody reads — the bug this test found in its own fixture.
        trends: FakeTrendsRepository = FakeTrendsRepository(points = points),
        settings: FakeSettingsRepository = FakeSettingsRepository(range),
    ) = StatisticsViewModel(
        settings = settings,
        repositories = StatisticsRepositories(
            statistics = FakeStatisticsRepository(sessions, records),
            trends = trends,
            measurements = FakeMeasurementRepository(body),
            exercises = FakeExerciseRepository(lifts),
        ),
        timeSource = TimeSource { now },
        savedStateHandle = SavedStateHandle(),
    )

    /**
     * A collect never returns, so it goes in the background scope: `runTest` cancels that at the end, and a
     * plain `launch` here would leave the test waiting a minute for a coroutine that is doing its job.
     */
    private fun TestScope.observe(viewModel: StatisticsViewModel) {
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
    }
}

private class FakeSettingsRepository(
    initial: StatisticsRange = StatisticsRange(),
) : SettingsRepository {
    val stored = MutableStateFlow(initial)
    override fun observeDefaultRestSeconds(): Flow<Int> = flowOf(45)
    override suspend fun setDefaultRestSeconds(seconds: Int): DataResult<Unit> = DataResult.Success(Unit)
    override fun observeRestCueEnabled(): Flow<Boolean> = flowOf(true)
    override suspend fun setRestCueEnabled(enabled: Boolean): DataResult<Unit> = DataResult.Success(Unit)
    override fun observeKeepScreenOn(): Flow<Boolean> = flowOf(true)
    override suspend fun setKeepScreenOn(enabled: Boolean): DataResult<Unit> = DataResult.Success(Unit)
    override fun observeStatisticsRange(): Flow<StatisticsRange> = stored
    override suspend fun setStatisticsRange(range: StatisticsRange): DataResult<Unit> {
        stored.value = range
        return DataResult.Success(Unit)
    }
}

private class FakeStatisticsRepository(
    private val sessions: List<WorkoutSummary>,
    private val records: Int?,
) : StatisticsRepository {
    override fun observeWorkoutSummaries(): Flow<List<WorkoutSummary>> = flowOf(sessions)
    override suspend fun countRecordsIn(from: Instant, to: Instant): DataResult<Int> =
        records?.let { DataResult.Success(it) } ?: DataResult.Success(0)
}

private class FakeTrendsRepository(
    private val points: List<TrendPoint> = emptyList(),
    private val exercisePoints: List<ExerciseTrendPoint> = emptyList(),
) : TrendsRepository {
    var askedFor: String? = null
        private set

    override fun observeTrends(limit: Int): Flow<DataResult<List<TrendPoint>>> =
        flowOf(DataResult.Success(points))

    override fun observeExerciseTrends(
        exerciseId: String,
        limit: Int,
    ): Flow<DataResult<List<ExerciseTrendPoint>>> {
        askedFor = exerciseId
        return flowOf(DataResult.Success(exercisePoints))
    }
}

private class FakeMeasurementRepository(
    private val entries: List<BodyMeasurement>,
) : MeasurementRepository {
    override fun observeAll(): Flow<List<BodyMeasurement>> = flowOf(entries)
    override suspend fun save(measurement: BodyMeasurement): DataResult<Unit> = DataResult.Success(Unit)
    override suspend fun delete(id: String): DataResult<Unit> = DataResult.Success(Unit)
}

private class FakeExerciseRepository(private val lifts: List<Exercise>) : ExerciseRepository {
    override fun observeExercises(): Flow<DataResult<List<Exercise>>> = flowOf(DataResult.Success(lifts))

    override suspend fun getExercise(id: String): DataResult<Exercise?> =
        DataResult.Success(lifts.firstOrNull { it.id == id })

    override suspend fun createCustomExercise(name: String): DataResult<Exercise> =
        DataResult.Failure(DataError.Invalid("not used here"))

    override suspend fun updateExercise(exercise: Exercise): DataResult<Unit> = DataResult.Success(Unit)
}
