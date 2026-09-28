package com.example.androidapp.ui.workout

import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.repository.WorkoutRepository
import com.example.androidapp.domain.successUnit
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val clock = TimeSource { Instant.parse("2026-09-28T08:00:00Z") }

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
    }

    @Test
    fun startsASessionOnEntry_soNothingCanBeLostBeforeItExists() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = ActiveWorkoutViewModel(repository, clock)
        observe(viewModel)
        settle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("s1", state.sessionId)
        // startedAt is rendered in the device's zone, so assert its shape rather
        // than a fixed string; elapsed is zone-independent and is asserted exactly.
        assertTrue("expected HH:mm, got '${state.startedAt}'", Regex("""\d{2}:\d{2}""").matches(state.startedAt))
        // 60 minutes rolls into the hour field rather than rendering as "60:00".
        assertEquals("1:00:00", state.elapsed)
        assertTrue(state.isEmpty)
    }

    @Test
    fun resumingAnOpenSession_keepsTheOriginalId() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository().apply {
            sessions.value = WorkoutSession(id = "existing", startedAt = Instant.parse("2026-09-28T07:30:00Z"))
        }
        val viewModel = ActiveWorkoutViewModel(repository, clock)
        observe(viewModel)
        settle()

        assertEquals("existing", viewModel.uiState.value.sessionId)
    }

    @Test
    fun addingAnExercise_showsItAsARow() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = ActiveWorkoutViewModel(repository, clock)
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
        val viewModel = ActiveWorkoutViewModel(repository, clock)
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
        val viewModel = ActiveWorkoutViewModel(repository, clock)
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
        val viewModel = ActiveWorkoutViewModel(repository, clock)
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
        val viewModel = ActiveWorkoutViewModel(repository, clock)
        observe(viewModel)
        settle()

        viewModel.onDiscard()
        settle()

        assertTrue(viewModel.closed.value)
    }

    @Test
    fun removingAnExercise_dropsTheRow() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = ActiveWorkoutViewModel(repository, clock)
        observe(viewModel)
        settle()

        viewModel.onAddExercise("back-squat")
        settle()
        val rowId = viewModel.uiState.value.exercises.single().id

        viewModel.onRemoveExercise(rowId)
        settle()

        assertTrue(viewModel.uiState.value.exercises.isEmpty())
    }

    private class FakeWorkoutRepository : WorkoutRepository {
        val sessions = MutableStateFlow<WorkoutSession?>(null)
        val exercises = MutableStateFlow<List<SessionExercise>>(emptyList())
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

        override suspend fun discardSession(sessionId: String): DataResult<Unit> {
            sessions.value = null
            exercises.value = emptyList()
            return successUnit()
        }
    }
}
