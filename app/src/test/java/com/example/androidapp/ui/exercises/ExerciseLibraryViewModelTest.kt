package com.example.androidapp.ui.exercises

import java.io.IOException
import com.example.androidapp.domain.DataError
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
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.repository.StartedSession
import com.example.androidapp.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        /** Lets a test drive the B4 path: a read that fails rather than throws. */
        var failReads = false

        override fun observeExercises(): Flow<DataResult<List<Exercise>>> =
            if (failReads) {
                flowOf(DataResult.Failure(DataError.Storage(IOException("database is locked"))))
            } else {
                state.map { DataResult.Success(it) }
            }

        override suspend fun getExercise(id: String): DataResult<Exercise?> =
            if (failReads) {
                DataResult.Failure(DataError.Storage(IOException("database is locked")))
            } else {
                DataResult.Success(state.value.firstOrNull { it.id == id })
            }

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
    )


    @Test
    fun aFailedRead_isReported_insteadOfTakingTheScreenDown() = runTest(dispatcher) {
        // ROADMAP B4: this used to escape the flow as an exception. It is now a
        // value the screen can render where the list would have been.
        val repository = FakeRepository(emptyList()).apply { failReads = true }
        val viewModel = ExerciseLibraryViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("the failure must reach the screen", state.error)
        assertFalse(state.isLoading)
        assertTrue("an unreadable library must not claim to be empty", state.items.isEmpty())
    }
}
