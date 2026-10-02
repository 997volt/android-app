package com.example.androidapp.ui.settings

import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The settings screen's state (ROADMAP B23).
 *
 * Its own KDoc makes a promise worth testing: it renders what is **stored**, not what was tapped,
 * so a choice that failed to reach disk must not look applied.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theStoredValue_isWhatTheScreenShows() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(FakeSettingsRepository(stored = 45))
        advanceUntilIdle()

        assertEquals(45, viewModel.uiState.value.defaultRestSeconds)
    }

    @Test
    fun aChoiceThatIsStored_becomesTheShownValue() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(stored = RestTimer.DEFAULT_SECONDS)
        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.onSetDefaultRest(30)
        advanceUntilIdle()

        assertEquals(30, viewModel.uiState.value.defaultRestSeconds)
        assertEquals(listOf(30), repository.writes)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun aChoiceThatFails_isReported_andDoesNotLookApplied() = runTest(dispatcher) {
        // The promise in the ViewModel's own KDoc: a failed write must not change what is shown, or
        // the next workout silently uses the old rest while the screen says otherwise.
        val repository = FakeSettingsRepository(stored = 90, refuseWrites = true)
        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.onSetDefaultRest(30)
        advanceUntilIdle()

        assertEquals("still what is stored", 90, viewModel.uiState.value.defaultRestSeconds)
        assertEquals(DataError.Invalid("refused"), viewModel.uiState.value.error)
    }
}

/** Hand-written, like every fake here: there is no mocking framework in this project. */
private class FakeSettingsRepository(
    stored: Int,
    private val refuseWrites: Boolean = false,
) : SettingsRepository {
    override fun observeGoals(): Flow<Map<String, Double>> = flowOf(emptyMap())

    override suspend fun setGoal(metricId: String, value: Double?): DataResult<Unit> =
        DataResult.Success(Unit)


    private val rest = MutableStateFlow(stored)
    val writes = mutableListOf<Int>()

    override fun observeDefaultRestSeconds(): Flow<Int> = rest.asStateFlow()

    override suspend fun setDefaultRestSeconds(seconds: Int): DataResult<Unit> {
        writes += seconds
        return if (refuseWrites) {
            DataResult.Failure(DataError.Invalid("refused"))
        } else {
            rest.value = seconds
            DataResult.Success(Unit)
        }
    }

    override fun observeRestCueEnabled(): Flow<Boolean> = flowOf(true)

    override suspend fun setRestCueEnabled(enabled: Boolean): DataResult<Unit> =
        DataResult.Success(Unit)

    override fun observeKeepScreenOn(): Flow<Boolean> = flowOf(true)

    override suspend fun setKeepScreenOn(enabled: Boolean): DataResult<Unit> =
        DataResult.Success(Unit)

    override fun observeStatisticsRange(): Flow<StatisticsRange> = flowOf(StatisticsRange())

    override suspend fun setStatisticsRange(range: StatisticsRange): DataResult<Unit> =
        DataResult.Success(Unit)

}
