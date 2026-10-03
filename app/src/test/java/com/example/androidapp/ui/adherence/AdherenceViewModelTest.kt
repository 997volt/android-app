package com.example.androidapp.ui.adherence

import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.AdherenceReport
import com.example.androidapp.domain.model.MonthAdherence
import com.example.androidapp.domain.model.PendingOccurrence
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.ProgramRun
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.SlotPrescription
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.domain.repository.ProgramRepository
import com.example.androidapp.domain.repository.SlotSetEdit
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.TimeZone
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
import org.junit.Before
import org.junit.Test

/**
 * What the Adherence screen reads (ROADMAP P3.5).
 *
 * The interesting part is the window: one read per shown month, back through history and never
 * past the month it is now — and the two absences of a ratio, which are different facts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdherenceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now: Instant = Instant.parse("2026-10-20T09:00:00Z")
    private val october = YearMonth.of(2026, 10)
    private val previousTimeZone: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        TimeZone.setDefault(previousTimeZone)
    }

    @Test
    fun itOpensOnTheCurrentMonth_andReadsIt() = runTest(dispatcher) {
        val repository = FakeProgramRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.month).isEqualTo(october)
        assertThat(repository.asked).containsExactly(october)
    }

    @Test
    fun goingBack_loadsThatMonth_andLeavesForwardOpen() = runTest(dispatcher) {
        val repository = FakeProgramRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onPreviousMonth()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.month).isEqualTo(october.minusMonths(1))
        assertThat(viewModel.uiState.value.canGoForward).isTrue()
        assertThat(repository.asked).containsExactly(october, october.minusMonths(1)).inOrder()
    }

    @Test
    fun markableWeeks_areTheStartedWeeksOfTheShownMonth() = runTest(dispatcher) {
        // ROADMAP P3.10: a week that has not started cannot be marked, because the app has no
        // forward view and a deload is decided by how the block is going.
        val viewModel = viewModel(FakeProgramRepository())
        observe(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.markableWeeks).containsExactly(
            LocalDate.of(2026, 9, 28),
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 10, 19),
        ).inOrder()
    }

    @Test
    fun markingADeload_writesIt_andReadsTheShownMonthAgain() = runTest(dispatcher) {
        val repository = FakeProgramRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)
        advanceUntilIdle()
        val readsBefore = repository.asked.size

        viewModel.onToggleDeload("p1", LocalDate.of(2026, 10, 5), marked = true)
        advanceUntilIdle()

        assertThat(repository.deloads)
            .containsExactly(Triple("p1", LocalDate.of(2026, 10, 5), true))
        // The ratio and the toggle have to agree, and one is derived from the other's rows.
        assertThat(repository.asked.size).isEqualTo(readsBefore + 1)
    }

    @Test
    fun theCalendar_neverGoesPastTheCurrentMonth() = runTest(dispatcher) {
        // A month that has not happened has nothing to score, so there is nothing there to see.
        val repository = FakeProgramRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canGoForward).isFalse()

        viewModel.onNextMonth()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.month).isEqualTo(october)
        assertThat(repository.asked).containsExactly(october)
    }

    @Test
    fun comingBackFromHistory_stopsAtTheCurrentMonth() = runTest(dispatcher) {
        val viewModel = viewModel(FakeProgramRepository())
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onPreviousMonth()
        viewModel.onPreviousMonth()
        advanceUntilIdle()
        viewModel.onNextMonth()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.month).isEqualTo(october.minusMonths(1))

        viewModel.onNextMonth()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.month).isEqualTo(october)

        viewModel.onNextMonth()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.month).isEqualTo(october)
    }

    @Test
    fun theRatio_reachesTheState() = runTest(dispatcher) {
        val repository = FakeProgramRepository().apply {
            report = DataResult.Success(
                AdherenceReport(
                    hasActiveProgram = true,
                    adherence = MonthAdherence(done = 3, skipped = 1, missed = 0),
                ),
            )
        }
        val viewModel = viewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.adherence.ratio).isEqualTo(0.75)
        assertThat(viewModel.uiState.value.hasActiveProgram).isTrue()
    }

    @Test
    fun withNoActiveProgram_theStateSaysSo_evenThoughTheDaysAreRead() = runTest(dispatcher) {
        val repository = FakeProgramRepository().apply {
            report = DataResult.Success(
                AdherenceReport(
                    hasActiveProgram = false,
                    adherence = MonthAdherence(trainedDays = setOf(LocalDate.of(2026, 10, 6))),
                ),
            )
        }
        val viewModel = viewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasActiveProgram).isFalse()
        assertThat(viewModel.uiState.value.adherence.ratio).isNull()
        assertThat(viewModel.uiState.value.adherence.trainedDays)
            .containsExactly(LocalDate.of(2026, 10, 6))
    }

    @Test
    fun aFailedRead_saysSo_ratherThanClaimingAnEmptyMonth() = runTest(dispatcher) {
        val repository = FakeProgramRepository().apply {
            report = DataResult.Failure(DataError.Storage(IOException("disk full")))
        }
        val viewModel = viewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isInstanceOf(DataError.Storage::class.java)
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        // The month is still the one asked for, so the header does not jump on a failure.
        assertThat(viewModel.uiState.value.month).isEqualTo(october)
    }

    private fun viewModel(repository: FakeProgramRepository) =
        AdherenceViewModel(repository, TimeSource { now })

    /** A collect never returns, so it runs in the background scope the test cancels. */
    private fun TestScope.observe(viewModel: AdherenceViewModel) {
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
    }

    /** Hand-written, because the project uses no mocking framework (DECISIONS.md). */
    private class FakeProgramRepository : ProgramRepository {
        var report: DataResult<AdherenceReport> =
            DataResult.Success(AdherenceReport(hasActiveProgram = true, adherence = MonthAdherence()))

        /** Every month the screen asked for, in order. */
        val asked = mutableListOf<YearMonth>()

        override suspend fun monthAdherence(
            month: YearMonth,
            today: LocalDate,
            zone: ZoneId,
        ): DataResult<AdherenceReport> {
            asked += month
            return report
        }

        override fun observePrograms(): Flow<List<WorkoutProgram>> = flowOf(emptyList())

        override fun observeProgram(programId: String): Flow<WorkoutProgram?> = flowOf(null)

        override fun observeActivePrograms(): Flow<List<WorkoutProgram>> = flowOf(emptyList())

        override fun observeSlots(programId: String): Flow<List<ProgramSlot>> = flowOf(emptyList())

        override fun observeSlotPrescriptions(slotId: String): Flow<List<SlotPrescription>> =
            flowOf(emptyList())

        override fun observeProgramRun(programId: String): Flow<ProgramRun?> = flowOf(null)

    /** Every deload write, as the program, week and state it claimed (ROADMAP P3.10). */
    val deloads = mutableListOf<Triple<String, LocalDate, Boolean>>()

    override suspend fun setDeloadWeek(
        programId: String,
        weekStart: java.time.LocalDate,
        marked: Boolean,
    ): DataResult<Unit> {
        deloads += Triple(programId, weekStart, marked)
        return DataResult.Success(Unit)
    }

        override suspend fun estimatedOneRepMax(exerciseId: String): DataResult<Long?> =
            error("these tests do not estimate a one-rep max")

        override suspend fun slotPreviousPerformance(
            slotId: String,
            exerciseId: String,
            currentSessionId: String,
            zone: ZoneId,
        ): DataResult<PreviousPerformance> = error("these tests do not read slot history")

        override suspend fun setSlotExercisePlan(
            slotId: String,
            exerciseId: String,
            restSeconds: Int?,
            techniqueNote: String?,
        ): DataResult<Unit> = error("these tests do not prescribe an exercise")

        override suspend fun addSlotSet(
            slotId: String,
            exerciseId: String,
            edit: SlotSetEdit,
        ): DataResult<Unit> = error("these tests do not prescribe a set")

        override suspend fun updateSlotSet(slotSetId: String, edit: SlotSetEdit): DataResult<Unit> =
            error("these tests do not edit a prescribed set")

        override suspend fun removeSlotSet(slotSetId: String): DataResult<Unit> =
            error("these tests do not remove a prescribed set")

        override suspend fun createProgram(name: String): DataResult<String> =
            error("these tests do not create a program")

        override suspend fun renameProgram(programId: String, name: String): DataResult<Unit> =
            error("these tests do not rename a program")

        override suspend fun deleteProgram(programId: String): DataResult<Unit> =
            error("these tests do not delete a program")

        override suspend fun activateProgram(programId: String): DataResult<Unit> =
            error("these tests do not activate a program")

        override suspend fun deactivateProgram(programId: String): DataResult<Unit> =
            error("these tests do not deactivate a program")

        override suspend fun moveProgram(programId: String, delta: Int): DataResult<Unit> =
            error("these tests do not move a program")

        override suspend fun addSlot(
            programId: String,
            templateId: String,
            weekday: DayOfWeek?,
        ): DataResult<Unit> = error("these tests do not add a slot")

        override suspend fun setSlotWeekday(slotId: String, weekday: DayOfWeek?): DataResult<Unit> =
            error("these tests do not schedule a slot")

        override suspend fun moveSlot(slotId: String, delta: Int): DataResult<Unit> =
            error("these tests do not move a slot")

        override suspend fun removeSlot(slotId: String): DataResult<Unit> =
            error("these tests do not remove a slot")

        override suspend fun pendingOccurrences(
            today: LocalDate,
            zone: ZoneId,
        ): DataResult<List<PendingOccurrence>> = error("these tests do not ask about missed days")

        override suspend fun skipOccurrences(
            slotIds: List<String>,
            weekStart: LocalDate,
        ): DataResult<Unit> = error("these tests do not record a skip")
    }
}
