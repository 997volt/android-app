package com.example.androidapp.ui.templates

import java.time.DayOfWeek
import com.example.androidapp.domain.model.SetType
import kotlinx.coroutines.flow.flowOf
import com.example.androidapp.domain.repository.TemplateSetEdit
import com.example.androidapp.domain.model.TemplateSet
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.domain.repository.TemplateRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.junit.runner.RunWith

/**
 * Under Robolectric rather than plain JVM: the editor reads its template id out of
 * a `SavedStateHandle` through the type-safe route, and that path touches a real
 * `android.os.Bundle`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TemplateEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.observe(viewModel: TemplateEditorViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private fun viewModelFor(repository: TemplateRepository, templateId: String = "t1") =
        TemplateEditorViewModel(repository, SavedStateHandle(mapOf("templateId" to templateId)))

    @Test
    fun theTemplateAndItsExercises_comeFromTheRepository() = runTest(dispatcher) {
        val repository = FakeTemplateRepository().apply {
            templates.value = listOf(WorkoutTemplate(id = "t1", name = "Push day", exerciseCount = 1))
            exercises.value = listOf(exercise("te1", 0, "Back Squat"))
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Push day", state.template?.name)
        assertEquals(listOf("Back Squat"), state.exercises.map { it.exerciseName })
        assertFalse(state.notFound)
    }

    @Test
    fun aTemplateThatIsGone_readsAsNotFound_soTheScreenCanLeave() = runTest(dispatcher) {
        val viewModel = viewModelFor(FakeTemplateRepository())
        observe(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.notFound)
    }

    @Test
    fun renaming_passesTheNewNameThrough() = runTest(dispatcher) {
        val repository = FakeTemplateRepository().apply {
            templates.value = listOf(WorkoutTemplate(id = "t1", name = "Push day"))
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onRename("  Legs  ")
        advanceUntilIdle()

        assertEquals(listOf("t1" to "  Legs  "), repository.renamed)
    }

    @Test
    fun moving_passesTheDirectionThrough() = runTest(dispatcher) {
        val repository = FakeTemplateRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onMoveExercise("te1", -1)
        advanceUntilIdle()

        assertEquals(listOf("te1" to -1), repository.moved)
    }

    @Test
    fun removing_asksTheRepositoryToRemoveThatRow() = runTest(dispatcher) {
        val repository = FakeTemplateRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onRemoveExercise("te1")
        advanceUntilIdle()

        assertEquals(listOf("te1"), repository.removed)
    }

    @Test
    fun deletingTheTemplate_reportsIt_soTheScreenCanLeave() = runTest(dispatcher) {
        val repository = FakeTemplateRepository().apply {
            templates.value = listOf(WorkoutTemplate(id = "t1", name = "Push day"))
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onDeleteTemplate()
        advanceUntilIdle()

        assertEquals(listOf("t1"), repository.deleted)
        assertTrue(viewModel.deleted.value)
    }

    @Test
    fun aFailedWrite_surfacesTheError_ratherThanLeavingTheScreenLying() = runTest(dispatcher) {
        val repository = FakeTemplateRepository().apply {
            templates.value = listOf(WorkoutTemplate(id = "t1", name = "Push day"))
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        advanceUntilIdle()
        repository.failWrites = true

        viewModel.onMoveExercise("te1", 1)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse("a failed delete must not close the editor", viewModel.deleted.value)
    }

    @Test
    fun theErrorCanBeCleared_onceItHasBeenShown() = runTest(dispatcher) {
        val repository = FakeTemplateRepository().apply { failWrites = true }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        advanceUntilIdle()
        viewModel.onRemoveExercise("te1")
        advanceUntilIdle()

        viewModel.onErrorShown()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
    }

    private fun exercise(id: String, position: Int, name: String) = TemplateExercise(
        id = id,
        templateId = "t1",
        exerciseId = "back-squat",
        position = position,
        exerciseName = name,
        primaryMuscle = MuscleGroup.QUADS,
        equipment = Equipment.BARBELL,
    )

    private class FakeTemplateRepository : TemplateRepository {
        val templates = MutableStateFlow<List<WorkoutTemplate>>(emptyList())
        val exercises = MutableStateFlow<List<TemplateExercise>>(emptyList())
        val renamed = mutableListOf<Pair<String, String>>()
        val removed = mutableListOf<String>()
        val moved = mutableListOf<Pair<String, Int>>()
        val deleted = mutableListOf<String>()
        val addedSets = mutableListOf<Pair<String, TemplateSetEdit>>()
        val duplicated = mutableListOf<String>()
        val savedPlans = mutableListOf<Triple<String, Int?, String?>>()
        var failWrites = false

        override fun observeTemplates(): Flow<List<WorkoutTemplate>> = templates

        override fun observeTemplate(templateId: String): Flow<WorkoutTemplate?> =
            templates.map { rows -> rows.firstOrNull { it.id == templateId } }

        override fun observeExercises(templateId: String): Flow<List<TemplateExercise>> = exercises
        override fun observeSets(templateId: String): Flow<List<TemplateSet>> = flowOf(emptyList())
        override suspend fun addSet(
            templateExerciseId: String,
            edit: TemplateSetEdit,
        ): DataResult<Unit> {
            addedSets += templateExerciseId to edit
            return DataResult.Success(Unit)
        }

        override suspend fun updateSet(
            templateSetId: String,
            edit: TemplateSetEdit,
        ): DataResult<Unit> = DataResult.Success(Unit)

        override suspend fun removeSet(templateSetId: String): DataResult<Unit> =
            DataResult.Success(Unit)
        override suspend fun duplicateSets(templateExerciseId: String): DataResult<Unit> {
            duplicated += templateExerciseId
            return DataResult.Success(Unit)
        }
        override suspend fun setExercisePlan(
            templateExerciseId: String,
            restSeconds: Int?,
            techniqueNote: String?,
        ): DataResult<Unit> {
            savedPlans += Triple(templateExerciseId, restSeconds, techniqueNote)
            return DataResult.Success(Unit)
        }

        override suspend fun createTemplate(name: String): DataResult<String> =
            DataResult.Success("t-new")

        override suspend fun renameTemplate(templateId: String, name: String): DataResult<Unit> {
            if (failWrites) return failure()
            renamed += templateId to name
            return DataResult.Success(Unit)
        }

        override suspend fun setWeekday(
            templateId: String,
            weekday: DayOfWeek?,
        ): DataResult<Unit> = error("these tests do not schedule a plan")

        override suspend fun deleteTemplate(templateId: String): DataResult<Unit> {
            if (failWrites) return failure()
            deleted += templateId
            return DataResult.Success(Unit)
        }

        override suspend fun addExercise(templateId: String, exerciseId: String): DataResult<Unit> =
            DataResult.Success(Unit)

        override suspend fun removeExercise(templateExerciseId: String): DataResult<Unit> {
            if (failWrites) return failure()
            removed += templateExerciseId
            return DataResult.Success(Unit)
        }

        override suspend fun moveExercise(templateExerciseId: String, delta: Int): DataResult<Unit> {
            if (failWrites) return failure()
            moved += templateExerciseId to delta
            return DataResult.Success(Unit)
        }

        private fun failure() = DataResult.Failure(DataError.Storage(IOException("disk full")))
    }


    @Test
    fun addingAPlannedSet_passesItsTargetsToTheRepository() = runTest(dispatcher) {
        // The plan editor's whole job: what the dialog collected is what is stored
        // (ROADMAP N14).
        val repository = FakeTemplateRepository()
        val viewModel = viewModelFor(repository)
        advanceUntilIdle()

        viewModel.onAddSet(
            "te1",
            TemplateSetEdit(
                role = SetType.TOP_SET,
                targetWeightGrams = 140_000L,
                targetRepsMin = 1,
                targetRepsMax = 2,
                targetRpeHalves = 9,
                note = "grind",
            ),
        )
        advanceUntilIdle()

        val (exerciseId, edit) = repository.addedSets.single()
        assertEquals("te1", exerciseId)
        assertEquals(SetType.TOP_SET, edit.role)
        assertEquals(140_000L, edit.targetWeightGrams)
        assertEquals(2, edit.targetRepsMax)
        assertEquals(9, edit.targetRpeHalves)
    }

    @Test
    fun duplicatingAPlan_asksTheRepositoryForTheExercise() = runTest(dispatcher) {
        val repository = FakeTemplateRepository()
        val viewModel = viewModelFor(repository)
        advanceUntilIdle()

        viewModel.onDuplicateSets("te1")
        advanceUntilIdle()

        assertEquals(listOf("te1"), repository.duplicated)
    }

    @Test
    fun savingARestAndCue_passesBothThrough() = runTest(dispatcher) {
        val repository = FakeTemplateRepository()
        val viewModel = viewModelFor(repository)
        advanceUntilIdle()

        viewModel.onSaveExercisePlan("te1", restSeconds = 180, techniqueNote = "Slow descent")
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("te1", 180, "Slow descent")),
            repository.savedPlans,
        )
    }
}
