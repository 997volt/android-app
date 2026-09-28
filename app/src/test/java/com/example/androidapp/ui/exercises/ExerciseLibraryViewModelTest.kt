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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val viewModel = ExerciseLibraryViewModel(FakeRepository(listOf(squat, bench)))
        observe(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("loading should have finished", state.isLoading)
        assertEquals(listOf("Back Squat", "Barbell Bench Press"), state.items.map { it.name })
    }

    @Test
    fun query_filtersItemsAndIsEchoedBackToTheField() = runTest(dispatcher) {
        val viewModel = ExerciseLibraryViewModel(FakeRepository(listOf(squat, bench)))
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
        val viewModel = ExerciseLibraryViewModel(FakeRepository(listOf(squat)))
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onQueryChange("zzz")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("should report an empty result, not a spinner", state.isEmpty)
        assertFalse(state.isLoading)
    }

    @Test
    fun itemExposesRowLabelsForTheListUi() = runTest(dispatcher) {
        val viewModel = ExerciseLibraryViewModel(FakeRepository(listOf(squat)))
        observe(viewModel)
        advanceUntilIdle()

        val item = viewModel.uiState.value.items.single()
        assertEquals("Quads", item.muscleLabel)
        assertEquals("Barbell", item.equipmentLabel)
    }

    private class FakeRepository(exercises: List<Exercise>) : ExerciseRepository {
        private val state = MutableStateFlow(exercises)
        override fun observeExercises(): Flow<List<Exercise>> = state
        override suspend fun getExercise(id: String): Exercise? =
            state.value.firstOrNull { it.id == id }
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
    }
}
