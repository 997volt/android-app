package com.example.androidapp.ui.adherence

import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.AdherenceReport
import com.example.androidapp.domain.model.DayOccurrence
import com.example.androidapp.domain.model.MonthAdherence
import com.example.androidapp.domain.model.OccurrenceState
import com.example.androidapp.domain.model.Streak
import com.example.androidapp.domain.repository.AdherenceRepository
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val repository = FakeAdherenceRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.month).isEqualTo(october)
        assertThat(repository.asked).containsExactly(october)
    }

    @Test
    fun goingBack_loadsThatMonth_andLeavesForwardOpen() = runTest(dispatcher) {
        val repository = FakeAdherenceRepository()
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
        val viewModel = viewModel(FakeAdherenceRepository())
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
        val repository = FakeAdherenceRepository()
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
        val repository = FakeAdherenceRepository()
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
        val viewModel = viewModel(FakeAdherenceRepository())
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
        val repository = FakeAdherenceRepository().apply {
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
        val repository = FakeAdherenceRepository().apply {
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
        val repository = FakeAdherenceRepository().apply {
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

    @Test
    fun selectingADay_readsThatDaysOccurrences() = runTest(dispatcher) {
        // ROADMAP P3.13: the correction dialog is keyed by the day that was tapped.
        val repository = FakeAdherenceRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)
        advanceUntilIdle()
        val day = LocalDate.of(2026, 10, 6)
        repository.day = DataResult.Success(
            listOf(
                DayOccurrence(
                    slotId = "s1",
                    templateId = "t1",
                    templateName = "Heavy lower",
                    date = day,
                    weekStart = LocalDate.of(2026, 10, 5),
                    state = OccurrenceState.MISSED,
                    canCorrect = true,
                ),
            ),
        )

        viewModel.onSelectDay(day)
        advanceUntilIdle()

        assertThat(repository.daysAsked).contains(day)
        assertThat(viewModel.uiState.value.day?.occurrences?.single()?.slotId).isEqualTo("s1")

        viewModel.onDismissDay()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.day).isNull()
    }

    @Test
    fun correctingASkip_writesIt_andReReadsTheOpenDay() = runTest(dispatcher) {
        val repository = FakeAdherenceRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)
        advanceUntilIdle()
        val day = LocalDate.of(2026, 10, 6)
        val occurrence = DayOccurrence(
            slotId = "s1",
            templateId = "t1",
            templateName = "Heavy lower",
            date = day,
            weekStart = LocalDate.of(2026, 10, 5),
            state = OccurrenceState.MISSED,
            canCorrect = true,
        )
        viewModel.onSelectDay(day)
        advanceUntilIdle()
        val readsBefore = repository.daysAsked.size

        viewModel.onSetSkipped(occurrence, skipped = true)
        advanceUntilIdle()

        assertThat(repository.corrections)
            .containsExactly(Triple("s1", LocalDate.of(2026, 10, 5), true))
        // The dialog's rows and the ratio behind them both re-read.
        assertThat(repository.daysAsked.size).isEqualTo(readsBefore + 1)
    }

    @Test
    fun theRunOfScheduledWork_reachesTheState() = runTest(dispatcher) {
        // ROADMAP P3.15: measured to today, so it reads the same while the grid shows history.
        val repository = FakeAdherenceRepository()
        repository.streak = DataResult.Success(
            Streak(count = 4, startedOn = LocalDate.of(2026, 10, 1)),
        )
        val viewModel = viewModel(repository)
        observe(viewModel)

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.streak?.count).isEqualTo(4)
        assertThat(viewModel.uiState.value.streak?.startedOn).isEqualTo(LocalDate.of(2026, 10, 1))
    }

    @Test
    fun aFailedStreakRead_leavesTheMonthsNumbersOnScreen() = runTest(dispatcher) {
        val repository = FakeAdherenceRepository()
        repository.streak = DataResult.Failure(DataError.Storage(IOException("disk full")))
        val viewModel = viewModel(repository)
        observe(viewModel)

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.streak).isNull()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    private fun viewModel(repository: FakeAdherenceRepository) =
        AdherenceViewModel(repository, TimeSource { now })

    /** A collect never returns, so it runs in the background scope the test cancels. */
    private fun TestScope.observe(viewModel: AdherenceViewModel) {
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
    }

    /** Hand-written, because the project uses no mocking framework (DECISIONS.md). */
    private class FakeAdherenceRepository : AdherenceRepository {
        var report: DataResult<AdherenceReport> =
            DataResult.Success(AdherenceReport(hasActiveProgram = true, adherence = MonthAdherence()))

        /** Every month the screen asked for, in order. */
        val asked = mutableListOf<YearMonth>()

        /** Every deload write, as the program, week and state it claimed (ROADMAP P3.10). */
        val deloads = mutableListOf<Triple<String, LocalDate, Boolean>>()

        override suspend fun monthAdherence(
            month: YearMonth,
            today: LocalDate,
            zone: ZoneId,
        ): DataResult<AdherenceReport> {
            asked += month
            return report
        }

        /** What the streak read answers with; none unless a test sets one (ROADMAP P3.15). */
        var streak: DataResult<Streak?> = DataResult.Success(null)

        override suspend fun streak(today: LocalDate, zone: ZoneId): DataResult<Streak?> = streak

        override suspend fun setDeloadWeek(
            programId: String,
            weekStart: LocalDate,
            marked: Boolean,
        ): DataResult<Unit> {
            deloads += Triple(programId, weekStart, marked)
            return DataResult.Success(Unit)
        }

        /** What the open day answers with; empty unless a test sets it (P3.13). */
        var day: DataResult<List<DayOccurrence>> = DataResult.Success(emptyList())

        /** Every day the dialog asked for, in order. */
        val daysAsked = mutableListOf<LocalDate>()

        /** Every skip correction, as the slot, week and state it claimed (P3.13). */
        val corrections = mutableListOf<Triple<String, LocalDate, Boolean>>()

        override suspend fun occurrencesOn(
            date: LocalDate,
            today: LocalDate,
            zone: ZoneId,
        ): DataResult<List<DayOccurrence>> {
            daysAsked += date
            return day
        }

        override suspend fun setOccurrenceSkipped(
            slotId: String,
            weekStart: LocalDate,
            skipped: Boolean,
        ): DataResult<Unit> {
            corrections += Triple(slotId, weekStart, skipped)
            return DataResult.Success(Unit)
        }
    }
}
