package com.example.androidapp.ui.workout

import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.RestNotifier
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.repository.WorkoutRepository
import com.example.androidapp.domain.successUnit
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val clock = TimeSource { FIXED_INSTANT }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The elapsed-time ticker is an unbounded `delay` loop, so `advanceUntilIdle`
     * would never return. Advancing by a bounded amount lets the flows settle
     * without running the ticker forever.
     */
    private fun TestScope.settle() {
        advanceTimeBy(1)
        runCurrent()
    }

    private fun TestScope.observe(viewModel: ActiveWorkoutViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        // The clock is a separate flow now (F16), so it needs its own subscriber to
        // become active — exactly as the screen does.
        backgroundScope.launch { viewModel.clock.collect {} }
    }

    private fun viewModelFor(
        repository: FakeWorkoutRepository,
        notifier: FakeRestNotifier = FakeRestNotifier(),
    ) = ActiveWorkoutViewModel(repository, clock, notifier)

    @Test
    fun startsASessionOnEntry_soNothingCanBeLostBeforeItExists() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("s1", state.sessionId)
        // startedAt is rendered in the device's zone, so assert its shape rather
        // than a fixed string; elapsed is zone-independent and is asserted exactly.
        assertTrue("expected HH:mm, got '${state.startedAt}'", Regex("""\d{2}:\d{2}""").matches(state.startedAt))
        // 60 minutes rolls into the hour field rather than rendering as "60:00".
        // Read from the clock: the elapsed time deliberately no longer lives in the
        // screen state (F16), so a tick cannot rebuild the exercise list.
        assertEquals("1:00:00", viewModel.clock.value.elapsed)
        assertTrue(state.isEmpty)
    }

    @Test
    fun resumingAnOpenSession_keepsTheOriginalId() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository().apply {
            sessions.value = WorkoutSession(id = "existing", startedAt = Instant.parse("2026-09-28T07:30:00Z"))
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        assertEquals("existing", viewModel.uiState.value.sessionId)
    }

    @Test
    fun addingAnExercise_showsItAsARow() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onAddExercise("back-squat")
        settle()

        val rows = viewModel.uiState.value.exercises
        assertEquals(1, rows.size)
        assertEquals("Back Squat", rows.single().name)
        assertEquals("Quads", rows.single().muscleLabel)
        assertFalse(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun aFailedWrite_isSurfacedAsState_notThrown() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        repository.failWrites = true
        viewModel.onAddExercise("back-squat")
        settle()

        val error = viewModel.uiState.value.error
        assertNotNull("a dropped write must not be silent", error)
        assertTrue(error is DataError.Storage)
        assertTrue("nothing should have been added", viewModel.uiState.value.exercises.isEmpty())
    }

    @Test
    fun aSuccessfulWrite_clearsAPreviousError() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        repository.failWrites = true
        viewModel.onAddExercise("back-squat")
        settle()
        assertNotNull(viewModel.uiState.value.error)

        repository.failWrites = false
        viewModel.onAddExercise("back-squat")
        settle()

        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun finishingMarksTheScreenClosed_andClearsTheSession() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onFinish()
        settle()

        assertTrue("the screen should leave after finishing", viewModel.closed.value)
        assertEquals(null, viewModel.uiState.value.sessionId)
    }

    @Test
    fun discarding_alsoClosesTheScreen() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onDiscard()
        settle()

        assertTrue(viewModel.closed.value)
    }

    @Test
    fun removingAnExercise_dropsTheRow() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onAddExercise("back-squat")
        settle()
        val rowId = viewModel.uiState.value.exercises.single().id

        viewModel.onRemoveExercise(rowId)
        settle()

        assertTrue(viewModel.uiState.value.exercises.isEmpty())
    }

    @Test
    fun loggingASet_storesTheSuggestion_andArmsTheRestAlert() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val notifier = FakeRestNotifier()
        val viewModel = viewModelFor(repository, notifier)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        val logged = repository.sets.value.single()
        assertEquals(DEFAULT_REPS, logged.reps)
        assertEquals(Weight.DEFAULT_GRAMS, logged.weightGrams)
        assertEquals("a rest must be armed on set completion", 1, notifier.scheduled.size)
    }

    @Test
    fun theNextSet_prefillsWhatWasJustDone() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        // The suggestion for set 2 must echo set 1, not fall back to the default.
        val row = viewModel.uiState.value.exercises.single()
        assertEquals(1, row.sets.size)
        assertEquals(row.sets.single().reps, row.suggestion.reps)
        assertEquals(row.sets.single().weightGrams, row.suggestion.weightGrams)
    }

    @Test
    fun previousPerformance_prefillsTheFirstEverSet() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository().apply {
            previous = PreviousPerformance(
                listOf(
                    SetEntry(
                        id = "old",
                        sessionExerciseId = "old-ex",
                        setIndex = 0,
                        reps = 5,
                        weightGrams = 100_000,
                    ),
                ),
            )
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        val row = viewModel.uiState.value.exercises.single()
        assertEquals(5, row.suggestion.reps)
        assertEquals(100_000, row.suggestion.weightGrams)
        assertEquals("last time should be shown, not just used", 100_000L, row.lastTime?.weightGrams)
    }

    @Test
    fun deletingASet_thenUndoing_restoresIt() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        val logged = repository.sets.value.single()
        viewModel.onDeleteSet(logged.id)
        settle()
        assertTrue("the set should be gone", repository.sets.value.isEmpty())
        assertNotNull("an undo must be offered", viewModel.uiState.value.pendingUndo)

        viewModel.onUndoDelete()
        settle()

        assertEquals(1, repository.sets.value.size)
        assertEquals(logged.reps, repository.sets.value.single().reps)
        assertEquals(null, viewModel.uiState.value.pendingUndo)
    }

    @Test
    fun skippingTheRest_cancelsTheAlert() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val notifier = FakeRestNotifier()
        val viewModel = viewModelFor(repository, notifier)
        observe(viewModel)
        settle()

        viewModel.onSkipRest()
        settle()

        assertEquals(1, notifier.cancelCount)
    }

    @Test
    fun adjustingTheRest_reschedulesTheAlert() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val notifier = FakeRestNotifier()
        val viewModel = viewModelFor(repository, notifier)
        observe(viewModel)
        settle()

        viewModel.onAdjustRest(RestTimer.ADJUST_STEP_SECONDS)
        settle()

        assertEquals(1, notifier.scheduled.size)
    }

    @Test
    fun aTick_updatesTheClock_butNeverEmitsANewScreenState() = runTest(dispatcher) {
        // This is the mechanism behind F16, stated as a property: the exercise list
        // stops recomposing every second only if a tick cannot produce a new
        // ActiveWorkoutUiState. If this test ever fails, the ticker has crept back
        // into the screen state and the whole list is being rebuilt once a second.
        val tickingClock = MutableClock(FIXED_INSTANT)
        val viewModel = ActiveWorkoutViewModel(FakeWorkoutRepository(), tickingClock, FakeRestNotifier())

        val states = mutableListOf<ActiveWorkoutUiState>()
        val clocks = mutableListOf<WorkoutClock>()
        backgroundScope.launch { viewModel.uiState.collect { states += it } }
        backgroundScope.launch { viewModel.clock.collect { clocks += it } }
        settle()

        val stateEmissions = states.size
        val clockEmissions = clocks.size

        // Three seconds of workout time, then one ticker period.
        tickingClock.now = tickingClock.now.plusSeconds(3)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(
            "a tick must not rebuild the screen state (F16)",
            stateEmissions,
            states.size,
        )
        assertTrue("the clock must still have ticked", clocks.size > clockEmissions)
        assertEquals("1:00:03", viewModel.clock.value.elapsed)
    }

    private class FakeWorkoutRepository : WorkoutRepository {
        val sessions = MutableStateFlow<WorkoutSession?>(null)
        val exercises = MutableStateFlow<List<SessionExercise>>(emptyList())
        val sets = MutableStateFlow<List<SetEntry>>(emptyList())
        var previous: PreviousPerformance = PreviousPerformance(emptyList())
        var failWrites = false

        override fun observeActiveSession(): Flow<WorkoutSession?> = sessions

        override fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>> = exercises

        override suspend fun startOrResumeSession(): DataResult<String> {
            sessions.value?.let { return DataResult.Success(it.id) }
            val created = WorkoutSession(id = "s1", startedAt = Instant.parse("2026-09-28T07:00:00Z"))
            sessions.value = created
            return DataResult.Success(created.id)
        }

        override suspend fun addExercise(sessionId: String, exerciseId: String): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            exercises.value = exercises.value + SessionExercise(
                id = "row-${exercises.value.size}",
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = exercises.value.size,
                exerciseName = "Back Squat",
                primaryMuscle = MuscleGroup.QUADS,
                equipment = Equipment.BARBELL,
            )
            return successUnit()
        }

        override suspend fun removeExercise(sessionExerciseId: String): DataResult<Unit> {
            exercises.value = exercises.value.filterNot { it.id == sessionExerciseId }
            return successUnit()
        }

        override suspend fun finishSession(sessionId: String): DataResult<Unit> {
            sessions.value = null
            return successUnit()
        }

        override suspend fun deleteSession(sessionId: String): DataResult<Unit> {
            sessions.value = null
            exercises.value = emptyList()
            return successUnit()
        }

        override fun observeSets(sessionId: String): Flow<List<SetEntry>> = sets

        // History is exercised by its own tests; the session tests only need the
        // interface satisfied.
        override fun observeHistory(): Flow<List<WorkoutSummary>> = MutableStateFlow(emptyList())

        override fun observeSession(sessionId: String): Flow<WorkoutSession?> = MutableStateFlow(null)

        override suspend fun logSet(
            sessionExerciseId: String,
            reps: Int,
            weightGrams: Long,
            setType: SetType,
        ): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            sets.value = sets.value + SetEntry(
                id = "set-${sets.value.size}",
                sessionExerciseId = sessionExerciseId,
                setIndex = sets.value.size,
                reps = reps,
                weightGrams = weightGrams,
                setType = setType,
            )
            return successUnit()
        }

        override suspend fun updateSet(setId: String, reps: Int, weightGrams: Long): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            sets.value = sets.value.map {
                if (it.id == setId) it.copy(reps = reps, weightGrams = weightGrams) else it
            }
            return successUnit()
        }

        override suspend fun deleteSet(setId: String): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            sets.value = sets.value.filterNot { it.id == setId }
            return successUnit()
        }

        override suspend fun previousPerformance(
            exerciseId: String,
            currentSessionId: String,
        ): DataResult<PreviousPerformance> = DataResult.Success(previous)

        override suspend fun startRest(seconds: Int): DataResult<Instant> =
            DataResult.Success(FIXED_INSTANT.plusSeconds(seconds.toLong()))

        override suspend fun adjustRest(deltaSeconds: Int): DataResult<Instant> =
            DataResult.Success(FIXED_INSTANT.plusSeconds(deltaSeconds.toLong()))

        override suspend fun clearRest(): DataResult<Unit> = successUnit()
    }

    /** A clock the test can move, so elapsed time can be asserted exactly. */
    private class MutableClock(var now: Instant) : TimeSource {
        override fun now(): Instant = now
    }

    /** Records what the screen asked to be alerted about, with no Android involved. */
    private class FakeRestNotifier : RestNotifier {
        val scheduled = mutableListOf<Instant>()
        var cancelCount = 0
            private set

        override fun schedule(endsAt: Instant) {
            scheduled += endsAt
        }

        override fun cancel() {
            cancelCount++
        }
    }

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-09-28T08:00:00Z")
    }
}
