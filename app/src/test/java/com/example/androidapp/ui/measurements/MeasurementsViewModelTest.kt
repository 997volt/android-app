package com.example.androidapp.ui.measurements

import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.BodyMeasurement
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.domain.repository.MeasurementRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What the measurements screen's ViewModel owns: passing an entry on, and reporting what came back. */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val entries = MutableStateFlow<List<BodyMeasurement>>(emptyList())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun itShowsWhatTheRepositoryHolds_newestFirst() = runTest(dispatcher) {
        entries.value = listOf(entry("m2", weight = 82_000L), entry("m1", weight = 83_000L))
        val viewModel = MeasurementsViewModel(FakeMeasurementRepository(entries))
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(
            listOf("m2", "m1"),
            viewModel.uiState.value.entries.map { it.id },
        )
    }

    @Test
    fun saving_passesTheEntryThrough_andClearsTheError() = runTest(dispatcher) {
        val repository = FakeMeasurementRepository(entries)
        repository.failNextSave = true
        val viewModel = MeasurementsViewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onSave(entry("", weight = 82_000L))
        advanceUntilIdle()
        assertTrue(
            "a failure is surfaced",
            viewModel.error.value is DataError.Storage,
        )

        repository.failNextSave = false
        viewModel.onSave(entry("", weight = 82_000L))
        advanceUntilIdle()
        assertNull("and cleared by the next save that works", viewModel.error.value)
    }

    private fun entry(id: String, weight: Long) = BodyMeasurement(
        id = id,
        measuredAt = Instant.parse("2026-10-02T08:00:00Z"),
        weightGrams = weight,
        tape = mapOf(TapeSite.WAIST to 860L),
    )
}

private class FakeMeasurementRepository(
    private val entries: MutableStateFlow<List<BodyMeasurement>>,
) : MeasurementRepository {

    var failNextSave = false

    override fun observeAll(): Flow<List<BodyMeasurement>> = entries

    override suspend fun save(measurement: BodyMeasurement): DataResult<Unit> {
        if (failNextSave) return DataResult.Failure(DataError.Storage(IllegalStateException()))
        entries.value = listOf(measurement)
        return DataResult.Success(Unit)
    }

    override suspend fun delete(id: String): DataResult<Unit> {
        entries.value = entries.value.filterNot { it.id == id }
        return DataResult.Success(Unit)
    }
}
