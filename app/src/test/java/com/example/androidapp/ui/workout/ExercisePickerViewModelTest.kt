package com.example.androidapp.ui.workout

import com.example.androidapp.domain.model.PersonalRecords
import java.time.DayOfWeek
import com.example.androidapp.domain.repository.TemplateSetEdit
import com.example.androidapp.domain.model.TemplateSet
import androidx.lifecycle.SavedStateHandle
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.domain.repository.StartedSession
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.domain.repository.WorkoutRepository
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
/**
 * Under Robolectric rather than plain JVM: the ViewModel reads its route argument
 * out of a `SavedStateHandle`, and that path touches a real `android.os.Bundle`
 * (the same reason [com.example.androidapp.ui.exercises.ExerciseDetailViewModelTest]
 * is).
 */
@RunWith(AndroidJUnit4::class)
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

    /**
     * The picker's target is a route argument (ROADMAP N3), so the fake handle
     * carries `templateId` under the name `toRoute` reads it by.
     */
    private fun viewModelFor(
        exercises: ExerciseRepository = FakeExerciseRepository(),
        workouts: WorkoutRepository = FakeWorkoutRepository(),
        templates: TemplateRepository = FakeTemplateRepository(),
        templateId: String? = null,
    ) = ExercisePickerViewModel(
        exercises,
        workouts,
        templates,
        SavedStateHandle(mapOf("templateId" to templateId)),
    )

    /** `uiState` uses `WhileSubscribed`, so nothing flows until it is collected. */
    private fun kotlinx.coroutines.test.TestScope.observe(viewModel: ExercisePickerViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun creatingAnExercise_storesIt_andAddsItToTheSession() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository()
        val workouts = FakeWorkoutRepository()
        val viewModel = viewModelFor(exercises, workouts)
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
        val viewModel = viewModelFor(exercises, workouts)
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
        val viewModel = viewModelFor(exercises, workouts)
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
        val viewModel = viewModelFor(exercises, workouts)
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
        val viewModel = viewModelFor(exercises, workouts)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        assertNotNull("a dropped append must not be silent", viewModel.error.value)
        assertFalse(viewModel.added.value)
    }

    @Test
    fun pickingForATemplate_appendsToTheTemplate_insteadOfTheSession() = runTest(dispatcher) {
        // The same picker fills a template (N3): only the destination changes.
        val exercises = FakeExerciseRepository(listOf(seeded))
        val workouts = FakeWorkoutRepository()
        val templates = FakeTemplateRepository()
        val viewModel = viewModelFor(exercises, workouts, templates, templateId = "t1")
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        assertEquals(listOf("t1" to "back-squat"), templates.added)
        assertTrue(workouts.added.isEmpty())
        assertTrue(viewModel.added.value)
    }

    @Test
    fun aNewExercise_canBeCreatedStraightIntoATemplate() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository()
        val templates = FakeTemplateRepository()
        val viewModel = viewModelFor(exercises, templates = templates, templateId = "t1")
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onCreateExercise("Sled Push")
        advanceUntilIdle()

        assertEquals(listOf("t1" to exercises.created?.id), templates.added)
        assertTrue(viewModel.added.value)
    }

    @Test
    fun aFailedTemplateAppend_reportsIt_ratherThanPopping() = runTest(dispatcher) {
        val templates = FakeTemplateRepository().apply { failAdds = true }
        val viewModel = viewModelFor(templates = templates, templateId = "t1")
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        advanceUntilIdle()

        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.added.value)
    }

    @Test
    fun theErrorCanBeCleared_onceItHasBeenShown() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository().apply { failCreates = true }
        val viewModel = viewModelFor(exercises, FakeWorkoutRepository())
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

        override fun observeExercises(): Flow<DataResult<List<Exercise>>> =
            state.map { DataResult.Success(it) }

        override suspend fun getExercise(id: String): DataResult<Exercise?> =
            DataResult.Success(state.value.firstOrNull { it.id == id })

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

    /** Records which template an exercise landed in (ROADMAP N3). */
    private class FakeTemplateRepository : TemplateRepository {
        override suspend fun setSupersetGroup(
            templateExerciseId: String,
            group: Int?,
        ): DataResult<Unit> = DataResult.Success(Unit)
        val added = mutableListOf<Pair<String, String>>()
        var failAdds = false

        override fun observeTemplates(): Flow<List<WorkoutTemplate>> = flowOf(emptyList())
        override fun observeTemplate(templateId: String): Flow<WorkoutTemplate?> = flowOf(null)
        override fun observeExercises(templateId: String): Flow<List<TemplateExercise>> =
            flowOf(emptyList())
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

        override suspend fun createTemplate(name: String): DataResult<String> = unused()
        override suspend fun renameTemplate(templateId: String, name: String): DataResult<Unit> = unused()
        override suspend fun setWeekday(
            templateId: String,
            weekday: DayOfWeek?,
        ): DataResult<Unit> = error("these tests do not schedule a plan")

        override suspend fun deleteTemplate(templateId: String): DataResult<Unit> = unused()

        override suspend fun addExercise(templateId: String, exerciseId: String): DataResult<Unit> {
            if (failAdds) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            added += templateId to exerciseId
            return DataResult.Success(Unit)
        }

        override suspend fun removeExercise(templateExerciseId: String): DataResult<Unit> = unused()
        override suspend fun moveExercise(templateExerciseId: String, delta: Int): DataResult<Unit> =
            unused()

        private fun unused(): Nothing = error("the picker must not call this")
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
        override suspend fun startOrResumeSession(templateId: String?): DataResult<StartedSession> = unused()
        override suspend fun removeExercise(sessionExerciseId: String): DataResult<Unit> = unused()
        override suspend fun finishExercise(sessionExerciseId: String): DataResult<Unit> = unused()
        override suspend fun reopenExercise(sessionExerciseId: String): DataResult<Unit> = unused()
        override suspend fun rateExercise(
            sessionExerciseId: String,
            muscleFeel: Int?,
            jointPain: Int?,
            jointPainNote: String?,
        ): DataResult<Unit> = unused()
        override suspend fun personalRecords(
            exerciseId: String,
            excludingSessionId: String?,
        ): DataResult<PersonalRecords> = DataResult.Success(records)

        val supersetGroups = mutableMapOf<String, Int?>()

        /** What the record read answers with; empty means no records yet. */
        var records: PersonalRecords = PersonalRecords()

        override suspend fun setSupersetGroup(
            sessionExerciseId: String,
            group: Int?,
        ): DataResult<Unit> {
            supersetGroups[sessionExerciseId] = group
            return DataResult.Success(Unit)
        }

        override suspend fun finishSession(sessionId: String): DataResult<Unit> = unused()
        override suspend fun setReadinessNote(sessionId: String, note: String?): DataResult<Unit> = unused()
        override suspend fun setWorkoutNotes(sessionId: String, note: String?): DataResult<Unit> = unused()
        override suspend fun deleteSession(sessionId: String): DataResult<Unit> = unused()
        override suspend fun logSet(
            sessionExerciseId: String,
            reps: Int,
            weightGrams: Long,
            setType: SetType,
        assistanceGrams: Long,
        ): DataResult<Unit> = unused()
        override suspend fun updateSet(
            setId: String,
            reps: Int,
            weightGrams: Long,
            rpeHalves: Int?,
            note: String?,
            setType: SetType,
        assistanceGrams: Long,
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
