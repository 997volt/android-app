package com.example.androidapp.ui.programs

import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
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
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The point-of-start rule (ROADMAP P3.3).
 *
 * Asked only when a scheduled day this week has neither a session nor a recorded skip, and
 * asked **at the point of starting**: the two answers are asserted as intents, because one
 * starts the missed day and the other settles every miss this week before starting what was
 * asked for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramStartGateViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // A Wednesday, so "this week" has a Monday and a Tuesday behind it.
    private val now: Instant = Instant.parse("2026-10-07T12:00:00Z")
    private val monday: LocalDate = LocalDate.of(2026, 10, 5)
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

    private fun viewModel(repository: FakeProgramRepository) =
        ProgramStartGateViewModel(repository, TimeSource { now })

    @Test
    fun withNothingPending_theStartGoesStraightThrough() = runTest(dispatcher) {
        val viewModel = viewModel(FakeProgramRepository())

        viewModel.requestStart(StartIntent(templateId = "t1", label = "Bench"))
        advanceUntilIdle()

        assertThat(viewModel.prompt.value).isNull()
        assertThat(viewModel.started.value).isEqualTo(StartIntent(templateId = "t1", label = "Bench"))
    }

    @Test
    fun aMissedDay_asks_beforeAnythingStarts() = runTest(dispatcher) {
        val repository = FakeProgramRepository().apply { pending = twoMisses() }
        val viewModel = viewModel(repository)

        viewModel.requestStart(StartIntent(templateId = "bench", label = "Bench"))
        advanceUntilIdle()

        assertThat(viewModel.prompt.value)
            .isEqualTo(SkipPromptUi("Paused Squat", DayOfWeek.TUESDAY, "Bench"))
        assertNull("nothing may start while the question is open", viewModel.started.value)
    }

    @Test
    fun doingItNow_startsTheMissedDay_insteadOfWhatWasAskedFor() = runTest(dispatcher) {
        val repository = FakeProgramRepository().apply { pending = twoMisses() }
        val viewModel = viewModel(repository)
        viewModel.requestStart(StartIntent(templateId = "bench", label = "Bench"))
        advanceUntilIdle()

        viewModel.onDoItNow()

        assertThat(viewModel.started.value)
            .isEqualTo(
                StartIntent(
                    templateId = "squat-template",
                    label = "Paused Squat",
                    // The missed slot travels with the start so its own prescription seeds it (P3.8).
                    slotId = "squat-slot",
                ),
            )
        assertThat(repository.skipped).isEmpty()
    }

    @Test
    fun continuing_settlesEveryPendingOccurrence_thenStartsTheRequest() = runTest(dispatcher) {
        val repository = FakeProgramRepository().apply { pending = twoMisses() }
        val viewModel = viewModel(repository)
        viewModel.requestStart(StartIntent(templateId = "bench", label = "Bench"))
        advanceUntilIdle()

        viewModel.onContinue()
        advanceUntilIdle()

        // Both misses, in one call, for this week: asking again for the next one would turn
        // two misses into two interrogations.
        assertThat(repository.skipped).containsExactly(
            listOf("squat-slot", "press-slot") to monday,
        )
        assertThat(viewModel.started.value)
            .isEqualTo(StartIntent(templateId = "bench", label = "Bench"))
    }

    @Test
    fun dismissingTheQuestion_cancelsTheStart() = runTest(dispatcher) {
        val repository = FakeProgramRepository().apply { pending = twoMisses() }
        val viewModel = viewModel(repository)
        viewModel.requestStart(StartIntent(templateId = "bench", label = "Bench"))
        advanceUntilIdle()

        viewModel.onDismiss()

        assertThat(viewModel.prompt.value).isNull()
        assertNull("a dismissed question must not choose an answer", viewModel.started.value)
        assertThat(repository.skipped).isEmpty()
    }

    @Test
    fun aFailedAsk_doesNotBlockTheWorkout() = runTest(dispatcher) {
        // The question is an interruption; losing the ability to train over a schedule
        // lookup would be a worse failure than not asking.
        val repository = FakeProgramRepository().apply { pendingFails = true }
        val viewModel = viewModel(repository)

        viewModel.requestStart(StartIntent(templateId = "bench", label = "Bench"))
        advanceUntilIdle()

        assertThat(viewModel.prompt.value).isNull()
        assertThat(viewModel.started.value).isEqualTo(StartIntent(templateId = "bench", label = "Bench"))
    }

    @Test
    fun aFailedSkip_stillStarts_theWorkoutAndReports() = runTest(dispatcher) {
        val repository = FakeProgramRepository().apply {
            pending = twoMisses()
            skipFails = true
        }
        val viewModel = viewModel(repository)
        viewModel.requestStart(StartIntent(templateId = "bench", label = "Bench"))
        advanceUntilIdle()

        viewModel.onContinue()
        advanceUntilIdle()

        assertThat(viewModel.error.value).isInstanceOf(DataError.Storage::class.java)
        assertThat(viewModel.started.value).isEqualTo(StartIntent(templateId = "bench", label = "Bench"))
    }

    private fun twoMisses() = listOf(
        PendingOccurrence(
            slotId = "squat-slot",
            templateId = "squat-template",
            templateName = "Paused Squat",
            weekday = DayOfWeek.TUESDAY,
            date = monday.plusDays(1),
        ),
        PendingOccurrence(
            slotId = "press-slot",
            templateId = "press-template",
            templateName = "Overhead Press",
            weekday = DayOfWeek.MONDAY,
            date = monday,
        ),
    )

    /** Hand-written, because the project uses no mocking framework (DECISIONS.md). */
    private class FakeProgramRepository : ProgramRepository {
        var pending: List<PendingOccurrence> = emptyList()
        var pendingFails = false
        var skipFails = false

        /** Every skip write, as the ids and the week it claimed. */
        val skipped = mutableListOf<Pair<List<String>, LocalDate>>()

        override fun observePrograms(): Flow<List<WorkoutProgram>> = flowOf(emptyList())

        override fun observeProgram(programId: String): Flow<WorkoutProgram?> = flowOf(null)

        override fun observeActivePrograms(): Flow<List<WorkoutProgram>> = flowOf(emptyList())

        override fun observeSlots(programId: String): Flow<List<ProgramSlot>> = flowOf(emptyList())

        override fun observeSlotPrescriptions(slotId: String): Flow<List<SlotPrescription>> =
            flowOf(emptyList())

        override fun observeProgramRun(programId: String): Flow<ProgramRun?> = flowOf(null)

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

        override suspend fun pendingOccurrences(
            today: LocalDate,
            zone: ZoneId,
        ): DataResult<List<PendingOccurrence>> = if (pendingFails) {
            DataResult.Failure(DataError.Storage(IOException("the schedule could not be read")))
        } else {
            DataResult.Success(pending)
        }

        override suspend fun skipOccurrences(
            slotIds: List<String>,
            weekStart: LocalDate,
        ): DataResult<Unit> {
            skipped += slotIds to weekStart
            return if (skipFails) {
                DataResult.Failure(DataError.Storage(IOException("disk full")))
            } else {
                DataResult.Success(Unit)
            }
        }

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

        override suspend fun monthAdherence(
            month: java.time.YearMonth,
            today: LocalDate,
            zone: ZoneId,
        ): DataResult<com.example.androidapp.domain.model.AdherenceReport> =
            error("these tests do not read adherence")
    }
}
