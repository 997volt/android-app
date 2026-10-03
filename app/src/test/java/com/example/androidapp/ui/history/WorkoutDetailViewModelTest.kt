package com.example.androidapp.ui.history

import java.time.DayOfWeek
import com.example.androidapp.domain.repository.TemplateSetEdit
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.domain.model.TemplateSet
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.PersonalRecords
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.repository.StartedSession
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.repository.WorkoutRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The history detail's own mapping (ROADMAP B5, B9).
 *
 * This screen had no test at all, which is how B5 shipped: the live workout showed an
 * assisted set as `-20 kg` and the detail showed `0 kg`, because the ViewModel built its
 * `HistorySet` without passing the column. The screen test built its state by hand, so
 * it could not see a mapping that never ran.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeWorkoutRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeWorkoutRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Collects the state for the life of the test.
     *
     * `uiState` is shared `WhileSubscribed`, so without a collector the flows never run
     * and the screen looks empty — which is not the mapping this test is about.
     */
    private fun TestScope.observe(viewModel: WorkoutDetailViewModel) {
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
    }

    @Test
    fun savingAsATemplate_passesTheName_andOffersTheNewPlan() = runTest(dispatcher) {
        // ROADMAP N31: this ViewModel's part is to hand the name over and surface what came back —
        // the copy itself is the repository's transaction, tested against a real database.
        val templates = FakeTemplateRepository(savedId = "plan-1")
        val viewModel = viewModelFor(templates)
        observe(viewModel)
        advanceUntilIdle()

        viewModel.onSaveAsTemplate("  Push day  ")
        advanceUntilIdle()

        assertEquals(listOf(SESSION_ID to "  Push day  "), templates.copied)
        assertEquals("plan-1", viewModel.savedTemplate.value)

        viewModel.onDismissSavedTemplate()
        assertNull("the offer is dismissed once taken", viewModel.savedTemplate.value)
    }

    private fun viewModelFor(templates: FakeTemplateRepository = FakeTemplateRepository()) =
        WorkoutDetailViewModel(
            workoutRepository = repository,
            templateRepository = templates,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to SESSION_ID)),
        )

    @Test
    fun everyColumnOfASet_reachesTheScreen() = runTest(dispatcher) {
        // The mapping is the whole test: a column the ViewModel forgets is a value the
        // screen silently renders as its default.
        repository.sets.value = listOf(
            SetEntry(
                id = "set1",
                sessionExerciseId = "se1",
                setIndex = 0,
                reps = 8,
                weightGrams = 0L,
                assistanceGrams = 20_000L,
                rpeHalves = 19,
                note = "hard",
            ),
        )

        val viewModel = viewModelFor()
        observe(viewModel)
        advanceUntilIdle()

        val set = viewModel.uiState.value.exercises.single().sets.single()
        assertEquals("B5: the help survives into history", 20_000L, set.assistanceGrams)
        assertEquals("B6's sibling: halves stay halves in the model", 19, set.rpeHalves)
        assertEquals(8, set.reps)
        assertEquals("hard", set.note)
    }

    @Test
    fun theRatingsAndTheLocation_reachTheScreen() = runTest(dispatcher) {
        val viewModel = viewModelFor()
        observe(viewModel)
        advanceUntilIdle()

        val exercise = viewModel.uiState.value.exercises.single()
        assertEquals(8, exercise.muscleFeel)
        assertEquals(4, exercise.jointPain)
        assertEquals("left shoulder", exercise.jointPainNote)
    }

    @Test
    fun theVolume_theScreenShows_isTheSumTheSqlComputes() = runTest(dispatcher) {
        // The getter's KDoc claims this is "one formula in two places" and that the
        // instrumented test asserts they agree. This is the formula half of that claim;
        // `WorkoutEditingTest` asserts the same number against the SQL for real rows.
        repository.sets.value = listOf(
            setEntry(id = "set1", index = 0, reps = 5, weightGrams = 100_000L),
            // An assisted set contributes nothing, exactly as bodyweight does (N15).
            setEntry(id = "set2", index = 1, reps = 8, weightGrams = 0L, assistance = 20_000L),
        )

        val viewModel = viewModelFor()
        observe(viewModel)
        advanceUntilIdle()

        assertEquals(500_000L, viewModel.uiState.value.volumeGrams)
    }

    private fun setEntry(
        id: String,
        index: Int,
        reps: Int,
        weightGrams: Long,
        assistance: Long = 0L,
    ) = SetEntry(
        id = id,
        sessionExerciseId = "se1",
        setIndex = index,
        reps = reps,
        weightGrams = weightGrams,
        assistanceGrams = assistance,
    )

    private class FakeWorkoutRepository : WorkoutRepository {
        val session = MutableStateFlow<WorkoutSession?>(
            WorkoutSession(id = SESSION_ID, startedAt = Instant.parse("2026-09-29T07:00:00Z")),
        )
        val exercises = MutableStateFlow(
            listOf(
                SessionExercise(
                    id = "se1",
                    sessionId = SESSION_ID,
                    exerciseId = "back-squat",
                    position = 0,
                    exerciseName = "Back Squat",
                    primaryMuscle = MuscleGroup.QUADS,
                    equipment = Equipment.BARBELL,
                    muscleFeel = 8,
                    jointPain = 4,
                    jointPainNote = "left shoulder",
                ),
            ),
        )
        val sets = MutableStateFlow<List<SetEntry>>(emptyList())

        override fun observeSession(sessionId: String): Flow<WorkoutSession?> = session
        override fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>> = exercises
        override fun observeSets(sessionId: String): Flow<List<SetEntry>> = sets
        override fun observeActiveSession(): Flow<WorkoutSession?> = flowOf(null)
        override fun observeHistory(): Flow<List<WorkoutSummary>> = flowOf(emptyList())
        override suspend fun startOrResumeSession(
            templateId: String?,
            slotId: String?,
        ): DataResult<StartedSession> =
            unused()

        override suspend fun repeatLastSession(): DataResult<StartedSession> = unused()

        override suspend fun addExercise(sessionId: String, exerciseId: String): DataResult<Unit> =
            unused()

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

        /** Every group write, as the ids it covered — one entry per call, which is the point. */
        val supersetGroups = mutableListOf<List<String>>()

        /** What the record read answers with; empty means no records yet. */
        var records: PersonalRecords = PersonalRecords()

        override suspend fun setSupersetGroup(
            sessionExerciseIds: List<String>,
            group: Int?,
        ): DataResult<Unit> {
            supersetGroups += sessionExerciseIds
            return DataResult.Success(Unit)
        }

        override suspend fun finishSession(sessionId: String): DataResult<Unit> = unused()
        override suspend fun setReadinessNote(sessionId: String, note: String?): DataResult<Unit> =
            unused()

        override suspend fun setWorkoutNotes(sessionId: String, note: String?): DataResult<Unit> =
            unused()

        override suspend fun deleteSession(sessionId: String): DataResult<Unit> = unused()
        override suspend fun logSet(
            sessionExerciseId: String,
            reps: Int,
            weightGrams: Long,
            setType: com.example.androidapp.domain.model.SetType,
            assistanceGrams: Long,
        ): DataResult<Unit> = unused()

        override suspend fun updateSet(
            setId: String,
            reps: Int,
            weightGrams: Long,
            rpeHalves: Int?,
            note: String?,
            setType: com.example.androidapp.domain.model.SetType,
            assistanceGrams: Long,
        ): DataResult<Unit> = unused()

        override suspend fun deleteSet(setId: String): DataResult<Unit> = unused()
        override suspend fun previousPerformance(
            exerciseId: String,
            currentSessionId: String,
        ): DataResult<PreviousPerformance> =
            DataResult.Success(PreviousPerformance(emptyList()))

        override suspend fun startRest(seconds: Int) = unused()
        override suspend fun adjustRest(deltaSeconds: Int) = unused()
        override suspend fun clearRest(): DataResult<Unit> = unused()

        private fun unused(): Nothing = error("this test does not write")
    }

    private companion object {
        const val SESSION_ID = "s1"
    }
}

