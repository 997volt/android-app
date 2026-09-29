package com.example.androidapp.ui.workout

import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.RestNotifier
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.WorkoutSummary
import com.example.androidapp.domain.repository.StartedSession
import com.example.androidapp.domain.repository.WorkoutRepository
import com.example.androidapp.domain.successUnit
import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class ActiveWorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val clock = TimeSource { FIXED_INSTANT }

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
        // The clock is a separate flow now (F16), so it needs its own subscriber to
        // become active — exactly as the screen does.
        backgroundScope.launch { viewModel.clock.collect {} }
    }

    /**
     * What Navigation hands a destination's SavedStateHandle for `ActiveWorkout`.
     *
     * `toRoute` reads each argument by its serial name, so the key here is the
     * route property's name rather than the route class.
     */
    private fun activeWorkoutRoute(templateId: String? = null) =
        SavedStateHandle(mapOf("templateId" to templateId))

    private fun viewModelFor(
        repository: FakeWorkoutRepository,
        notifier: FakeRestNotifier = FakeRestNotifier(),
        templateId: String? = null,
    ) = ActiveWorkoutViewModel(repository, clock, notifier, activeWorkoutRoute(templateId))

    @Test
    fun startsASessionOnEntry_soNothingCanBeLostBeforeItExists() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("s1", state.sessionId)
        // startedAt is rendered in the device's zone, so assert its shape rather
        // than a fixed string; elapsed is zone-independent and is asserted exactly.
        assertTrue("expected HH:mm, got '${state.startedAt}'", Regex("""\d{2}:\d{2}""").matches(state.startedAt))
        // 60 minutes rolls into the hour field rather than rendering as "60:00".
        // Read from the clock: the elapsed time deliberately no longer lives in the
        // screen state (F16), so a tick cannot rebuild the exercise list.
        assertEquals("1:00:00", viewModel.clock.value.elapsed)
        assertTrue(state.isEmpty)
    }

    @Test
    fun resumingAnOpenSession_keepsTheOriginalId() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository().apply {
            sessions.value = WorkoutSession(id = "existing", startedAt = Instant.parse("2026-09-28T07:30:00Z"))
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        assertEquals("existing", viewModel.uiState.value.sessionId)
    }

    @Test
    fun aFreshlyOpenedSession_asksForAReadinessNote() = runTest(dispatcher) {
        val viewModel = viewModelFor(FakeWorkoutRepository())
        observe(viewModel)
        settle()

        assertTrue(
            "a new workout should prompt (N4)",
            viewModel.uiState.value.isReadinessPromptVisible,
        )
    }

    @Test
    fun aResumedSession_doesNot_askAgain() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository().apply {
            sessions.value = WorkoutSession(
                id = "existing",
                startedAt = Instant.parse("2026-09-28T07:30:00Z"),
                readinessNote = "Slept badly",
            )
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        val state = viewModel.uiState.value
        assertFalse("a resumed session already had its chance", state.isReadinessPromptVisible)
        assertEquals("Slept badly", state.readinessNote)
    }

    @Test
    fun savingTheReadinessNote_writesIt_andDismissesThePrompt() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onSaveReadinessNote("Shoulders still sore from Monday")
        settle()

        assertEquals("Shoulders still sore from Monday", repository.lastReadinessNote)
        assertEquals(
            "Shoulders still sore from Monday",
            viewModel.uiState.value.readinessNote,
        )
        assertFalse(viewModel.uiState.value.isReadinessPromptVisible)
    }

    @Test
    fun skippingTheReadinessPrompt_writesNothing_butStillDismissesIt() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onDismissReadinessPrompt()
        settle()

        assertNull("skipping must not write an empty note", repository.lastReadinessNote)
        assertNull(viewModel.uiState.value.readinessNote)
        assertFalse(viewModel.uiState.value.isReadinessPromptVisible)
    }

    @Test
    fun aBlankReadinessNote_clearsTheNote_ratherThanStoringAnEmptyString() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onSaveReadinessNote("   ")
        settle()

        assertNull(repository.lastReadinessNote)
        assertNull(viewModel.uiState.value.readinessNote)
    }

    @Test
    fun finishingAnExercise_marksItsRowDone_andOffersAnUndo() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val id = viewModel.uiState.value.exercises.single().id

        viewModel.onFinishExercise(id)
        settle()

        val state = viewModel.uiState.value
        assertTrue("the row must render as done (N7)", state.exercises.single().isFinished)
        assertEquals(
            "the snackbar must be able to take it back",
            id,
            state.pendingFinishedExerciseId,
        )
    }

    @Test
    fun reopeningAnExercise_restoresIt() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val id = viewModel.uiState.value.exercises.single().id
        viewModel.onFinishExercise(id)
        settle()

        viewModel.onReopenExercise(id)
        settle()

        assertFalse("Reopen must undo Done (N7)", viewModel.uiState.value.exercises.single().isFinished)
    }

    @Test
    fun theUndoSnackbar_reopensTheExercise_andClearsItself() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val id = viewModel.uiState.value.exercises.single().id
        viewModel.onFinishExercise(id)
        settle()

        viewModel.onUndoFinishExercise()
        settle()

        val state = viewModel.uiState.value
        assertFalse(state.exercises.single().isFinished)
        assertNull("an undo must not fire twice", state.pendingFinishedExerciseId)
    }

    @Test
    fun dismissingTheUndo_leavesTheExerciseDone() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val id = viewModel.uiState.value.exercises.single().id
        viewModel.onFinishExercise(id)
        settle()

        viewModel.onDismissFinishUndo()
        settle()

        val state = viewModel.uiState.value
        assertTrue("letting the snackbar time out keeps the exercise done", state.exercises.single().isFinished)
        assertNull(state.pendingFinishedExerciseId)
    }

    @Test
    fun aFailedFinish_isSurfaced_notThrown() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val id = viewModel.uiState.value.exercises.single().id
        repository.failWrites = true

        viewModel.onFinishExercise(id)
        settle()

        assertNotNull("a dropped write must not be silent", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.exercises.single().isFinished)
    }

    @Test
    fun finishingWithRatings_writesThem_andMarksTheRowDone() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val id = viewModel.uiState.value.exercises.single().id

        viewModel.onFinishExercise(id, muscleFeel = 8, jointPain = 2)
        settle()

        val row = viewModel.uiState.value.exercises.single()
        assertEquals(8, row.muscleFeel)
        assertEquals(2, row.jointPain)
        assertTrue("the exercise is done either way (N8)", row.isFinished)
    }

    @Test
    fun skippingTheRatings_stillFinishesTheExercise() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        viewModel.onFinishExercise(viewModel.uiState.value.exercises.single().id)
        settle()

        val row = viewModel.uiState.value.exercises.single()
        assertTrue(row.isFinished)
        assertNull("skipping must not invent a rating", row.muscleFeel)
        assertNull(row.jointPain)
    }

    @Test
    fun aFailedRating_leavesTheExerciseOpen_andSurfacesTheError() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val id = viewModel.uiState.value.exercises.single().id
        repository.failWrites = true

        viewModel.onFinishExercise(id, muscleFeel = 8, jointPain = null)
        settle()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse(
            "the ratings are written first, so a failure leaves the exercise open",
            viewModel.uiState.value.exercises.single().isFinished,
        )
    }

    @Test
    fun addingAnExercise_showsItAsARow() = runTest(dispatcher) {        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onAddExercise("back-squat")
        settle()

        val rows = viewModel.uiState.value.exercises
        assertEquals(1, rows.size)
        assertEquals("Back Squat", rows.single().name)
        assertEquals("Quads · Barbell", rows.single().subtitle)
        assertFalse(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun aFailedWrite_isSurfacedAsState_notThrown() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
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
        val viewModel = viewModelFor(repository)
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
        val viewModel = viewModelFor(repository)
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
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onDiscard()
        settle()

        assertTrue(viewModel.closed.value)
    }

    @Test
    fun removingAnExercise_dropsTheRow() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onAddExercise("back-squat")
        settle()
        val rowId = viewModel.uiState.value.exercises.single().id

        viewModel.onRemoveExercise(rowId)
        settle()

        assertTrue(viewModel.uiState.value.exercises.isEmpty())
    }

    @Test
    fun loggingASet_storesTheSuggestion_andArmsTheRestAlert() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val notifier = FakeRestNotifier()
        val viewModel = viewModelFor(repository, notifier)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        val logged = repository.sets.value.single()
        assertEquals(DEFAULT_REPS, logged.reps)
        assertEquals(Weight.DEFAULT_GRAMS, logged.weightGrams)
        // N6: the one-tap path deliberately writes neither, so logging stays fast.
        assertNull(logged.rpe)
        assertNull(logged.note)
        assertEquals("a rest must be armed on set completion", 1, notifier.scheduled.size)
    }

    @Test
    fun editingASet_passesItsRpeAndCommentThrough() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        val logged = repository.sets.value.single()
        viewModel.onUpdateSet(logged.id, reps = 5, weightGrams = 100_000L, rpe = 7, note = "Tough")
        settle()

        val stored = repository.sets.value.single()
        assertEquals(5, stored.reps)
        assertEquals(7, stored.rpe)
        assertEquals("Tough", stored.note)
        // And the row the screen renders carries them, for the marker.
        val row = viewModel.uiState.value.exercises.single().sets.single()
        assertEquals(7, row.rpe)
        assertEquals("Tough", row.note)
    }

    @Test
    fun loggingASet_usesTheExercisesOwnRest_whenItHasOne() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository().apply { restSecondsForNextExercise = 180 }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        // N5: the exercise's own rest replaces the 90 s app default, which is
        // also what the +15 s/−15 s controls start from.
        assertEquals(180, repository.lastRestSeconds)
    }

    @Test
    fun loggingASet_fallsBackToTheAppDefault_whenNoRestIsSet() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        assertEquals(RestTimer.DEFAULT_SECONDS, repository.lastRestSeconds)
    }

    @Test
    fun anExercisesTechniqueCue_isCarriedToTheRow() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository().apply {
            techniqueNoteForNextExercise = "Brace, sit back"
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        assertEquals("Brace, sit back", viewModel.uiState.value.exercises.single().techniqueNote)
    }

    @Test
    fun theNextSet_prefillsWhatWasJustDone() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        // The suggestion for set 2 must echo set 1, not fall back to the default.
        val row = viewModel.uiState.value.exercises.single()
        assertEquals(1, row.sets.size)
        assertEquals(row.sets.single().reps, row.suggestion.reps)
        assertEquals(row.sets.single().weightGrams, row.suggestion.weightGrams)
    }

    @Test
    fun previousPerformance_prefillsTheFirstEverSet() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository().apply {
            previous = PreviousPerformance(
                listOf(
                    SetEntry(
                        id = "old",
                        sessionExerciseId = "old-ex",
                        setIndex = 0,
                        reps = 5,
                        weightGrams = 100_000,
                    ),
                ),
            )
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        val row = viewModel.uiState.value.exercises.single()
        assertEquals(5, row.suggestion.reps)
        assertEquals(100_000, row.suggestion.weightGrams)
        assertEquals("last time should be shown, not just used", 100_000L, row.lastTime?.weightGrams)
    }

    @Test
    fun deletingASet_thenUndoing_restoresIt() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        val logged = repository.sets.value.single()
        viewModel.onDeleteSet(logged.id)
        settle()
        assertTrue("the set should be gone", repository.sets.value.isEmpty())
        assertNotNull("an undo must be offered", viewModel.uiState.value.pendingUndo)

        viewModel.onUndoDelete()
        settle()

        assertEquals(1, repository.sets.value.size)
        assertEquals(logged.reps, repository.sets.value.single().reps)
        assertEquals(null, viewModel.uiState.value.pendingUndo)
    }

    @Test
    fun skippingTheRest_cancelsTheAlert() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val notifier = FakeRestNotifier()
        val viewModel = viewModelFor(repository, notifier)
        observe(viewModel)
        settle()

        viewModel.onSkipRest()
        settle()

        assertEquals(1, notifier.cancelCount)
    }

    @Test
    fun adjustingTheRest_reschedulesTheAlert() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val notifier = FakeRestNotifier()
        val viewModel = viewModelFor(repository, notifier)
        observe(viewModel)
        settle()

        viewModel.onAdjustRest(RestTimer.ADJUST_STEP_SECONDS)
        settle()

        assertEquals(1, notifier.scheduled.size)
    }

    @Test
    fun aDeletedSetsUndo_reports_whenItsExerciseIsGone() = runTest(dispatcher) {
        // ROADMAP B3's regression: delete a set, remove its exercise, then tap the
        // Undo the snackbar was still offering. It must say so, not no-op — the
        // reported bug was exactly this, silently.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val exerciseId = viewModel.uiState.value.exercises.single().id
        viewModel.onLogSet(exerciseId)
        settle()
        viewModel.onDeleteSet(repository.sets.value.single().id)
        settle()
        viewModel.onRemoveExercise(exerciseId)
        settle()

        // Tapped as a stale snackbar would, after the state it belonged to went.
        viewModel.onUndoDelete()
        settle()

        assertNotNull("a stale undo must report, not silently do nothing", viewModel.uiState.value.error)
        assertTrue("nothing may be written for a removed exercise", repository.sets.value.isEmpty())
    }

    @Test
    fun aStaleUndo_isNotOffered_becauseItsSubjectIsGone() = runTest(dispatcher) {
        // The screen dismisses an undo whose subject has gone; what the ViewModel
        // owes it is the truth to judge by. `pendingUndo` still names the set, and
        // the set no longer has an exercise in the session — that pair is what makes
        // the snackbar go (ROADMAP B3).
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val exerciseId = viewModel.uiState.value.exercises.single().id
        viewModel.onLogSet(exerciseId)
        settle()
        viewModel.onDeleteSet(repository.sets.value.single().id)
        settle()
        val pending = viewModel.uiState.value.pendingUndo
        assertNotNull(pending)

        viewModel.onRemoveExercise(exerciseId)
        settle()

        assertNull(
            "an undo whose subject has gone must not be offered",
            viewModel.uiState.value.undoableSet,
        )
        assertEquals(exerciseId, pending?.sessionExerciseId)
    }

    @Test
    fun aStaleFinishUndo_reports_whenItsExerciseIsGone() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val exerciseId = viewModel.uiState.value.exercises.single().id
        viewModel.onFinishExercise(exerciseId)
        settle()
        viewModel.onRemoveExercise(exerciseId)
        settle()

        assertNull(
            "a Done undo whose exercise has gone is not offered",
            viewModel.uiState.value.undoableFinishedExerciseId,
        )

        viewModel.onUndoFinishExercise()
        settle()

        assertNotNull("a stale finish undo must report too", viewModel.uiState.value.error)
    }

    @Test
    fun aTick_updatesTheClock_butNeverEmitsANewScreenState() = runTest(dispatcher) {
        // This is the mechanism behind F16, stated as a property: the exercise list
        // stops recomposing every second only if a tick cannot produce a new
        // ActiveWorkoutUiState. If this test ever fails, the ticker has crept back
        // into the screen state and the whole list is being rebuilt once a second.
        val tickingClock = MutableClock(FIXED_INSTANT)
        val viewModel = ActiveWorkoutViewModel(
            FakeWorkoutRepository(),
            tickingClock,
            FakeRestNotifier(),
            activeWorkoutRoute(),
        )

        val states = mutableListOf<ActiveWorkoutUiState>()
        val clocks = mutableListOf<WorkoutClock>()
        backgroundScope.launch { viewModel.uiState.collect { states += it } }
        backgroundScope.launch { viewModel.clock.collect { clocks += it } }
        settle()

        val stateEmissions = states.size
        val clockEmissions = clocks.size

        // Three seconds of workout time, then one ticker period.
        tickingClock.now = tickingClock.now.plusSeconds(3)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(
            "a tick must not rebuild the screen state (F16)",
            stateEmissions,
            states.size,
        )
        assertTrue("the clock must still have ticked", clocks.size > clockEmissions)
        assertEquals("1:00:03", viewModel.clock.value.elapsed)
    }

    private class FakeWorkoutRepository : WorkoutRepository {
        val sessions = MutableStateFlow<WorkoutSession?>(null)
        val exercises = MutableStateFlow<List<SessionExercise>>(emptyList())
        val sets = MutableStateFlow<List<SetEntry>>(emptyList())
        var previous: PreviousPerformance = PreviousPerformance(emptyList())
        var failWrites = false

        /** What the next added exercise carries, so N5's plumbing can be asserted. */
        var restSecondsForNextExercise: Int? = null
        var techniqueNoteForNextExercise: String? = null

        /** The rest length the ViewModel actually asked for, or null if never asked. */
        var lastRestSeconds: Int? = null

        /** The readiness note the ViewModel last wrote, or null if never written. */
        var lastReadinessNote: String? = null

        override fun observeActiveSession(): Flow<WorkoutSession?> = sessions

        override fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>> = exercises

        override suspend fun startOrResumeSession(templateId: String?): DataResult<StartedSession> {
            sessions.value?.let { return DataResult.Success(StartedSession(it.id, isNew = false)) }
            val created = WorkoutSession(id = "s1", startedAt = Instant.parse("2026-09-28T07:00:00Z"))
            sessions.value = created
            return DataResult.Success(StartedSession(created.id, isNew = true))
        }

        override suspend fun setReadinessNote(sessionId: String, note: String?): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            // Mirrors the repository: blank is stored as null, not as "".
            lastReadinessNote = note?.trim()?.ifEmpty { null }
            sessions.value = sessions.value?.copy(readinessNote = lastReadinessNote)
            return successUnit()
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
                restSeconds = restSecondsForNextExercise,
                techniqueNote = techniqueNoteForNextExercise,
            )
            return successUnit()
        }

        override suspend fun removeExercise(sessionExerciseId: String): DataResult<Unit> {
            exercises.value = exercises.value.filterNot { it.id == sessionExerciseId }
            return successUnit()
        }

        override suspend fun finishExercise(sessionExerciseId: String): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            exercises.value = exercises.value.map {
                if (it.id == sessionExerciseId) it.copy(finishedAt = FIXED_INSTANT) else it
            }
            return successUnit()
        }

        override suspend fun reopenExercise(sessionExerciseId: String): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            exercises.value = exercises.value.map {
                if (it.id == sessionExerciseId) it.copy(finishedAt = null) else it
            }
            return successUnit()
        }

        override suspend fun rateExercise(
            sessionExerciseId: String,
            muscleFeel: Int?,
            jointPain: Int?,
        ): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            exercises.value = exercises.value.map {
                if (it.id == sessionExerciseId) {
                    it.copy(muscleFeel = muscleFeel, jointPain = jointPain)
                } else {
                    it
                }
            }
            return successUnit()
        }

        override suspend fun finishSession(sessionId: String): DataResult<Unit> {
            sessions.value = null
            return successUnit()
        }

        override suspend fun deleteSession(sessionId: String): DataResult<Unit> {
            sessions.value = null
            exercises.value = emptyList()
            return successUnit()
        }

        override fun observeSets(sessionId: String): Flow<List<SetEntry>> = sets

        // History is exercised by its own tests; the session tests only need the
        // interface satisfied.
        override fun observeHistory(): Flow<List<WorkoutSummary>> = MutableStateFlow(emptyList())

        override fun observeSession(sessionId: String): Flow<WorkoutSession?> = MutableStateFlow(null)

        override suspend fun logSet(
            sessionExerciseId: String,
            reps: Int,
            weightGrams: Long,
            setType: SetType,
        ): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            sets.value = sets.value + SetEntry(
                id = "set-${sets.value.size}",
                sessionExerciseId = sessionExerciseId,
                setIndex = sets.value.size,
                reps = reps,
                weightGrams = weightGrams,
                setType = setType,
            )
            return successUnit()
        }

        override suspend fun updateSet(
            setId: String,
            reps: Int,
            weightGrams: Long,
            rpe: Int?,
            note: String?,
        ): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            sets.value = sets.value.map {
                if (it.id == setId) {
                    it.copy(reps = reps, weightGrams = weightGrams, rpe = rpe, note = note)
                } else {
                    it
                }
            }
            return successUnit()
        }

        override suspend fun deleteSet(setId: String): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            sets.value = sets.value.filterNot { it.id == setId }
            return successUnit()
        }

        override suspend fun previousPerformance(
            exerciseId: String,
            currentSessionId: String,
        ): DataResult<PreviousPerformance> = DataResult.Success(previous)

        override suspend fun startRest(seconds: Int): DataResult<Instant> {
            lastRestSeconds = seconds
            return DataResult.Success(FIXED_INSTANT.plusSeconds(seconds.toLong()))
        }

        override suspend fun adjustRest(deltaSeconds: Int): DataResult<Instant> =
            DataResult.Success(FIXED_INSTANT.plusSeconds(deltaSeconds.toLong()))

        override suspend fun clearRest(): DataResult<Unit> = successUnit()
    }

    /** A clock the test can move, so elapsed time can be asserted exactly. */
    private class MutableClock(var now: Instant) : TimeSource {
        override fun now(): Instant = now
    }

    /** Records what the screen asked to be alerted about, with no Android involved. */
    private class FakeRestNotifier : RestNotifier {
        val scheduled = mutableListOf<Instant>()
        var cancelCount = 0
            private set

        override fun schedule(endsAt: Instant) {
            scheduled += endsAt
        }

        override fun cancel() {
            cancelCount++
        }
    }

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-09-28T08:00:00Z")
    }
}
