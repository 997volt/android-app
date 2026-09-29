package com.example.androidapp.ui.workout

import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.domain.repository.StartedSession
import com.example.androidapp.domain.repository.WorkoutRepository
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ExercisePickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** `uiState` uses `WhileSubscribed`, so nothing flows until it is collected. */
    private fun kotlinx.coroutines.test.TestScope.observe(viewModel: ExercisePickerViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun creatingAnExercise_storesIt_andAddsItToTheSession() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository()
        val workouts = FakeWorkoutRepository()
        val viewModel = ExercisePickerViewModel(exercises, workouts)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onCreateExercise("  Sled Push  ")
        advanceUntilIdle()

        // Created from the name alone, as an unedited custom exercise (N2).
        assertEquals("Sled Push", exercises.created?.name)
        assertEquals(true, exercises.created?.isCustom)
        assertEquals(MuscleGroup.OTHER, exercises.created?.primaryMuscle)
        assertEquals(Equipment.OTHER, exercises.created?.equipment)
        assertEquals(MovementPattern.OTHER, exercises.created?.movementPattern)

        // And appended to the open workout, so the picker can pop.
        assertEquals(listOf("s1" to exercises.created?.id), workouts.added)
        assertTrue(viewModel.added.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun aFailedCreate_reportsIt_andAddsNothing() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository().apply { failCreates = true }
        val workouts = FakeWorkoutRepository()
        val viewModel = ExercisePickerViewModel(exercises, workouts)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onCreateExercise("Sled Push")
        advanceUntilIdle()

        assertNotNull(viewModel.error.value)
        assertTrue(workouts.added.isEmpty())
        assertFalse(viewModel.added.value)
    }

    @Test
    fun withNoOpenSession_theAppendFails_ratherThanFilingItUnderNothing() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository()
        val workouts = FakeWorkoutRepository().apply { session.value = null }
        val viewModel = ExercisePickerViewModel(exercises, workouts)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onCreateExercise("Sled Push")
        advanceUntilIdle()

        // The library row still exists; only the append failed.
        assertNotNull(exercises.created)
        assertEquals(DataError.NotFound, viewModel.error.value)
        assertFalse(viewModel.added.value)
    }

    @Test
    fun aFailedAppend_isReported() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository()
        val workouts = FakeWorkoutRepository().apply { failAdds = true }
        val viewModel = ExercisePickerViewModel(exercises, workouts)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onCreateExercise("Sled Push")
        advanceUntilIdle()

        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.added.value)
    }

    @Test
    fun pickingAnExistingExercise_alsoReportsAFailedAppend() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository(listOf(seeded))
        val workouts = FakeWorkoutRepository().apply { failAdds = true }
        val viewModel = ExercisePickerViewModel(exercises, workouts)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        assertNotNull("a dropped append must not be silent", viewModel.error.value)
        assertFalse(viewModel.added.value)
    }

    @Test
    fun theErrorCanBeCleared_onceItHasBeenShown() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository().apply { failCreates = true }
        val viewModel = ExercisePickerViewModel(exercises, FakeWorkoutRepository())
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onCreateExercise("Sled Push")
        advanceUntilIdle()
        viewModel.onErrorShown()

        assertNull(viewModel.error.value)
    }

    private class FakeExerciseRepository(
        initial: List<Exercise> = emptyList(),
    ) : ExerciseRepository {
        private val state = MutableStateFlow(initial)
        var created: Exercise? = null
        var failCreates = false

        override fun observeExercises(): Flow<List<Exercise>> = state
        override suspend fun getExercise(id: String): Exercise? =
            state.value.firstOrNull { it.id == id }

        override suspend fun createCustomExercise(name: String): DataResult<Exercise> {
            if (failCreates) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            val exercise = Exercise(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                primaryMuscle = MuscleGroup.OTHER,
                equipment = Equipment.OTHER,
                movementPattern = MovementPattern.OTHER,
                isCustom = true,
            )
            created = exercise
            state.value = state.value + exercise
            return DataResult.Success(exercise)
        }

        override suspend fun updateExercise(exercise: Exercise): DataResult<Unit> =
            DataResult.Success(Unit)
    }

    /** Only the picker's two calls matter here; everything else fails loudly. */
    private class FakeWorkoutRepository : WorkoutRepository {
        val session = MutableStateFlow<WorkoutSession?>(
            WorkoutSession(id = "s1", startedAt = Instant.parse("2026-09-29T07:00:00Z")),
        )
        val added = mutableListOf<Pair<String, String?>>()
        var failAdds = false

        override fun observeActiveSession(): Flow<WorkoutSession?> = session
        override suspend fun addExercise(sessionId: String, exerciseId: String): DataResult<Unit> {
            if (failAdds) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            added += sessionId to exerciseId
            return DataResult.Success(Unit)
        }

        override fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>> =
            flowOf(emptyList())

        override fun observeSets(sessionId: String): Flow<List<SetEntry>> = flowOf(emptyList())
        override fun observeHistory(): Flow<List<WorkoutSummary>> = flowOf(emptyList())
        override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(null)
        override suspend fun startOrResumeSession(): DataResult<StartedSession> = unused()
        override suspend fun removeExercise(sessionExerciseId: String): DataResult<Unit> = unused()
        override suspend fun finishExercise(sessionExerciseId: String): DataResult<Unit> = unused()
        override suspend fun reopenExercise(sessionExerciseId: String): DataResult<Unit> = unused()
        override suspend fun rateExercise(
            sessionExerciseId: String,
            muscleFeel: Int?,
            jointPain: Int?,
        ): DataResult<Unit> = unused()
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

        private fun unused(): Nothing = error("the picker must not call this")
    }

    private companion object {
        val seeded = Exercise(
            id = "back-squat",
            name = "Back Squat",
            primaryMuscle = MuscleGroup.QUADS,
            equipment = Equipment.BARBELL,
            movementPattern = MovementPattern.SQUAT,
        )
    }
}
