package com.example.androidapp.ui.workout

import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.domain.model.ProgressionReason
import com.example.androidapp.domain.model.PersonalRecords
import com.example.androidapp.domain.model.AdherenceReport
import com.example.androidapp.domain.model.PendingOccurrence
import com.example.androidapp.domain.model.ProgramRun
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.SlotPrescription
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.domain.repository.ProgramRepository
import com.example.androidapp.domain.repository.SlotSetEdit
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
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.SlotSet
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
    private fun activeWorkoutRoute(
        templateId: String? = null,
        repeatLast: Boolean = false,
        slotId: String? = null,
    ) = SavedStateHandle(
        mapOf("templateId" to templateId, "repeatLast" to repeatLast, "slotId" to slotId),
    )

    private fun viewModelFor(
        repository: FakeWorkoutRepository,
        templateId: String? = null,
        templates: FakeTemplateRepository = FakeTemplateRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        repeatLast: Boolean = false,
        programs: FakeProgramRepository = FakeProgramRepository(),
        slotId: String? = null,
    ) = ActiveWorkoutViewModel(
        repository,
        clock,
        templates,
        programs,
        activeWorkoutRoute(templateId, repeatLast, slotId),
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

        viewModel.uiState.value.pendingFinishedExerciseId?.let(viewModel::onReopenExercise)
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
        val viewModel = viewModelFor(repository)
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
    }

    @Test
    fun finishingWithAComment_writesTheNote_beforeClosing() = runTest(dispatcher) {
        // ROADMAP B35, the third: its name described cancelling an alert that no longer exists, and it
        // asserted nothing. What it exercised — a comment written on the way to finishing — is real
        // and was covered by nothing else.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
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

        assertEquals("the comment reaches the session", "Good session", repository.lastWorkoutNotes)
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
        val viewModel = viewModelFor(repository)
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
        // N33 changed what this asserts, on purpose: the prefill is last time *unchanged*, and the step
        // the app proposes is offered beside it. Before, this number was what one tap logged, so a
        // lifter who progresses by hand had to undo the app's arithmetic on every first set.
        assertEquals("the same reps as last time", 5, row.suggestion.reps)
        assertEquals(100_000, row.suggestion.weightGrams)
        assertEquals("and one more rep is offered", 6, row.suggestion.offer?.reps)
        assertEquals(100_000L, row.suggestion.offer?.weightGrams)
        assertEquals(ProgressionReason.MORE_REPS, row.suggestion.offer?.reason)
        assertEquals("last time is still shown as itself, not as the target", 100_000L, row.lastTime?.weightGrams)
    }

    @Test
    fun acceptingTheOffer_makesItThePrefill_andonlyThen() = runTest(dispatcher) {
        // ROADMAP N33, the half a user can act on: the proposal is shown, and taking it is what puts its
        // numbers where one tap will log them.
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

        val offered = viewModel.uiState.value.exercises.single()
        assertEquals("the prefill is what was done", 5, offered.suggestion.reps)
        assertEquals("and one more rep is offered", 6, offered.suggestion.offer?.reps)

        viewModel.onAcceptOffer(offered.id)
        settle()

        val accepted = viewModel.uiState.value.exercises.single()
        assertEquals("taking it makes the proposal the prefill", 6, accepted.suggestion.reps)
        assertEquals(100_000L, accepted.suggestion.weightGrams)
        assertNull("and it is no longer a proposal", accepted.suggestion.offer)
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
    fun skippingTheRest_clearsIt() = runTest(dispatcher) {
        // ROADMAP B35: this test asserted nothing once the alert it was written for was deleted. What
        // remains is the clearing, which is the part a user can see — and it had no coverage.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onSkipRest()
        settle()

        assertTrue("the rest is cleared", repository.restCleared)
    }

    @Test
    fun adjustingTheRest_movesTheEndInstant() = runTest(dispatcher) {
        // ROADMAP B35, the second of the two: the adjustment reaches the repository with the step the
        // button sends, which is the contract the screen depends on.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()

        viewModel.onAdjustRest(RestTimer.ADJUST_STEP_SECONDS)
        settle()

        assertEquals(RestTimer.ADJUST_STEP_SECONDS, repository.lastRestAdjustedBy)
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

        viewModel.uiState.value.pendingFinishedExerciseId?.let(viewModel::onReopenExercise)
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
            FakeTemplateRepository(),
            FakeProgramRepository(),
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

        /** What the last finished workout held, in order (ROADMAP N29). */
        var repeatedExerciseIds: List<String> = emptyList()
        val exercises = MutableStateFlow<List<SessionExercise>>(emptyList())
        val sets = MutableStateFlow<List<SetEntry>>(emptyList())
        var previous: PreviousPerformance = PreviousPerformance(emptyList())
        var failWrites = false

        /** What the next added exercise carries, so N5's plumbing can be asserted. */
        var restSecondsForNextExercise: Int? = null
        var techniqueNoteForNextExercise: String? = null

        /** The rest length the ViewModel actually asked for, or null if never asked. */
        var lastRestSeconds: Int? = null

        /** The rest actions, which used to be recorded nowhere (ROADMAP B35). */
        var lastRestAdjustedBy: Int? = null
        var restCleared = false

        /** What the record read answers with; empty means no records yet. */
        var records: PersonalRecords = PersonalRecords()


        /** The readiness note the ViewModel last wrote, or null if never written. */
        var lastReadinessNote: String? = null

        /** The workout comment the ViewModel last wrote, or null if never written. */
        var lastWorkoutNotes: String? = null

        override fun observeActiveSession(): Flow<WorkoutSession?> = sessions

        override fun observeSessionExercises(sessionId: String): Flow<List<SessionExercise>> = exercises

        override suspend fun startOrResumeSession(
            templateId: String?,
            slotId: String?,
        ): DataResult<StartedSession> {
            sessions.value?.let { return DataResult.Success(StartedSession(it.id, isNew = false)) }
            val created = WorkoutSession(id = "s1", startedAt = Instant.parse("2026-09-28T07:00:00Z"))
            sessions.value = created
            return DataResult.Success(StartedSession(created.id, isNew = true))
        }

        /** ROADMAP N29: the same shape as starting, since a repeat is a start with exercises. */
        override suspend fun repeatLastSession(): DataResult<StartedSession> {
            sessions.value?.let { return DataResult.Success(StartedSession(it.id, isNew = false)) }
            val created = WorkoutSession(id = "s1", startedAt = Instant.parse("2026-09-28T07:00:00Z"))
            sessions.value = created
            repeatedExerciseIds.forEach { addExercise(created.id, it) }
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

        /** Every group write, as the ids it covered — one entry per call, which is the point. */
        val supersetGroups = mutableListOf<List<String>>()

        override suspend fun setSupersetGroup(
            sessionExerciseIds: List<String>,
            group: Int?,
        ): DataResult<Unit> {
            supersetGroups += sessionExerciseIds
            exercises.value = exercises.value.map {
                if (it.id in sessionExerciseIds) it.copy(supersetGroup = group) else it
            }
            return DataResult.Success(Unit)
        }

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

        override suspend fun adjustRest(deltaSeconds: Int): DataResult<Instant> {
            lastRestAdjustedBy = deltaSeconds
            return DataResult.Success(FIXED_INSTANT.plusSeconds(deltaSeconds.toLong()))
        }

        override suspend fun clearRest(): DataResult<Unit> {
            restCleared = true
            return DataResult.Success(Unit)
        }
    }

    /** A clock the test can move, so elapsed time can be asserted exactly. */
    private class MutableClock(var now: Instant) : TimeSource {
        override fun now(): Instant = now
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
    fun aSlotsPrescription_prefillsOverTheTemplatesTarget() = runTest(dispatcher) {
        // ROADMAP P3.8: the slot's prescription wins where it speaks, so two slots pointing at one
        // template train it differently.
        val repository = FakeWorkoutRepository()
        val templates = FakeTemplateRepository(
            planned = listOf(
                plannedExercise(
                    position = 0,
                    sets = listOf(plannedSet(index = 0, reps = 5, weightGrams = 90_000L)),
                ),
            ),
        )
        val programs = FakeProgramRepository().apply {
            prescriptions = listOf(
                SlotPrescription(
                    exerciseId = "back-squat",
                    sets = listOf(
                        SlotSet(id = "ps0", setIndex = 0, targetWeightGrams = 110_000L, targetRepsMin = 2),
                    ),
                ),
            )
        }
        val viewModel = viewModelFor(
            repository,
            templateId = "t1",
            templates = templates,
            programs = programs,
            slotId = "slot-1",
        )
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        val suggestion = viewModel.uiState.value.exercises.single().suggestion
        assertEquals("the slot's reps, not the template's", 2, suggestion.reps)
        assertEquals(110_000L, suggestion.weightGrams)
    }

    @Test
    fun aSlotsPercentage_prefillsTheWeightDerivedFromTheEstimate() = runTest(dispatcher) {
        // The one target a template's planned set cannot carry (P3.8): 85% of a 100 kg estimate.
        val repository = FakeWorkoutRepository()
        val programs = FakeProgramRepository().apply {
            oneRepMax = 100_000L
            prescriptions = listOf(
                SlotPrescription(
                    exerciseId = "back-squat",
                    sets = listOf(
                        SlotSet(id = "ps0", setIndex = 0, targetPercentOf1Rm = 85, targetRepsMin = 3),
                    ),
                ),
            )
        }
        val viewModel = viewModelFor(
            repository,
            templateId = "t1",
            programs = programs,
            slotId = "slot-1",
        )
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        val suggestion = viewModel.uiState.value.exercises.single().suggestion
        assertEquals(3, suggestion.reps)
        assertEquals(85_000L, suggestion.weightGrams)
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
        override suspend fun setSupersetGroup(
            templateExerciseIds: List<String>,
            group: Int?,
        ): DataResult<Unit> = DataResult.Success(Unit)
        override fun observeTemplates(): Flow<List<WorkoutTemplate>> = flowOf(emptyList())
        override fun observeTemplate(templateId: String): Flow<WorkoutTemplate?> = flowOf(null)
        override fun observeExercises(templateId: String): Flow<List<TemplateExercise>> =
            flowOf(planned)

        override fun observeSets(templateId: String): Flow<List<TemplateSet>> = flowOf(emptyList())
        override suspend fun createTemplateFromSession(
            sessionId: String,
            name: String,
        ): DataResult<String> = DataResult.Success("t1")

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

        override suspend fun prependSets(
            templateExerciseId: String,
            edits: List<TemplateSetEdit>,
        ): DataResult<Unit> = DataResult.Success(Unit)

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


    @Test
    fun pairing_putsBothExercisesInOneGroup() = runTest(dispatcher) {
        // "These two, together" is what the tap says, so one exercise in a group is not the
        // outcome — and a group of one is a state nobody asked for (N24).
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onAddExercise("bench-press")
        settle()
        viewModel.onToggleSuperset(viewModel.uiState.value.exercises.last().id)
        settle()
        val groups = viewModel.uiState.value.exercises.map { it.supersetGroup }
        assertNotNull("the tapped exercise is in a group", groups.last())
        assertEquals("and so is the one above it", groups.first(), groups.last())
        assertEquals(
            "ROADMAP B27: one write covering the whole group, so a failure cannot leave half of it",
            1,
            repository.supersetGroups.size,
        )
        assertEquals(
            "and that one write named both rows",
            viewModel.uiState.value.exercises.map { it.id }.toSet(),
            repository.supersetGroups.single().toSet(),
        )
    }
    @Test
    fun unpairing_takesTheWholeGroupApart() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onAddExercise("bench-press")
        settle()
        val second = viewModel.uiState.value.exercises.last().id
        viewModel.onToggleSuperset(second)
        settle()
        viewModel.onToggleSuperset(second)
        settle()
        assertTrue(
            "a group of one is not a group",
            viewModel.uiState.value.exercises.all { it.supersetGroup == null },
        )
    }
    @Test
    fun inASuperset_nothingRestsUntilTheRoundIsDone() = runTest(dispatcher) {
        // ROADMAP N24: resting between the pair would defeat the pairing the user asked for.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onAddExercise("bench-press")
        settle()
        viewModel.onToggleSuperset(viewModel.uiState.value.exercises.last().id)
        settle()
        // Localising the failure: the state must actually carry the group, or the round
        // check has nothing to work with.
        val rows = viewModel.uiState.value.exercises
        assertNotNull("the pair is grouped in the state", rows.first().supersetGroup)
        assertEquals("and both share it", rows.first().supersetGroup, rows.last().supersetGroup)
        assertEquals("with no sets logged on the second yet", 0, rows.last().sets.size)

        // The fake seeds a rest value, so clearing it is what makes "no rest" observable.
        repository.lastRestSeconds = null

        // The first exercise of the pair: the second has not logged this round yet.
        viewModel.onLogSet(viewModel.uiState.value.exercises.first().id)
        settle()
        assertNull("no rest until the round is finished", repository.lastRestSeconds)
        // The second logs the same round, which is the round complete.
        repository.lastRestSeconds = null
        viewModel.onLogSet(viewModel.uiState.value.exercises.last().id)
        settle()
        assertNotNull("now the round is done, so it rests", repository.lastRestSeconds)
    }
    @Test
    fun anUngroupedExercise_stillRestsAfterEverySet() = runTest(dispatcher) {
        // The old behaviour, stated so N24 cannot quietly change it.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id)
        settle()
        assertNotNull(repository.lastRestSeconds)
    }


    @Test
    fun aSuperset_restsForItsLongestMember() = runTest(dispatcher) {
        // ROADMAP B15, against DECISIONS.md: the rest is the group's, not the member that
        // happened to close the round. Taking it from the closer meant the same pair rested 90
        // or 180 depending on which exercise the user logged last.
        val repository = FakeWorkoutRepository()
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        repository.restSecondsForNextExercise = 180
        viewModel.onAddExercise("back-squat")
        settle()
        repository.restSecondsForNextExercise = 90
        viewModel.onAddExercise("bench-press")
        settle()
        viewModel.onToggleSuperset(viewModel.uiState.value.exercises.last().id)
        settle()
        viewModel.onLogSet(viewModel.uiState.value.exercises.first().id)
        settle()

        // The 90-second member closes the round, and the group still rests for 180.
        repository.lastRestSeconds = null
        viewModel.onLogSet(viewModel.uiState.value.exercises.last().id)
        settle()

        assertEquals(180, repository.lastRestSeconds)
    }

    @Test
    fun aWarmUp_raisesNoRecord() = runTest(dispatcher) {
        // ROADMAP B17: arm WARMUP, log above the best at that rep count, and the banner used to
        // fire — exactly what the file's own doc said could no longer happen.
        val repository = FakeWorkoutRepository().apply {
            records = PersonalRecords(mapOf(8 to 17_500L))
        }
        val viewModel = viewModelFor(repository)
        observe(viewModel)
        settle()
        viewModel.onAddExercise("back-squat")
        settle()

        // The prefill is 20 kg × 8, above the recorded 17.5 — but as a warm-up.
        viewModel.onLogSet(
            sessionExerciseId = viewModel.uiState.value.exercises.single().id,
            setType = SetType.WARMUP,
        )
        settle()

        assertNull("a warm-up is not a personal best", viewModel.personalRecord.value)
    }


    @Test
    fun aRecord_namesTheBarThisSessionSet_notHistories() = runTest(dispatcher) {
        // ROADMAP B18, and the assertion the batch had no way of making until now: `records` and
        // the merged view agree until the session beats its own history at the same rep count, so
        // reading the wrong one was invisible. A plan with a ramp is what makes them differ: the
        // second set is prescribed at 22.5 against the 20 just lifted, while history holds 17.5.
        val repository = FakeWorkoutRepository().apply {
            records = PersonalRecords(mapOf(8 to 17_500L))
        }
        val templates = FakeTemplateRepository(
            planned = listOf(
                plannedExercise(
                    position = 0,
                    sets = listOf(
                        plannedSet(index = 0, reps = 8, weightGrams = 20_000L),
                        plannedSet(index = 1, reps = 8, weightGrams = 22_500L),
                    ),
                ),
            ),
        )
        val viewModel = viewModelFor(repository, templateId = "t1", templates = templates)
        observe(viewModel)
        settle()
        // The plan supplies the targets; the exercise itself is added the way the other tests do it.
        viewModel.onAddExercise("back-squat")
        settle()

        // Set one: a record over history.
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id, SetType.NORMAL)
        settle()
        assertEquals(17_500L, viewModel.personalRecord.value?.previousBestGrams)

        // Set two: the plan's 22.5 beats the 20 *this session* already logged, so that is what the
        // banner must name — history's 17.5 is no longer the bar.
        viewModel.onLogSet(viewModel.uiState.value.exercises.single().id, SetType.NORMAL)
        settle()

        val moment = viewModel.personalRecord.value
        assertNotNull(moment)
        assertEquals(22_500L, moment!!.weightGrams)
        assertEquals("the session's bar, not the older history", 20_000L, moment.previousBestGrams)
    }

    @Test
    fun repeatLast_opensTheSessionHoldingTheLastWorkoutsExercises() = runTest(dispatcher) {
        // ROADMAP B43: the SavedStateHandle never set `repeatLast`, so this branch was never entered by
        // any test and the fake's `repeatedExerciseIds` was never assigned — the whole path went
        // unexercised while each of its parts was tested separately.
        val repository = FakeWorkoutRepository()
        repository.repeatedExerciseIds = listOf("back-squat", "bench-press")
        val viewModel = viewModelFor(repository, repeatLast = true)
        observe(viewModel)
        settle()

        assertEquals(
            "the repeated workout's exercises arrive, in order",
            listOf("back-squat", "bench-press"),
            viewModel.uiState.value.exercises.map { it.exerciseId },
        )
    }

    @Test
    fun startingNormally_doesNotRepeatTheLastWorkout() = runTest(dispatcher) {
        // The other direction: the flag decides, so the test above cannot be passing because the fake
        // seeds something regardless.
        val repository = FakeWorkoutRepository()
        repository.repeatedExerciseIds = listOf("back-squat")
        val viewModel = viewModelFor(repository, repeatLast = false)
        observe(viewModel)
        settle()

        assertTrue("an ordinary start is empty", viewModel.uiState.value.exercises.isEmpty())
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
    override fun observeGoals(): Flow<Map<String, Double>> = flowOf(emptyMap())

    override suspend fun setGoal(metricId: String, value: Double?): DataResult<Unit> =
        DataResult.Success(Unit)


    private val rest = MutableStateFlow(initialRestSeconds)

    override fun observeDefaultRestSeconds(): Flow<Int> = rest.asStateFlow()

    override suspend fun setDefaultRestSeconds(seconds: Int): DataResult<Unit> {
        if (seconds !in SettingsRepository.VALID_REST_SECONDS) {
            return DataResult.Failure(DataError.Invalid("out of range"))
        }
        rest.value = seconds
        return DataResult.Success(Unit)
    }

    override fun observeRestCueEnabled(): Flow<Boolean> = flowOf(true)

    override suspend fun setRestCueEnabled(enabled: Boolean): DataResult<Unit> =
        DataResult.Success(Unit)

    override fun observeKeepScreenOn(): Flow<Boolean> = flowOf(true)

    override suspend fun setKeepScreenOn(enabled: Boolean): DataResult<Unit> =
        DataResult.Success(Unit)

    override fun observeStatisticsRange(): Flow<StatisticsRange> = flowOf(StatisticsRange())

    override suspend fun setStatisticsRange(range: StatisticsRange): DataResult<Unit> =
        DataResult.Success(Unit)
}

/**
 * Hand-written, because the project uses no mocking framework (DECISIONS.md).
 *
 * The workout screen reads a slot's prescription and that slot's history only when the route
 * carried a slot (ROADMAP P3.8); every write here is a path this screen never takes.
 */
private class FakeProgramRepository : ProgramRepository {
    override fun observePrograms(): Flow<List<WorkoutProgram>> = flowOf(emptyList())

    override fun observeProgram(programId: String): Flow<WorkoutProgram?> = flowOf(null)

    override fun observeActivePrograms(): Flow<List<WorkoutProgram>> = flowOf(emptyList())

    override fun observeSlots(programId: String): Flow<List<ProgramSlot>> = flowOf(emptyList())

    /** What the started slot prescribes; empty unless a test sets one (ROADMAP P3.8). */
    var prescriptions: List<SlotPrescription> = emptyList()

    override fun observeSlotPrescriptions(slotId: String): Flow<List<SlotPrescription>> =
        flowOf(prescriptions)

    override fun observeProgramRun(programId: String): Flow<ProgramRun?> = flowOf(null)

    override suspend fun setDeloadWeek(
        programId: String,
        weekStart: java.time.LocalDate,
        marked: Boolean,
    ): DataResult<Unit> = error("these tests do not mark a deload")

    /** N17's estimate a slot's percentage resolves against (ROADMAP P3.8). */
    var oneRepMax: Long? = null

    override suspend fun estimatedOneRepMax(exerciseId: String): DataResult<Long?> =
        DataResult.Success(oneRepMax)

    override suspend fun slotPreviousPerformance(
        slotId: String,
        exerciseId: String,
        currentSessionId: String,
        zone: java.time.ZoneId,
    ): DataResult<PreviousPerformance> = DataResult.Success(PreviousPerformance(emptyList()))

    override suspend fun createProgram(name: String): DataResult<String> =
        error("the workout screen does not create a program")

    override suspend fun renameProgram(programId: String, name: String): DataResult<Unit> =
        error("the workout screen does not rename a program")

    override suspend fun deleteProgram(programId: String): DataResult<Unit> =
        error("the workout screen does not delete a program")

    override suspend fun activateProgram(programId: String): DataResult<Unit> =
        error("the workout screen does not activate a program")

    override suspend fun deactivateProgram(programId: String): DataResult<Unit> =
        error("the workout screen does not deactivate a program")

    override suspend fun moveProgram(programId: String, delta: Int): DataResult<Unit> =
        error("the workout screen does not move a program")

    override suspend fun addSlot(
        programId: String,
        templateId: String,
        weekday: DayOfWeek?,
    ): DataResult<Unit> = error("the workout screen does not add a slot")

    override suspend fun setSlotWeekday(slotId: String, weekday: DayOfWeek?): DataResult<Unit> =
        error("the workout screen does not schedule a slot")

    override suspend fun moveSlot(slotId: String, delta: Int): DataResult<Unit> =
        error("the workout screen does not move a slot")

    override suspend fun removeSlot(slotId: String): DataResult<Unit> =
        error("the workout screen does not remove a slot")

    override suspend fun setSlotExercisePlan(
        slotId: String,
        exerciseId: String,
        restSeconds: Int?,
        techniqueNote: String?,
    ): DataResult<Unit> = error("the workout screen does not prescribe an exercise")

    override suspend fun addSlotSet(
        slotId: String,
        exerciseId: String,
        edit: SlotSetEdit,
    ): DataResult<Unit> = error("the workout screen does not prescribe a set")

    override suspend fun updateSlotSet(slotSetId: String, edit: SlotSetEdit): DataResult<Unit> =
        error("the workout screen does not edit a prescribed set")

    override suspend fun removeSlotSet(slotSetId: String): DataResult<Unit> =
        error("the workout screen does not remove a prescribed set")

    override suspend fun pendingOccurrences(
        today: java.time.LocalDate,
        zone: java.time.ZoneId,
    ): DataResult<List<PendingOccurrence>> = error("the workout screen does not ask about misses")

    override suspend fun skipOccurrences(
        slotIds: List<String>,
        weekStart: java.time.LocalDate,
    ): DataResult<Unit> = error("the workout screen does not record a skip")

    override suspend fun monthAdherence(
        month: java.time.YearMonth,
        today: java.time.LocalDate,
        zone: java.time.ZoneId,
    ): DataResult<AdherenceReport> = error("the workout screen does not read adherence")
}
