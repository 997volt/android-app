package com.example.androidapp.ui.trends

import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.TrendMetric
import com.example.androidapp.domain.model.TrendPoint
import com.example.androidapp.domain.repository.TrendsRepository
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.observe(viewModel: TrendsViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun aSectionPerMetric_isBuiltFromTheWindow() = runTest(dispatcher) {
        val repository = FakeTrendsRepository(
            points = listOf(
                point(rpeHalves = 6.0, feel = 6.0, pain = 2.0),
                point(rpeHalves = 8.0, feel = 8.0, pain = 4.0),
            ),
        )
        val viewModel = TrendsViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.windowSize)
        assertEquals(
            listOf(TrendMetric.RPE, TrendMetric.MUSCLE_FEEL, TrendMetric.JOINT_PAIN),
            state.sections.map { it.metric },
        )

        val rpeHalves = state.sections.single { it.metric == TrendMetric.RPE }
        assertEquals(8.0, rpeHalves.latest)
        assertEquals(7.0, rpeHalves.average)
        assertEquals(2, rpeHalves.recorded)
        assertTrue(rpeHalves.hasLine)
    }

    @Test
    fun aMetricRecordedOnce_isANumber_ratherThanALine() = runTest(dispatcher) {
        // One point is not a trend, and drawing a line through it would suggest one.
        val repository = FakeTrendsRepository(
            points = listOf(point(rpeHalves = 7.0), point(rpeHalves = null)),
        )
        val viewModel = TrendsViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        val rpeHalves = viewModel.uiState.value.sections.single { it.metric == TrendMetric.RPE }
        assertEquals(1, rpeHalves.recorded)
        assertEquals(7.0, rpeHalves.latest)
        assertFalse(rpeHalves.hasLine)
    }

    @Test
    fun theGaps_areKeptInTheSeries_soTheChartCanBreakItsLine() = runTest(dispatcher) {
        val repository = FakeTrendsRepository(
            points = listOf(point(feel = 6.0), point(feel = null), point(feel = 8.0)),
        )
        val viewModel = TrendsViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        val feel = viewModel.uiState.value.sections.single { it.metric == TrendMetric.MUSCLE_FEEL }
        assertEquals(listOf(6.0, null, 8.0), feel.values)
        // Two recorded points, one of them separated by a gap: still a line, broken.
        assertEquals(2, feel.recorded)
        assertTrue(feel.hasLine)
    }

    @Test
    fun nothingRecorded_isNotTheSameAsNothingToRead() = runTest(dispatcher) {
        val viewModel = TrendsViewModel(FakeTrendsRepository(points = listOf(point())))
        observe(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasNothingRecorded)
        assertNull("an empty window is not a failure", state.error)
    }

    @Test
    fun aWorkoutWindow_withOnlySomeMetrics_isNotNothing() = runTest(dispatcher) {
        val viewModel = TrendsViewModel(FakeTrendsRepository(points = listOf(point(rpeHalves = 7.0))))
        observe(viewModel)
        advanceUntilIdle()

        assertFalse(
            "one recorded metric is something to show, even if the others are empty",
            viewModel.uiState.value.hasNothingRecorded,
        )
    }

    @Test
    fun aFailedRead_isAScreenState_ratherThanAThrow() = runTest(dispatcher) {
        val repository = FakeTrendsRepository(failure = DataError.Storage(IOException("locked")))
        val viewModel = TrendsViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun point(
        rpeHalves: Double? = null,
        feel: Double? = null,
        pain: Double? = null,
    ) = TrendPoint(
        startedAt = Instant.parse("2026-09-28T08:00:00Z"),
        averageRpe = rpeHalves,
        averageMuscleFeel = feel,
        averageJointPain = pain,
    )

    private class FakeTrendsRepository(
        private val points: List<TrendPoint> = emptyList(),
        private val failure: DataError? = null,
    ) : TrendsRepository {
        override fun observeTrends(limit: Int): Flow<DataResult<List<TrendPoint>>> =
            flowOf(failure?.let { DataResult.Failure(it) } ?: DataResult.Success(points))
    }
}
