package com.example.androidapp.ui.exercises

import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.repository.ExerciseRepository
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
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.repository.StartedSession
import com.example.androidapp.domain.repository.WorkoutRepository
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * `uiState` uses `WhileSubscribed`, so nothing flows until something
     * collects it — the tests subscribe in `backgroundScope` to model a live UI.
     */
    private fun kotlinx.coroutines.test.TestScope.observe(viewModel: ExerciseLibraryViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun publishesWholeLibraryOnceLoaded() = runTest(dispatcher) {
        val viewModel = viewModelFor(squat, bench)
        observe(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("loading should have finished", state.isLoading)
        assertEquals(listOf("Back Squat", "Barbell Bench Press"), state.items.map { it.name })
    }

    @Test
    fun query_filtersItemsAndIsEchoedBackToTheField() = runTest(dispatcher) {
        val viewModel = viewModelFor(squat, bench)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onQueryChange("squat")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("squat", state.query)
        assertEquals(listOf("Back Squat"), state.items.map { it.name })
    }

    @Test
    fun unmatchedQuery_isEmpty_butNotStillLoading() = runTest(dispatcher) {
        val viewModel = viewModelFor(squat)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onQueryChange("zzz")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("should report an empty result, not a spinner", state.isEmpty)
        assertFalse(state.isLoading)
    }

    @Test
    fun itemExposesTheRowSubtitle() = runTest(dispatcher) {
        val viewModel = viewModelFor(squat)
        observe(viewModel)
        advanceUntilIdle()

        assertEquals("Quads · Barbell", viewModel.uiState.value.items.single().subtitle)
    }

    @Test
    fun anUneditedCustomExercise_hasNoSubtitle_ratherThanSayingOtherTwice() = runTest(dispatcher) {
        // N2: a custom exercise is created from a name alone, so its muscle and
        // equipment are both OTHER. The row must not read "Other · Other".
        val viewModel = viewModelFor(custom)
        observe(viewModel)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.items.single().subtitle)
    }

    private class FakeRepository(exercises: List<Exercise>) : ExerciseRepository {
        private val state = MutableStateFlow(exercises)
        override fun observeExercises(): Flow<List<Exercise>> = state
        override suspend fun getExercise(id: String): Exercise? =
            state.value.firstOrNull { it.id == id }

        override suspend fun createCustomExercise(name: String): DataResult<Exercise> =
            error("the library screen must not create exercises")

        override suspend fun updateExercise(exercise: Exercise): DataResult<Unit> =
            error("the library screen must not edit exercises")
    }

    private companion object {
        val squat = Exercise(
            id = "back-squat",
            name = "Back Squat",
            primaryMuscle = MuscleGroup.QUADS,
            equipment = Equipment.BARBELL,
            movementPattern = MovementPattern.SQUAT,
        )
        val bench = Exercise(
            id = "barbell-bench-press",
            name = "Barbell Bench Press",
            primaryMuscle = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
        )
        val custom = Exercise(
            id = "custom-1",
            name = "Sled Push",
            primaryMuscle = MuscleGroup.OTHER,
            equipment = Equipment.OTHER,
            movementPattern = MovementPattern.OTHER,
            isCustom = true,
        )
    }

    @Test
    fun withNoWorkoutRunning_thereIsNothingToResume() = runTest(dispatcher) {
        val viewModel = viewModelFor(squat, bench)
        observe(viewModel)
        // Safe to settle fully: the clock only ticks while a workout is running,
        // so an idle library screen holds no timer open.
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.activeWorkout)
        assertEquals("", viewModel.clock.value.elapsed)
    }

    @Test
    fun anEmptyLibrary_isReportedSeparatelyFromAFailedSearch() = runTest(dispatcher) {
        val viewModel = viewModelFor()
        observe(viewModel)
        advanceUntilIdle()

        assertTrue("nothing exists at all", viewModel.uiState.value.libraryIsEmpty)
        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun aSearchWithNoHits_isNotAnEmptyLibrary() = runTest(dispatcher) {
        val viewModel = viewModelFor(squat)
        observe(viewModel)
        viewModel.onQueryChange("zzz")
        advanceUntilIdle()

        assertTrue("the search found nothing", viewModel.uiState.value.isEmpty)
        assertFalse("but the library is not empty", viewModel.uiState.value.libraryIsEmpty)
    }

    private fun viewModelFor(vararg exercises: Exercise) = ExerciseLibraryViewModel(
        exerciseRepository = FakeRepository(exercises.toList()),
        workoutRepository = NoActiveWorkout,
        timeSource = clock,
    )

    private val clock = TimeSource { Instant.parse("2026-09-28T08:00:00Z") }

    /**
     * The library screen only asks two things of the workout repository: is a
     * session running, and how many exercises does it hold. Everything else is
     * `error(...)` so an accidental call shows up as a failure rather than a
     * silent stub.
     */
    private object NoActiveWorkout : WorkoutRepository {
        override fun observeActiveSession(): Flow<WorkoutSession?> = flowOf(null)
        override fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>> =
            flowOf(emptyList())

        override fun observeSets(sessionId: String): Flow<List<SetEntry>> = flowOf(emptyList())
        override fun observeHistory(): Flow<List<WorkoutSummary>> = flowOf(emptyList())
        override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(null)
        override suspend fun startOrResumeSession(): DataResult<StartedSession> = unused()
        override suspend fun addExercise(sessionId: String, exerciseId: String): DataResult<Unit> = unused()
        override suspend fun removeExercise(sessionExerciseId: String): DataResult<Unit> = unused()
        override suspend fun finishExercise(sessionExerciseId: String): DataResult<Unit> = unused()
        override suspend fun reopenExercise(sessionExerciseId: String): DataResult<Unit> = unused()
        override suspend fun finishSession(sessionId: String): DataResult<Unit> = unused()
        override suspend fun setReadinessNote(sessionId: String, note: String?): DataResult<Unit> = unused()
        override suspend fun deleteSession(sessionId: String): DataResult<Unit> = unused()
        override suspend fun logSet(
            sessionExerciseId: String,
            reps: Int,
            weightGrams: Long,
            setType: SetType,
        ): DataResult<Unit> = unused()
        override suspend fun updateSet(
            setId: String,
            reps: Int,
            weightGrams: Long,
            rpe: Int?,
            note: String?,
        ): DataResult<Unit> = unused()
        override suspend fun deleteSet(setId: String): DataResult<Unit> = unused()
        override suspend fun previousPerformance(
            exerciseId: String,
            currentSessionId: String,
        ): DataResult<PreviousPerformance> = unused()
        override suspend fun startRest(seconds: Int): DataResult<Instant> = unused()
        override suspend fun adjustRest(deltaSeconds: Int): DataResult<Instant> = unused()
        override suspend fun clearRest(): DataResult<Unit> = unused()

        private fun unused(): Nothing = error("the library screen must not call this")
    }
}
