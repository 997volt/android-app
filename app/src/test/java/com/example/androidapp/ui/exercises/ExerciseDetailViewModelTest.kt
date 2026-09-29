package com.example.androidapp.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.repository.ExerciseRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Under Robolectric rather than plain JVM: the ViewModel reads its argument out
 * of a `SavedStateHandle` through the type-safe route, and that path touches a
 * real `android.os.Bundle`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsTheExerciseNamedByTheRoute() = runTest(dispatcher) {
        val viewModel = viewModelFor(FakeRepository(mutableListOf(custom)))

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Sled Push", state.exercise?.name)
        assertFalse(state.notFound)
    }

    @Test
    fun anUnknownId_isNotFound_ratherThanAnEndlessSpinner() = runTest(dispatcher) {
        val viewModel = viewModelFor(FakeRepository(mutableListOf()))

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.notFound)
    }

    @Test
    fun onlyACustomExercise_canBeEdited() = runTest(dispatcher) {
        val customViewModel = viewModelFor(FakeRepository(mutableListOf(custom)))
        advanceUntilIdle()
        assertTrue(customViewModel.uiState.value.canEdit)

        val seededViewModel = viewModelFor(
            FakeRepository(mutableListOf(seeded)),
            exerciseId = "back-squat",
        )
        advanceUntilIdle()
        assertFalse(seededViewModel.uiState.value.canEdit)
    }

    @Test
    fun saving_writesTheEditedAttributes_andLeavesEditMode() = runTest(dispatcher) {
        val repository = FakeRepository(mutableListOf(custom))
        val viewModel = viewModelFor(repository)
        advanceUntilIdle()

        viewModel.onEdit()
        viewModel.onSave(
            ExerciseEdit(
                name = "  Sled Push Heavy  ",
                primaryMuscle = MuscleGroup.QUADS,
                equipment = Equipment.MACHINE,
                movementPattern = MovementPattern.SQUAT,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEditing)
        // The name is trimmed on the way in, so the stored row and the screen agree.
        assertEquals("Sled Push Heavy", state.exercise?.name)
        assertEquals(MuscleGroup.QUADS, state.exercise?.primaryMuscle)
        assertEquals(Equipment.MACHINE, state.exercise?.equipment)
        assertEquals(MovementPattern.SQUAT, state.exercise?.movementPattern)
        assertEquals("Sled Push Heavy", repository.saved?.name)
    }

    @Test
    fun aFailedSave_keepsTheFormOpen_andSurfacesTheError() = runTest(dispatcher) {
        val repository = FakeRepository(mutableListOf(custom)).apply { failWrites = true }
        val viewModel = viewModelFor(repository)
        advanceUntilIdle()

        viewModel.onEdit()
        viewModel.onSave(
            ExerciseEdit("Sled Push Heavy", MuscleGroup.QUADS, Equipment.MACHINE, MovementPattern.SQUAT),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("a failed save must not look like it worked", state.isEditing)
        assertNotNull(state.error)
        // The stored exercise is untouched, so re-opening the form shows the truth.
        assertEquals("Sled Push", state.exercise?.name)
    }

    private fun viewModelFor(
        repository: FakeRepository,
        exerciseId: String = "custom-1",
    ) = ExerciseDetailViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(mapOf("exerciseId" to exerciseId)),
    )

    private class FakeRepository(initial: MutableList<Exercise>) : ExerciseRepository {
        private val state = MutableStateFlow(initial.toList())
        var saved: Exercise? = null
        var failWrites = false

        override fun observeExercises(): Flow<List<Exercise>> = state
        override suspend fun getExercise(id: String): Exercise? =
            state.value.firstOrNull { it.id == id }

        override suspend fun createCustomExercise(name: String): DataResult<Exercise> =
            error("the detail screen must not create exercises")

        override suspend fun updateExercise(exercise: Exercise): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            saved = exercise
            state.value = state.value.map { if (it.id == exercise.id) exercise else it }
            return DataResult.Success(Unit)
        }
    }

    private companion object {
        val seeded = Exercise(
            id = "back-squat",
            name = "Back Squat",
            primaryMuscle = MuscleGroup.QUADS,
            equipment = Equipment.BARBELL,
            movementPattern = MovementPattern.SQUAT,
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
}
