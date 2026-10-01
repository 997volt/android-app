package com.example.androidapp.ui.templates

import java.time.DayOfWeek
import kotlinx.coroutines.flow.flowOf
import com.example.androidapp.domain.repository.TemplateSetEdit
import com.example.androidapp.domain.model.TemplateSet
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
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

@OptIn(ExperimentalCoroutinesApi::class)
class TemplatesViewModelTest {

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
    private fun TestScope.observe(viewModel: TemplatesViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun theList_comesFromTheRepository() = runTest(dispatcher) {
        val repository = FakeTemplateRepository().apply {
            templates.value = listOf(
                WorkoutTemplate(id = "t1", name = "Push day", exerciseCount = 5),
            )
        }
        val viewModel = TemplatesViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Push day"), state.templates.map { it.name })
    }

    @Test
    fun creating_reportsTheNewId_soTheListCanOpenItsEditor() = runTest(dispatcher) {
        val repository = FakeTemplateRepository().apply { createdId = "t9" }
        val viewModel = TemplatesViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onCreateTemplate("New template")
        advanceUntilIdle()

        assertEquals("New template", repository.createdName)
        assertEquals("t9", viewModel.createdTemplateId.value)
    }

    @Test
    fun theHandledId_isCleared_soARecompositionCannotReopenTheEditor() = runTest(dispatcher) {
        val repository = FakeTemplateRepository()
        val viewModel = TemplatesViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()
        viewModel.onCreateTemplate("New template")
        advanceUntilIdle()

        viewModel.onCreatedHandled()

        assertNull(viewModel.createdTemplateId.value)
    }

    @Test
    fun aFailedCreate_surfacesTheError_insteadOfPretendingItSaved() = runTest(dispatcher) {
        val repository = FakeTemplateRepository().apply { failWrites = true }
        val viewModel = TemplatesViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onCreateTemplate("New template")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertNull("nothing was created, so nothing may be opened", viewModel.createdTemplateId.value)
    }

    @Test
    fun theErrorCanBeCleared_onceItHasBeenShown() = runTest(dispatcher) {
        val repository = FakeTemplateRepository().apply { failWrites = true }
        val viewModel = TemplatesViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()
        viewModel.onCreateTemplate("New template")
        advanceUntilIdle()

        viewModel.onErrorShown()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun aSuccessfulCreate_clearsAnEarlierError() = runTest(dispatcher) {
        val repository = FakeTemplateRepository().apply { failWrites = true }
        val viewModel = TemplatesViewModel(repository)
        observe(viewModel)
        advanceUntilIdle()
        viewModel.onCreateTemplate("New template")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        repository.failWrites = false
        viewModel.onCreateTemplate("New template")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertTrue(repository.templates.value.isNotEmpty() || viewModel.createdTemplateId.value != null)
    }

    private class FakeTemplateRepository : TemplateRepository {
        val templates = MutableStateFlow<List<WorkoutTemplate>>(emptyList())
        val exercises = MutableStateFlow<List<TemplateExercise>>(emptyList())
        var createdId = "t-new"
        var createdName: String? = null
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
            error("these tests do not write a plan")
        }

        override suspend fun updateSet(
            templateSetId: String,
            edit: TemplateSetEdit,
        ): DataResult<Unit> {
            error("these tests do not write a plan")
        }

        override suspend fun removeSet(templateSetId: String): DataResult<Unit> {
            error("these tests do not write a plan")
        }
        override suspend fun duplicateSets(templateExerciseId: String): DataResult<Unit> {
            error("these tests do not write a plan")
        }
        override suspend fun setExercisePlan(
            templateExerciseId: String,
            restSeconds: Int?,
            techniqueNote: String?,
        ): DataResult<Unit> {
            error("these tests do not write a plan")
        }

        override suspend fun createTemplate(name: String): DataResult<String> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            createdName = name
            templates.value = templates.value + WorkoutTemplate(id = createdId, name = name)
            return DataResult.Success(createdId)
        }

        override suspend fun renameTemplate(templateId: String, name: String): DataResult<Unit> =
            writeFailureOrSuccess()

        override suspend fun setSupersetGroup(
            templateExerciseIds: List<String>,
            group: Int?,
        ): DataResult<Unit> = DataResult.Success(Unit)

        override suspend fun setWeekday(
            templateId: String,
            weekday: DayOfWeek?,
        ): DataResult<Unit> = error("these tests do not schedule a plan")

        override suspend fun deleteTemplate(templateId: String): DataResult<Unit> =
            writeFailureOrSuccess()

        override suspend fun addExercise(templateId: String, exerciseId: String): DataResult<Unit> =
            writeFailureOrSuccess()

        override suspend fun removeExercise(templateExerciseId: String): DataResult<Unit> =
            writeFailureOrSuccess()

        override suspend fun moveExercise(templateExerciseId: String, delta: Int): DataResult<Unit> =
            writeFailureOrSuccess()

        private fun writeFailureOrSuccess(): DataResult<Unit> = if (failWrites) {
            DataResult.Failure(DataError.Storage(IOException("disk full")))
        } else {
            DataResult.Success(Unit)
        }
    }

}