/**
 * A plan store that records what a copy asked for (ROADMAP N31).
 *
 * Top level rather than nested in the test class: it is a helper, and the tests only need to see what
 * was written and what came back.
 */
private class FakeTemplateRepository(
    private val savedId: String = "t1",
) : TemplateRepository {

    /** Every copy asked for, as the session it came from and the name it was given. */
    val copied = mutableListOf<Pair<String, String>>()

    override suspend fun createTemplateFromSession(
        sessionId: String,
        name: String,
    ): DataResult<String> {
        copied += sessionId to name
        return DataResult.Success(savedId)
    }

    override suspend fun createTemplate(name: String): DataResult<String> = unused()
    override suspend fun renameTemplate(templateId: String, name: String): DataResult<Unit> = unused()
    override suspend fun deleteTemplate(templateId: String): DataResult<Unit> = unused()
    override suspend fun setWeekday(templateId: String, weekday: DayOfWeek?): DataResult<Unit> = unused()
    override suspend fun setSupersetGroup(
        templateExerciseIds: List<String>,
        group: Int?,
    ): DataResult<Unit> = unused()

    override suspend fun addExercise(templateId: String, exerciseId: String): DataResult<Unit> = unused()
    override suspend fun removeExercise(templateExerciseId: String): DataResult<Unit> = unused()

    override suspend fun prependSets(
        templateExerciseId: String,
        edits: List<TemplateSetEdit>,
    ): DataResult<Unit> = unused()

    override suspend fun addSet(templateExerciseId: String, edit: TemplateSetEdit): DataResult<Unit> =
        unused()

    override suspend fun updateSet(templateSetId: String, edit: TemplateSetEdit): DataResult<Unit> =
        unused()

    override suspend fun removeSet(templateSetId: String): DataResult<Unit> = unused()
    override suspend fun duplicateSets(templateExerciseId: String): DataResult<Unit> = unused()
    override suspend fun moveExercise(templateExerciseId: String, delta: Int): DataResult<Unit> =
        unused()
    override suspend fun setExercisePlan(
        templateExerciseId: String,
        restSeconds: Int?,
        techniqueNote: String?,
    ): DataResult<Unit> = unused()

    override fun observeTemplates(): Flow<List<WorkoutTemplate>> = flowOf(emptyList())
    override fun observeTemplate(templateId: String): Flow<WorkoutTemplate?> = flowOf(null)
    override fun observeExercises(templateId: String): Flow<List<TemplateExercise>> = flowOf(emptyList())
    override fun observeSets(templateId: String): Flow<List<TemplateSet>> = flowOf(emptyList())

    private fun unused(): Nothing = error("this fake does not write")
}
