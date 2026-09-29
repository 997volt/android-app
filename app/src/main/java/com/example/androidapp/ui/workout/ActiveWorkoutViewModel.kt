package com.example.androidapp.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.RestNotifier
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.taxonomySubtitle
import com.example.androidapp.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A logged set as the screen shows it: position, weight, reps, and N6's extras. */
data class SetRow(
    val id: String,
    val number: Int,
    val reps: Int,
    val weightGrams: Long,
    /** 1–10, or null when none was recorded (ROADMAP N6). */
    val rpe: Int? = null,
    /** A short comment on the set, or null (ROADMAP N6). */
    val note: String? = null,
)

/** One exercise in the workout, with its sets and what the next set will prefill. */
data class SessionExerciseRow(
    val id: String,
    val exerciseId: String,
    val name: String,
    /** `Quads · Barbell`, or null while a custom exercise's taxonomy is unset. */
    val subtitle: String?,
    /** A cue to read while lifting, or null (ROADMAP N5). */
    val techniqueNote: String? = null,
    /** This exercise's own rest, or null for the app default (ROADMAP N5). */
    val restSeconds: Int? = null,
    val sets: List<SetRow> = emptyList(),
    val suggestion: SetSuggestion = SetSuggestion(DEFAULT_REPS, Weight.DEFAULT_GRAMS),
    val lastTime: SetRow? = null,
)

/**
 * The values that change every second (ROADMAP F16).
 *
 * Deliberately **separate** from [ActiveWorkoutUiState]. Folding a one-second
 * ticker into the screen's single state meant every tick produced a new state
 * object, and because that object also holds `List`s — which Compose cannot treat
 * as stable — the exercise list recomposed once a second along with everything
 * else. The clock is read only by the two small composables that display it, so
 * the list is off the per-second path entirely.
 */
data class WorkoutClock(
    val elapsed: String = "",
    val restSecondsRemaining: Int = 0,
) {
    /** One source of truth: "resting" is just "there is time left". */
    val isResting: Boolean get() = restSecondsRemaining > 0
}

data class ActiveWorkoutUiState(
    val isLoading: Boolean = true,
    val sessionId: String? = null,
    /** Fixed for the life of the session, so it does not belong on the clock. */
    val startedAt: String = "",
    val exercises: List<SessionExerciseRow> = emptyList(),
    /** A just-deleted set awaiting undo; the screen shows it as a snackbar. */
    val pendingUndo: SetEntry? = null,
    /** What was not recovered today, or null (ROADMAP N4). */
    val readinessNote: String? = null,
    /** True while a just-opened session is asking for that note. */
    val isReadinessPromptVisible: Boolean = false,
    /** Set when a write failed, so the screen can say so instead of lying. */
    val error: DataError? = null,
) {
    /** An open session with nothing in it — the state a user can abandon. */
    val isEmpty: Boolean get() = !isLoading && sessionId != null && exercises.isEmpty()

    /** No open session at all: finished, discarded, or never started. */
    val hasNoSession: Boolean get() = !isLoading && sessionId == null
}

