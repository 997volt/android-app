package com.example.androidapp.ui.workout

import com.example.androidapp.domain.model.PersonalRecords
import kotlinx.coroutines.flow.asStateFlow
import com.example.androidapp.domain.repository.SettingsRepository
import java.time.DayOfWeek
import com.example.androidapp.domain.repository.TemplateSetEdit
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.domain.model.TemplateSet
import com.example.androidapp.domain.model.TemplateExercise
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
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
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
        templates: FakeTemplateRepository = FakeTemplateRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = ActiveWorkoutViewModel(
        repository,
        clock,
        notifier,
        templates,
        activeWorkoutRoute(templateId),
        settings,
    )

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
    fun theReadinessNote_arrivesAsOneEmission_ratherThanTwo() = runTest(dispatcher) {
        // The same behaviour as the test above, asserted as a *sequence* instead of two
        // polled snapshots — which is what Turbine is for, and what `.value` cannot
        // express. The distinction matters here: `uiState` is a `combine` of the session,
        // the prompt flag and the error channel, and clearing the prompt while writing
        // the note would show up as a state that carries one without the other.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.uiState.test {
            // The loaded state: nothing written yet, and the prompt still up.
            val before = awaitItem()
            assertThat(before.isReadinessPromptVisible).isTrue()
            assertThat(before.readinessNote).isNull()

            viewModel.onSaveReadinessNote("Shoulders still sore from Monday")
            settle()

            // `StateFlow` conflates, so this yields the settled state rather than
            // pretending to know how many emissions happened on the way. What is being
            // asserted is the pairing: the note is present *in the same state* that has
            // dismissed the prompt, so no observer can see one without the other.
            val after = expectMostRecentItem()
            assertThat(after.readinessNote).isEqualTo("Shoulders still sore from Monday")
            assertThat(after.isReadinessPromptVisible).isFalse()
            assertThat(repository.lastReadinessNote).isEqualTo("Shoulders still sore from Monday")

            cancelAndIgnoreRemainingEvents()
        }
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
    fun ratingAnExercise_doesNotFinishIt() = runTest(dispatcher) {
        // ROADMAP N10: the same write the Done prompt makes, on its own.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val id = viewModel.uiState.value.exercises.single().id

        viewModel.onRateExercise(id, muscleFeel = 7, jointPain = 3, jointPainNote = "left knee")
        settle()

        val row = viewModel.uiState.value.exercises.single()
        assertEquals(7, row.muscleFeel)
        assertEquals(3, row.jointPain)
        assertEquals("left knee", row.jointPainNote)
        assertFalse("rating an exercise must not close it", row.isFinished)
        assertNull(
            "and it is not a Done, so there is no undo to offer",
            viewModel.uiState.value.pendingFinishedExerciseId,
        )
    }

    @Test
    fun aFailedRatingOutsideThePrompt_isSurfaced() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val id = viewModel.uiState.value.exercises.single().id
        repository.failWrites = true

        viewModel.onRateExercise(id, muscleFeel = 7, jointPain = null, jointPainNote = null)
        settle()

        assertNotNull(viewModel.uiState.value.error)
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
        val notifier = FakeRestNotifier()
        val viewModel = viewModelFor(repository, notifier)
        observe(viewModel)
        settle()

        viewModel.onFinish()
        settle()

        assertTrue("finishing publishes the review (N20)", viewModel.summary.value != null)
        assertFalse("and does not leave yet — the review is what the user reads", viewModel.closed.value)

        viewModel.onDismissSummary()
        settle()

        assertTrue("the screen should leave after the review is dismissed", viewModel.closed.value)
        assertEquals(null, viewModel.uiState.value.sessionId)
        // Asserted here because the code path that does it was rewritten once and the
        // cancel was lost: a finished workout must not leave a rest alarm armed.
        assertEquals("finishing must cancel any rest alert", 1, notifier.cancelCount)
    }

    @Test
    fun finishingWithAComment_alsoCancelsTheRestAlert() = runTest(dispatcher) {
        // The comment is written first, so this is a different path to the same close.
        val repository = FakeWorkoutRepository()
        val notifier = FakeRestNotifier()
        val viewModel = viewModelFor(repository, notifier)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        viewModel.onFinish(note = "Good session")
        settle()
        viewModel.onDismissSummary()
        settle()

        assertEquals(1, notifier.cancelCount)
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
        assertNull(logged.rpeHalves)
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
        viewModel.onUpdateSet(
            logged.id,
            reps = 5,
            weightGrams = 100_000L,
            rpeHalves = 7,
            note = "Tough",
            setType = SetType.WARMUP,
        )
        settle()

        val stored = repository.sets.value.single()
        assertEquals(5, stored.reps)
        assertEquals(7, stored.rpeHalves)
        assertEquals("Tough", stored.note)
        // And the row the screen renders carries them, for the marker.
        val row = viewModel.uiState.value.exercises.single().sets.single()
        assertEquals(7, row.rpeHalves)
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
    fun previousPerformance_isProgressed_notRepeated() = runTest(dispatcher) {
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
        assertEquals("N22: one more rep at the same load", 6, row.suggestion.reps)
        assertEquals(100_000, row.suggestion.weightGrams)
        assertEquals("last time is still shown as itself, not as the target", 100_000L, row.lastTime?.weightGrams)
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
    fun finishingWithAComment_writesIt_thenClosesTheWorkout() = runTest(dispatcher) {
        // ROADMAP N11: the moment of finishing is when the reason is remembered.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        viewModel.onFinish(note = "Slept badly, but the squats moved")
        settle()

        assertEquals("Slept badly, but the squats moved", repository.lastWorkoutNotes)
        assertEquals(
            "the comment is the review's, so it is readable there (N20)",
            "Slept badly, but the squats moved",
            viewModel.summary.value?.note,
        )

        viewModel.onDismissSummary()
        settle()

        assertTrue("finishing still finishes", viewModel.closed.value)
    }

    @Test
    fun skippingTheComment_finishesWithoutWritingOne() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        viewModel.onFinish()
        settle()

        assertNull("skipping must not invent a comment", repository.lastWorkoutNotes)
        assertNotNull(viewModel.summary.value)

        viewModel.onDismissSummary()
        settle()

        assertTrue(viewModel.closed.value)
    }

    @Test
    fun aCommentThatCannotBeWritten_stopsBeforeTheWorkoutIsLost() = runTest(dispatcher) {
        // A failed comment write must not silently close the workout: the failure is
        // reported and the workout is still open to finish again.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()
        repository.failWrites = true

        viewModel.onFinish(note = "Half a thought")
        settle()

        assertNotNull(viewModel.uiState.value.error)
        assertFalse("the workout must still be open", viewModel.closed.value)
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
            FakeTemplateRepository(),
            activeWorkoutRoute(),
            FakeSettingsRepository(),
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

        /** What the record read answers with; empty means no records yet. */
        var records: PersonalRecords = PersonalRecords()


        /** The readiness note the ViewModel last wrote, or null if never written. */
        var lastReadinessNote: String? = null

        /** The workout comment the ViewModel last wrote, or null if never written. */
        var lastWorkoutNotes: String? = null

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
            return DataResult.Success(Unit)
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
            return DataResult.Success(Unit)
        }

        override suspend fun removeExercise(sessionExerciseId: String): DataResult<Unit> {
            exercises.value = exercises.value.filterNot { it.id == sessionExerciseId }
            return DataResult.Success(Unit)
        }

        override suspend fun finishExercise(sessionExerciseId: String): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            exercises.value = exercises.value.map {
                if (it.id == sessionExerciseId) it.copy(finishedAt = FIXED_INSTANT) else it
            }
            return DataResult.Success(Unit)
        }

        override suspend fun reopenExercise(sessionExerciseId: String): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            exercises.value = exercises.value.map {
                if (it.id == sessionExerciseId) it.copy(finishedAt = null) else it
            }
            return DataResult.Success(Unit)
        }

        override suspend fun rateExercise(
            sessionExerciseId: String,
            muscleFeel: Int?,
            jointPain: Int?,
            jointPainNote: String?,
        ): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            exercises.value = exercises.value.map {
                if (it.id == sessionExerciseId) {
                    it.copy(
                        muscleFeel = muscleFeel,
                        jointPain = jointPain,
                        jointPainNote = jointPainNote,
                    )
                } else {
                    it
                }
            }
            return DataResult.Success(Unit)
        }

        override suspend fun setWorkoutNotes(sessionId: String, note: String?): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            lastWorkoutNotes = note
            sessions.value = sessions.value?.copy(notes = note)
            return DataResult.Success(Unit)
        }

        override suspend fun personalRecords(
            exerciseId: String,
            excludingSessionId: String?,
        ): DataResult<PersonalRecords> = DataResult.Success(records)

        override suspend fun finishSession(sessionId: String): DataResult<Unit> {
            sessions.value = null
            return DataResult.Success(Unit)
        }

        override suspend fun deleteSession(sessionId: String): DataResult<Unit> {
            sessions.value = null
            exercises.value = emptyList()
            return DataResult.Success(Unit)
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
        assistanceGrams: Long,
        ): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            sets.value = sets.value + SetEntry(
                id = "set-${sets.value.size}",
                sessionExerciseId = sessionExerciseId,
                setIndex = sets.value.size,
                reps = reps,
                weightGrams = weightGrams,
                setType = setType,
                // Without this the fake silently dropped the help, which is how B7's
                // fix could have gone unnoticed by its own test.
                assistanceGrams = assistanceGrams,
            )
            return DataResult.Success(Unit)
        }

        override suspend fun updateSet(
            setId: String,
            reps: Int,
            weightGrams: Long,
            rpeHalves: Int?,
            note: String?,
            setType: SetType,
        assistanceGrams: Long,
        ): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            sets.value = sets.value.map {
                if (it.id == setId) {
                    it.copy(
                        reps = reps,
                        weightGrams = weightGrams,
                        rpeHalves = rpeHalves,
                        note = note,
                        assistanceGrams = assistanceGrams,
                    )
                } else {
                    it
                }
            }
            return DataResult.Success(Unit)
        }

        override suspend fun deleteSet(setId: String): DataResult<Unit> {
            if (failWrites) return DataResult.Failure(DataError.Storage(IOException("disk full")))
            sets.value = sets.value.filterNot { it.id == setId }
            return DataResult.Success(Unit)
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

        override suspend fun clearRest(): DataResult<Unit> = DataResult.Success(Unit)
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

    @Test
    fun startingFromAPlan_prefillsTheFirstSetsTargets() = runTest(dispatcher) {
        // ROADMAP N14: "Start from template prefills the planned sets as targets."
        // Nothing is logged as a set — the plan is a target, and a logged set is a
        // separate row that is expected to differ.
        val repository = FakeWorkoutRepository()
        val templates = FakeTemplateRepository(
            planned = listOf(
                plannedExercise(
                    position = 0,
                    sets = listOf(plannedSet(index = 0, reps = 3, weightGrams = 140_000L)),
                ),
            ),
        )
        val viewModel = viewModelFor(repository, templateId = "t1", templates = templates)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        val suggestion = viewModel.uiState.value.exercises.single().suggestion
        assertEquals("the plan's reps, not the default", 3, suggestion.reps)
        assertEquals(140_000L, suggestion.weightGrams)
        assertTrue(
            "a plan prefills targets; it does not log sets",
            viewModel.uiState.value.exercises.single().sets.isEmpty(),
        )
    }

    @Test
    fun aPlanWithNoTargetForTheNextSet_leavesTheOlderRuleAlone() = runTest(dispatcher) {
        // The plan is silent about set two, so the set just logged decides (P1.3).
        val repository = FakeWorkoutRepository()
        val templates = FakeTemplateRepository(
            planned = listOf(
                plannedExercise(
                    position = 0,
                    sets = listOf(plannedSet(index = 0, reps = 3, weightGrams = 140_000L)),
                ),
            ),
        )
        val viewModel = viewModelFor(repository, templateId = "t1", templates = templates)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()
        // The user adjusted the first set away from the plan, which is expected: a
        // logged set is its own row and nothing verifies it against the plan.
        val logged = viewModel.uiState.value.exercises.single().sets.single()
        viewModel.onUpdateSet(
            logged.id,
            reps = 5,
            weightGrams = 100_000L,
            rpeHalves = null,
            note = null,
            setType = SetType.NORMAL,
        )
        settle()

        val suggestion = viewModel.uiState.value.exercises.single().suggestion
        assertEquals("the set just logged decides set two", 5, suggestion.reps)
        assertEquals(100_000L, suggestion.weightGrams)
    }

    /** One exercise of a plan, at a position in the session. */
    private fun plannedExercise(position: Int, sets: List<TemplateSet>) = TemplateExercise(
        id = "te-$position",
        templateId = "t1",
        exerciseId = "back-squat",
        position = position,
        exerciseName = "Back Squat",
        primaryMuscle = MuscleGroup.QUADS,
        equipment = Equipment.BARBELL,
        sets = sets,
    )

    private fun plannedSet(index: Int, reps: Int, weightGrams: Long) = TemplateSet(
        id = "ts-$index",
        templateExerciseId = "te-0",
        setIndex = index,
        role = SetType.NORMAL,
        targetWeightGrams = weightGrams,
        targetRepsMax = reps,
    )

    /** Reads only: this test never writes a plan, and the plan's reads are enough. */
    private class FakeTemplateRepository(
        private val planned: List<TemplateExercise> = emptyList(),
    ) : TemplateRepository {
        override fun observeTemplates(): Flow<List<WorkoutTemplate>> = flowOf(emptyList())
        override fun observeTemplate(templateId: String): Flow<WorkoutTemplate?> = flowOf(null)
        override fun observeExercises(templateId: String): Flow<List<TemplateExercise>> =
            flowOf(planned)

        override fun observeSets(templateId: String): Flow<List<TemplateSet>> = flowOf(emptyList())
        override suspend fun createTemplate(name: String): DataResult<String> = notUsed()
        override suspend fun renameTemplate(templateId: String, name: String): DataResult<Unit> =
            notUsed()

        override suspend fun setWeekday(
            templateId: String,
            weekday: DayOfWeek?,
        ): DataResult<Unit> = error("these tests do not schedule a plan")

        override suspend fun deleteTemplate(templateId: String): DataResult<Unit> = notUsed()
        override suspend fun addExercise(templateId: String, exerciseId: String): DataResult<Unit> =
            notUsed()

        override suspend fun removeExercise(templateExerciseId: String): DataResult<Unit> =
            notUsed()

        override suspend fun moveExercise(templateExerciseId: String, delta: Int): DataResult<Unit> =
            notUsed()

        override suspend fun addSet(
            templateExerciseId: String,
            edit: TemplateSetEdit,
        ): DataResult<Unit> = notUsed()

        override suspend fun updateSet(
            templateSetId: String,
            edit: TemplateSetEdit,
        ): DataResult<Unit> = notUsed()

        override suspend fun removeSet(templateSetId: String): DataResult<Unit> = notUsed()
        override suspend fun duplicateSets(templateExerciseId: String): DataResult<Unit> = notUsed()
        override suspend fun setExercisePlan(
            templateExerciseId: String,
            restSeconds: Int?,
            techniqueNote: String?,
        ): DataResult<Unit> = notUsed()

        private fun notUsed(): Nothing = error("this test does not write a plan")
    }

    @Test
    fun oneTapLog_writesTheAssistanceTheButtonShowed() = runTest(dispatcher) {
        // ROADMAP B7: the button reads "Log set · -20 kg × 8", so the set it writes has
        // to be that set. D3 chose "the button does what it says" over saying less.
        val repository = FakeWorkoutRepository()
        val templates = FakeTemplateRepository(
            planned = listOf(
                plannedExercise(
                    position = 0,
                    sets = listOf(
                        TemplateSet(
                            id = "ts-0",
                            templateExerciseId = "te-0",
                            setIndex = 0,
                            targetRepsMax = 8,
                            targetAssistanceGrams = 20_000L,
                        ),
                    ),
                ),
            ),
        )
        val viewModel = viewModelFor(repository, templateId = "t1", templates = templates)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        val shown = viewModel.uiState.value.exercises.single().suggestion
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        val written = repository.sets.value.single()
        assertEquals("the help the button showed", 20_000L, written.assistanceGrams)
        assertEquals(shown.reps, written.reps)
        assertEquals(shown.weightGrams, written.weightGrams)
    }

    @Test
    fun undoingADeletedAssistedSet_bringsTheHelpBack() = runTest(dispatcher) {
        // The same omission as B7, one call site later.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        val exerciseId = viewModel.uiState.value.exercises.single().id
        viewModel.onLogSet(exerciseId)
        settle()
        val logged = repository.sets.value.single()
        viewModel.onUpdateSet(
            logged.id,
            reps = logged.reps,
            weightGrams = 0L,
            assistanceGrams = 20_000L,
        )
        settle()

        viewModel.onDeleteSet(repository.sets.value.single().id)
        settle()
        viewModel.onUndoDelete()
        settle()

        assertEquals(
            "an undone set comes back as it was",
            20_000L,
            repository.sets.value.single().assistanceGrams,
        )
    }

    @Test
    fun theReview_countsTheWorkThatWasJustDone() = runTest(dispatcher) {
        // A device found this, and only a device could: the review was built *after*
        // finishing, and a stored session is no longer the live one — so the totals read
        // "Sets 0 · reps 0 · 0 kg" and the set that had just been logged was reported as
        // not performed. The fake clears the active session exactly as the real repository
        // does, so this test holds the fix.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        viewModel.onFinish()
        settle()

        val summary = viewModel.summary.value
        assertNotNull(summary)
        assertEquals("the set that was logged", 1, summary!!.totalSets)
        assertEquals(8, summary.totalReps)
        assertEquals("20 kg × 8", 160_000L, summary.totalVolumeGrams)
        assertEquals(
            "and it is not reported as never performed",
            1,
            summary.comparisons.single().performedSets,
        )
    }
    @Test
    fun anExerciseWithoutARest_usesTheConfiguredDefault() = runTest(dispatcher) {
        // ROADMAP N21: the default was a hardcoded 90 seconds with no way to change it, so
        // the setting has to reach a workout that is already open.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository, settings = FakeSettingsRepository(initialRestSeconds = 45))
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        assertEquals("the configured default, not the constant", 45, repository.lastRestSeconds)
    }

    @Test
    fun anExerciseWithItsOwnRest_stillWins() = runTest(dispatcher) {
        // N5's rule is unchanged by N21: a rest the exercise prescribes is not overridden by
        // the app-wide preference.
        val repository = FakeWorkoutRepository().apply { restSecondsForNextExercise = 180 }
        val viewModel = viewModelFor(repository, settings = FakeSettingsRepository(initialRestSeconds = 45))
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        assertEquals(180, repository.lastRestSeconds)
    }

    @Test
    fun aDefaultChangedMidWorkout_isPickedUp() = runTest(dispatcher) {
        // The store is a Flow for exactly this reason: the user can change it in settings and
        // come back to a workout that is still open.
        val repository = FakeWorkoutRepository()
        val settings = FakeSettingsRepository(initialRestSeconds = 90)
        val viewModel = viewModelFor(repository, settings = settings)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        settings.setDefaultRestSeconds(30)
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()

        assertEquals(30, repository.lastRestSeconds)
    }



    @Test
    fun loggingASetThatBeatsHistory_raisesTheRecord() = runTest(dispatcher) {
        // ROADMAP N23: the app has the numbers to say it the second it is true, and a record
        // noticed a week later in a list is a record nobody feels.
        val repository = FakeWorkoutRepository().apply {
            records = PersonalRecords(mapOf(8 to 17_500L))
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        // The prefill is 20 kg × 8, which beats the 17.5 kg recorded at eight reps.
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()
        val moment = viewModel.personalRecord.value
        assertNotNull(moment)
        assertEquals(8, moment!!.reps)
        assertEquals(20_000L, moment.weightGrams)
        assertEquals("it says what was beaten, not only what was done", 17_500L, moment.previousBestGrams)
    }
    @Test
    fun matchingTheBest_raisesNothing() = runTest(dispatcher) {
        // Celebrating a repeat devalues the word.
        val repository = FakeWorkoutRepository().apply {
            records = PersonalRecords(mapOf(8 to 20_000L))
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()
        assertNull(viewModel.personalRecord.value)
    }
    @Test
    fun theRecordIsClearedByTheNextSet() = runTest(dispatcher) {
        // It is news for the moment between sets, not a banner to dismiss (N23).
        val repository = FakeWorkoutRepository().apply {
            records = PersonalRecords(mapOf(8 to 17_500L))
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()
        assertNotNull(viewModel.personalRecord.value)
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()
        assertNull("the second set repeats the first, and repeats are not records", viewModel.personalRecord.value)
    }

}

/**
 * The settings store, hand-written like the others (there is no mocking framework here).
 *
 * It holds one value and hands it out as a `Flow`, which is what the ViewModel reads — so a
 * test can change the default rest *while a workout is open* and see it take effect.
 */
private class FakeSettingsRepository(
    initialRestSeconds: Int = RestTimer.DEFAULT_SECONDS,
) : SettingsRepository {

    private val rest = MutableStateFlow(initialRestSeconds)

    override fun observeDefaultRestSeconds(): Flow<Int> = rest.asStateFlow()

    override suspend fun setDefaultRestSeconds(seconds: Int): DataResult<Unit> {
        if (seconds !in SettingsRepository.VALID_REST_SECONDS) {
            return DataResult.Failure(DataError.Invalid("out of range"))
        }
        rest.value = seconds
        return DataResult.Success(Unit)
    }


}