/**
 * Owns the active workout: its exercises, their sets, and the rest timer
 * (ROADMAP P1.2, P1.3, P1.4, P1.8).
 *
 * The screen auto-starts a session on entry. That is deliberate: the whole point
 * of P1.8 is that the session exists in the database before anything is logged,
 * so a process death or a crash mid-set leaves a recoverable workout rather than
 * nothing. A session with no exercises is left for the user to discard rather
 * than cleaned up implicitly — an invisible delete is worse than a visible one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val timeSource: TimeSource,
    private val restNotifier: RestNotifier,
) : ViewModel() {

    private val lastError = MutableStateFlow<DataError?>(null)
    private val pendingUndo = MutableStateFlow<SetEntry?>(null)
    private val previousByExercise = MutableStateFlow<Map<String, PreviousPerformance>>(emptyMap())

    /** True while a just-opened session is asking what was not recovered today (N4). */
    private val readinessPromptVisible = MutableStateFlow(false)

    /** Emits true once a finish or discard succeeds, so the screen can leave. */
    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed

    /**
     * Shared so the session is not queried once per downstream flow.
     */
    private val activeSession = workoutRepository.observeActiveSession()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val sessionExercises: Flow<List<SessionExercise>> = activeSession
        .flatMapLatest { session ->
            if (session == null) flowOf(emptyList()) else workoutRepository.observeSessionExercises(session.id)
        }

    /**
     * Shared as state so the ViewModel can look a set up synchronously. That is
     * what lets delete/undo be expressed by id, instead of handing the screen a
     * domain object it has no other use for.
     */
    private val setsState: StateFlow<List<SetEntry>> = activeSession
        .flatMapLatest { session ->
            if (session == null) flowOf(emptyList()) else workoutRepository.observeSets(session.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Everything that describes the workout's contents. */
    private data class Snapshot(
        val session: WorkoutSession?,
        val exercises: List<SessionExercise>,
        val sets: List<SetEntry>,
        val previous: Map<String, PreviousPerformance>,
    )

    private val snapshots: Flow<Snapshot> = combine(
        activeSession,
        sessionExercises,
        setsState,
        previousByExercise,
    ) { session, exercises, logged, previous ->
        Snapshot(session, exercises, logged, previous)
    }

    /** Drives both the elapsed clock and the rest countdown; stops when unsubscribed. */
    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        snapshots,
        lastError,
        pendingUndo,
        readinessPromptVisible,
    ) { snapshot, error, undo, promptVisible ->
        snapshot.toUiState(error = error, undo = undo, readinessPromptVisible = promptVisible)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ActiveWorkoutUiState(),
    )

    /**
     * Ticks once a second.
     *
     * Nothing in the state above depends on it, so a tick can only recompose the
     * two composables that read this — the elapsed header and the rest bar.
     */
    val clock: StateFlow<WorkoutClock> = combine(activeSession, ticker) { session, _ ->
        val now = timeSource.now()
        WorkoutClock(
            elapsed = session
                ?.let { WorkoutFormat.elapsed(Duration.between(it.startedAt, now)) }
                .orEmpty(),
            restSecondsRemaining = RestTimer.remainingSeconds(session?.restEndsAt, now),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = WorkoutClock(),
    )

    init {
        viewModelScope.launch {
            when (val result = workoutRepository.startOrResumeSession()) {
                is DataResult.Success -> {
                    lastError.value = null
                    // Only a freshly opened session asks (ROADMAP N4). A resumed one
                    // has already had its chance, and re-asking after a process death
                    // would be nagging rather than prompting.
                    readinessPromptVisible.value = result.data.isNew
                }

                is DataResult.Failure -> lastError.value = result.error
            }
        }

        // Load each exercise's previous performance once, when it first appears.
        // Doing it here rather than per recomposition keeps the "last time" label
        // from re-querying on every tick of the elapsed clock.
        viewModelScope.launch {
            sessionExercises.collect { exercises ->
                val sessionId = activeSession.replayCache.firstOrNull()?.id ?: return@collect
                exercises
                    .map { it.exerciseId }
                    .filterNot { it in previousByExercise.value }
                    .forEach { exerciseId ->
                        val result = workoutRepository.previousPerformance(exerciseId, sessionId)
                        if (result is DataResult.Success) {
                            previousByExercise.update { it + (exerciseId to result.data) }
                        }
                    }
            }
        }
    }

    fun onAddExercise(exerciseId: String) = write { sessionId ->
        workoutRepository.addExercise(sessionId, exerciseId)
    }

    fun onRemoveExercise(sessionExerciseId: String) = write {
        workoutRepository.removeExercise(sessionExerciseId)
    }

    /** Logs a set using the prefilled values, then starts the rest (P1.4). */
    fun onLogSet(sessionExerciseId: String) {
        val row = uiState.value.exercises.firstOrNull { it.id == sessionExerciseId } ?: return
        viewModelScope.launch {
            val result = workoutRepository.logSet(
                sessionExerciseId = sessionExerciseId,
                reps = row.suggestion.reps,
                weightGrams = row.suggestion.weightGrams,
            )
            handle(result)
            // The exercise's own rest when it has one, otherwise the app default
            // (ROADMAP N5). The +15 s/−15 s controls remain one-off adjustments.
            if (result is DataResult.Success) {
                startRest(row.restSeconds ?: RestTimer.DEFAULT_SECONDS)
            }
        }
    }

    fun onUpdateSet(setId: String, reps: Int, weightGrams: Long, rpe: Int?, note: String?) {
        viewModelScope.launch {
            handle(workoutRepository.updateSet(setId, reps, weightGrams, rpe, note))
        }
    }

    fun onDeleteSet(setId: String) {
        val set = setsState.value.firstOrNull { it.id == setId } ?: return
        viewModelScope.launch {
            when (val result = workoutRepository.deleteSet(set.id)) {
                is DataResult.Success -> pendingUndo.value = set
                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    /**
     * Restores a deleted set.
     *
     * Re-logging appends it rather than restoring its original index — the values
     * come back, the position may not. That is the honest limit of an undo backed
     * by a soft delete, and better than pretending the row was never touched.
     */
    fun onUndoDelete() {
        val set = pendingUndo.value ?: return
        pendingUndo.value = null
        viewModelScope.launch {
            handle(
                workoutRepository.logSet(
                    sessionExerciseId = set.sessionExerciseId,
                    reps = set.reps,
                    weightGrams = set.weightGrams,
                    setType = set.setType,
                ),
            )
        }
    }

    fun onDismissUndo() {
        pendingUndo.value = null
    }

    /**
     * Writes the readiness note (ROADMAP N4), from the prompt a new session opens
     * with or from the workout header afterwards. A null or blank note clears it.
     */
    fun onSaveReadinessNote(note: String?) {
        val sessionId = uiState.value.sessionId ?: return
        viewModelScope.launch {
            when (val result = workoutRepository.setReadinessNote(sessionId, note)) {
                is DataResult.Success -> {
                    lastError.value = null
                    readinessPromptVisible.value = false
                }

                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    /**
     * Skips the prompt without writing anything. The header keeps offering the
     * field, so skipping is not a dead end.
     */
    fun onDismissReadinessPrompt() {
        readinessPromptVisible.value = false
    }

    fun onSkipRest() {
        viewModelScope.launch {
            handle(workoutRepository.clearRest())
            restNotifier.cancel()
        }
    }

    fun onAdjustRest(deltaSeconds: Int) {
        viewModelScope.launch {
            when (val result = workoutRepository.adjustRest(deltaSeconds)) {
                is DataResult.Success -> restNotifier.schedule(result.data)
                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    fun onFinish() = write(closeAfterwards = true) { sessionId ->
        workoutRepository.finishSession(sessionId)
    }

    fun onDiscard() = write(closeAfterwards = true) { sessionId ->
        workoutRepository.deleteSession(sessionId)
    }

    private suspend fun startRest(seconds: Int) {
        when (val result = workoutRepository.startRest(seconds)) {
            is DataResult.Success -> restNotifier.schedule(result.data)
            is DataResult.Failure -> lastError.value = result.error
        }
    }

    private fun handle(result: DataResult<*>) {
        when (result) {
            is DataResult.Success -> lastError.value = null
            is DataResult.Failure -> lastError.value = result.error
        }
    }

    /**
     * Runs [block] against the current session and records the failure, if any.
     *
     * The error is kept in state rather than thrown: a dropped write must be
     * visible to the user, and an exception inside a coroutine would not be.
     */
    private fun write(
        closeAfterwards: Boolean = false,
        block: suspend (String) -> DataResult<Unit>,
    ) {
        val sessionId = uiState.value.sessionId ?: return
        viewModelScope.launch {
            when (val result = block(sessionId)) {
                is DataResult.Success -> {
                    lastError.value = null
                    if (closeAfterwards) {
                        // A workout that is over must not leave an alarm armed.
                        restNotifier.cancel()
                        _closed.value = true
                    }
                }

                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    private fun Snapshot.toUiState(
        error: DataError?,
        undo: SetEntry?,
        readinessPromptVisible: Boolean,
    ): ActiveWorkoutUiState =
        ActiveWorkoutUiState(
            isLoading = false,
            sessionId = session?.id,
            startedAt = session?.let { WorkoutFormat.clockTime(it.startedAt) }.orEmpty(),
            exercises = exercises.map { it.toRow(sets = sets, previous = previous[it.exerciseId]) },
            pendingUndo = undo,
            readinessNote = session?.readinessNote,
            isReadinessPromptVisible = readinessPromptVisible,
            error = error,
        )

    private fun SessionExercise.toRow(
        sets: List<SetEntry>,
        previous: PreviousPerformance?,
    ): SessionExerciseRow {
        val loggedSets = sets
            .filter { it.sessionExerciseId == id }
            .sortedBy { it.setIndex }
            .mapIndexed { index, set ->
                SetRow(
                    id = set.id,
                    // Displayed 1-based and renumbered, so deleting the first set
                    // leaves the rest reading 1, 2, 3 rather than 2, 3, 4.
                    number = index + 1,
                    reps = set.reps,
                    weightGrams = set.weightGrams,
                    rpe = set.rpe,
                    note = set.note,
                )
            }

        return SessionExerciseRow(
            id = id,
            exerciseId = exerciseId,
            name = exerciseName,
            subtitle = taxonomySubtitle(primaryMuscle, equipment),
            techniqueNote = techniqueNote,
            restSeconds = restSeconds,
            sets = loggedSets,
            suggestion = suggestionForNextSet(loggedSets, previous, nextIndex = loggedSets.size),
            lastTime = previous?.sets?.firstOrNull()?.let { first ->
                SetRow(id = first.id, number = 1, reps = first.reps, weightGrams = first.weightGrams)
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TICK_MILLIS = 1_000L
    }
}
