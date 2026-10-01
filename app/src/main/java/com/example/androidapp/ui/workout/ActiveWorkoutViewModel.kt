package com.example.androidapp.ui.workout

import java.time.ZoneId
import com.example.androidapp.domain.model.zoneIdOrNull
import com.example.androidapp.domain.model.PersonalRecords
import com.example.androidapp.domain.model.PersonalRecordMoment
import com.example.androidapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import com.example.androidapp.domain.model.comparePlanToActual
import com.example.androidapp.domain.model.PlannedSetSpec
import com.example.androidapp.domain.model.PlanComparison
import com.example.androidapp.domain.model.PerformedSetSpec
import com.example.androidapp.domain.model.ExercisePlan
import com.example.androidapp.domain.model.ExerciseActual
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.RestTimer
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.taxonomySubtitle
import com.example.androidapp.domain.repository.WorkoutRepository
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.ui.navigation.ActiveWorkout
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
    val rpeHalves: Int? = null,
    /** A short comment on the set, or null (ROADMAP N6). */
    val note: String? = null,
    /** The role it was performed as (ROADMAP N14). */
    val setType: SetType = SetType.NORMAL,
    /** The machine's assistance, 0 for none (ROADMAP N15). */
    val assistanceGrams: Long = 0,
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
    /**
     * The superset or circuit this exercise is performed in, or null (ROADMAP N24).
     *
     * Exercises sharing it are done in rounds, so the screen labels them A1/A2 and the rest
     * waits for the round to finish.
     */
    val supersetGroup: Int? = null,
    /** `A1`/`A2` for a grouped exercise — giant-set notation, or null (ROADMAP N24). */
    val supersetLabel: String? = null,
    /** This exercise's own rest, or null for the app default (ROADMAP N5). */
    val restSeconds: Int? = null,
    /**
     * True once this exercise has been marked done (ROADMAP N7): its Log set button
     * is hidden, its sets are dimmed and not editable, and Reopen restores both.
     */
    val isFinished: Boolean = false,
    /** How well the target muscle was worked, 1–10, or null (ROADMAP N8). */
    val muscleFeel: Int? = null,
    /** Joint or connective-tissue discomfort, 1–10, or null (ROADMAP N8). */
    val jointPain: Int? = null,
    /** Which joints, or null (ROADMAP N9) — the same "nothing" as everywhere else. */
    val jointPainNote: String? = null,
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
    /**
     * An exercise just marked done, awaiting undo (ROADMAP N7). The id is enough:
     * the row is still in [exercises], so the snackbar can name it.
     */
    val pendingFinishedExerciseId: String? = null,
    /** What was not recovered today, or null (ROADMAP N4). */
    val readinessNote: String? = null,
    /** True while a just-opened session is asking for that note. */
    val isReadinessPromptVisible: Boolean = false,
    /** Set when a write failed, so the screen can say so instead of lying. */
    val error: DataError? = null,
) {
    /**
     * The deleted set the screen may offer to bring back, or null (ROADMAP B3).
     *
     * An undo is only offered while its subject is still in the session. The two undo
     * snackbars share one host, so without this a deleted set's Undo can outlive its
     * exercise: the row is gone, the button stays, and the write it fires is refused.
     * The rule lives here, named, rather than in the rendering — and the ViewModel
     * still reports if a tap races through before this turns null.
     */
    val undoableSet: SetEntry?
        get() = pendingUndo?.takeIf { set -> exercises.any { it.id == set.sessionExerciseId } }

    /** The Done undo, offered on the same terms (ROADMAP B3). */
    val undoableFinishedExerciseId: String?
        get() = pendingFinishedExerciseId?.takeIf { id -> exercises.any { it.id == id } }

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
    private val templateRepository: TemplateRepository,
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * Set when the workout was started from a template (ROADMAP N3).
     *
     * It only ever matters for the call that *opens* the session; the repository
     * seeds the exercises there, inside the same transaction. A resumed session
     * leaves this unused, which is what makes a template start idempotent.
     */
    private val templateId: String? = savedStateHandle.toRoute<ActiveWorkout>().templateId

    /** Whether this session should open with the last workout's exercises (ROADMAP N29). */
    private val repeatLast: Boolean = savedStateHandle.toRoute<ActiveWorkout>().repeatLast

    /**
     * The plan this workout was started from, or empty (ROADMAP N14).
     *
     * Nothing is stored on the session to link it: the route already carries the
     * template, and the back stack keeps it across process death, so the plan stays a
     * plan — editing it changes what the next workout prefills, which is what N16
     * calls a living template.
     */
    private val plannedExercises: Flow<List<TemplateExercise>> =
        templateId?.let { templateRepository.observeExercises(it) } ?: flowOf(emptyList())

    private val lastError = MutableStateFlow<DataError?>(null)

    /**
     * The review of the workout that just finished, published instead of closing the
     * screen outright (ROADMAP N20).
     *
     * Finish used to be a dead end: the ratings, the readiness note and the totals went
     * nowhere, and a plan was never compared with what was actually lifted. The summary is
     * built from state this screen already holds, so it costs no reads and needs no schema
     * — but it also means it is a *moment*, not something to come back to.
     */
    /**
     * The app-wide default rest, kept current while the screen is open (ROADMAP N21).
     *
     * Collected rather than read once: a change made in settings must reach a workout that
     * is already running, which is the reason the store exposes a `Flow` at all.
     */
    private val defaultRestSeconds = settingsRepository.observeDefaultRestSeconds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, RestTimer.DEFAULT_SECONDS)

    /**
     * The record just set, if the last logged set was one (ROADMAP N23).
     *
     * Not dismissible on purpose: it clears when the next set is logged, which is when the
     * news is stale — a banner that needs dismissing is a banner in the way between sets.
     */
    /**
     * Whether a finished rest is heard as well as seen (ROADMAP N27).
     *
     * Read from settings rather than held as its own state: the flag belongs to the app, and the
     * screen only needs the current answer.
     */
    val restCueEnabled: StateFlow<Boolean> = settingsRepository.observeRestCueEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Whether the screen should stay awake while this workout is open (ROADMAP N27). */
    val keepScreenOn: StateFlow<Boolean> = settingsRepository.observeKeepScreenOn()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _personalRecord = MutableStateFlow<PersonalRecordMoment?>(null)
    val personalRecord: StateFlow<PersonalRecordMoment?> = _personalRecord.asStateFlow()

    private val _summary = MutableStateFlow<WorkoutReview?>(null)
    val summary: StateFlow<WorkoutReview?> = _summary.asStateFlow()

    private val pendingUndo = MutableStateFlow<SetEntry?>(null)
    private val previousByExercise = MutableStateFlow<Map<String, PreviousPerformance>>(emptyMap())

    /** True while a just-opened session is asking what was not recovered today (N4). */
    private val readinessPromptVisible = MutableStateFlow(false)

    /** The exercise just marked done, awaiting the snackbar's undo (ROADMAP N7). */
    private val pendingFinishedExercise = MutableStateFlow<String?>(null)

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
        val planned: List<TemplateExercise>,
    )

    private val snapshots: Flow<Snapshot> = combine(
        activeSession,
        sessionExercises,
        setsState,
        previousByExercise,
        plannedExercises,
    ) { session, exercises, logged, previous, planned ->
        Snapshot(session, exercises, logged, previous, planned)
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
        pendingFinishedExercise,
    ) { snapshot, error, undo, promptVisible, finishedExercise ->
        snapshot.toUiState(
            error = error,
            undo = undo,
            readinessPromptVisible = promptVisible,
            pendingFinishedExerciseId = finishedExercise,
        )
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
            // Repeating is the same open, with the last workout's exercises as the seed instead of a
            // plan's — both go through the one append path (ROADMAP N3, N29).
            val opened =
                if (repeatLast) {
                    workoutRepository.repeatLastSession()
                } else {
                    workoutRepository.startOrResumeSession(templateId)
                }
            when (val result = opened) {
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

    /**
     * Marks an exercise done (ROADMAP N7) and offers an undo, because the mis-tap
     * this prevents is also the mis-tap it can cause.
     *
     * [muscleFeel] and [jointPain] are the skippable half (ROADMAP N8): they are
     * written first, so a failure leaves the exercise open with an error to read
     * rather than done with the ratings silently lost.
     */
    fun onFinishExercise(
        sessionExerciseId: String,
        muscleFeel: Int? = null,
        jointPain: Int? = null,
        jointPainNote: String? = null,
    ) {
        viewModelScope.launch {
            if (muscleFeel != null || jointPain != null) {
                val rated = workoutRepository.rateExercise(
                    sessionExerciseId = sessionExerciseId,
                    muscleFeel = muscleFeel,
                    jointPain = jointPain,
                    jointPainNote = jointPainNote,
                )
                if (rated is DataResult.Failure) {
                    lastError.value = rated.error
                    return@launch
                }
                lastError.value = null
            }
            when (val result = workoutRepository.finishExercise(sessionExerciseId)) {
                is DataResult.Success -> {
                    lastError.value = null
                    pendingFinishedExercise.value = sessionExerciseId
                }

                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    /**
     * Puts a just-finished exercise back into edit, from the snackbar (N7).
     *
     * Checks its subject first (ROADMAP B3): the snackbar can outlive the exercise
     * it refers to, and a doomed write would report `NotFound` about a row the user
     * never asked about.
     */

    fun onDismissFinishUndo() {
        pendingFinishedExercise.value = null
    }

    /**
     * Writes how an exercise felt, at any time (ROADMAP N10).
     *
     * The same write the Done prompt makes, without finishing anything: the ratings
     * are worth recording while the set is still fresh, and the prompt is then the
     * last chance rather than the only one.
     */
    fun onRateExercise(
        sessionExerciseId: String,
        muscleFeel: Int?,
        jointPain: Int?,
        jointPainNote: String?,
    ) {
        viewModelScope.launch {
            handle(
                workoutRepository.rateExercise(
                    sessionExerciseId = sessionExerciseId,
                    muscleFeel = muscleFeel,
                    jointPain = jointPain,
                    jointPainNote = jointPainNote,
                ),
            )
        }
    }

    /** Reopens a done exercise from its own button (N7). */
    /**
     * Puts a done exercise back into edit (N7): from its own button, or from the
     * snackbar's undo, which is why both go through here rather than through two
     * paths that could drift apart.
     */
    fun onReopenExercise(sessionExerciseId: String) {
        // Reopening *is* the undo, so the offer goes with it: the state already answers
        // "is it still in the session?", which is why the separate wrapper for the banner
        // was a second name for one operation.
        pendingFinishedExercise.value = null
        // Reopening something that is gone — undone twice, or removed meanwhile — is reported
        // rather than silently ignored: the guard that used to sit in the undo wrapper is this
        // question, asked where the action now is.
        if (!uiState.value.hasLiveExercise(sessionExerciseId)) {
            lastError.value = GONE_FROM_SESSION
            return
        }
        viewModelScope.launch {
            when (val result = workoutRepository.reopenExercise(sessionExerciseId)) {
                is DataResult.Success -> lastError.value = null
                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }


    /**
     * Logs a set using the prefilled values, then starts the rest (P1.4).
     *
     * The role comes from the caller (ROADMAP N19): it is a choice about *this* set, made
     * at the button, so it lives with the button rather than in this screen's state — and
     * nothing here has to remember to clear it afterwards.
     */
    fun onLogSet(sessionExerciseId: String, setType: SetType = SetType.NORMAL) {
        val row = uiState.value.exercises.firstOrNull { it.id == sessionExerciseId } ?: return
        viewModelScope.launch {
            // Read *before* the set is written, and excluding this session (ROADMAP N23): a
            // record is about beating history, and the set being logged is not history yet.
            // A failed read means no claim either way rather than a missed record.
            val records = when (
                val read = workoutRepository.personalRecords(
                    exerciseId = row.exerciseId,
                    excludingSessionId = uiState.value.sessionId,
                )
            ) {
                is DataResult.Success -> read.data
                is DataResult.Failure -> null
            }
            // History *and* what this session has already logged: without the second half the
            // same record is announced on every set that repeats it (found by a test).
            val against = records?.mergedWith(
                PersonalRecords.from(
                    row.sets.map {
                        PerformedSetSpec(
                            role = it.setType,
                            weightGrams = it.weightGrams,
                            assistanceGrams = it.assistanceGrams,
                            reps = it.reps,
                        )
                    },
                ),
            )
            _personalRecord.value = null

            val result = workoutRepository.logSet(
                sessionExerciseId = sessionExerciseId,
                reps = row.suggestion.reps,
                weightGrams = row.suggestion.weightGrams,
                setType = setType,
                // The button reads "-20 kg × 8"; a set written without the help would
                // be a different set from the one it just described (ROADMAP B7, D3).
                assistanceGrams = row.suggestion.assistanceGrams,
            )
            handle(result)
            if (result is DataResult.Success) {
                // A record noticed a week later in a list is a record nobody feels (N23).
                // The role goes in, or a heavy warm-up raises a best (B17); and what it beat is
                // read from the *merged* view, because the bar may have been set earlier in this
                // same session — saying "the first time at this rep count" then would be false
                // while claiming a record over that very set (B18).
                if (
                    against != null &&
                    against.isRecord(row.suggestion.reps, row.suggestion.weightGrams, setType)
                ) {
                    _personalRecord.value = PersonalRecordMoment(
                        exerciseName = row.name,
                        reps = row.suggestion.reps,
                        weightGrams = row.suggestion.weightGrams,
                        previousBestGrams = against.bestAt(row.suggestion.reps),
                    )
                }

                // ROADMAP N24: in a superset the rest belongs to the round, not the set, so
                // it waits until nothing else in the group is behind. Resting here would
                // defeat the pairing the user just asked for.
                if (uiState.value.roundIsCompleteFor(row)) {
                    // The group's own rest wins in a superset, then this exercise's, then the
                    // app setting (ROADMAP N5, N21, B15). Taking the rest from whichever member
                    // happened to close the round made the wait depend on the order the user
                    // logged in — the same round resting differently every time round — and the
                    // settled rule is the group's longest, so the pair is paced by its slowest
                    // member.
                    val rest = uiState.value.longestRestInRound(row)
                        ?: row.restSeconds
                        ?: defaultRestSeconds.value
                    startRest(rest)
                }
            }
        }
    }

    /**
     * Joins this exercise to the one above it in a superset, or leaves the group (N24).
     *
     * Pairing with the previous exercise is the only gesture that needs no new screen: the
     * order is already on screen, and "these two, together" is what a tap on the second one
     * means.
     */
    fun onToggleSuperset(sessionExerciseId: String) {
        val ordered = uiState.value.exercises
        val index = ordered.indexOfFirst { it.id == sessionExerciseId }
        if (index < 0) return
        val me = ordered[index]
        val previous = ordered.getOrNull(index - 1)

        val group = when {
            // Already grouped: leaving takes the whole group apart, because a group of one is
            // not a group and half a superset is a state nobody asked for.
            me.supersetGroup != null -> null
            previous?.supersetGroup != null -> previous.supersetGroup
            previous != null -> (ordered.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1
            else -> null
        }

        viewModelScope.launch {
            // Leaving takes the whole group apart, because a group of one is not a group and
            // half a superset is a state nobody asked for; joining pulls the exercise above in,
            // because a superset is at least two exercises.
            val changed = if (group == null) {
                ordered.filter { it.supersetGroup == me.supersetGroup }.map { it.id }
            } else {
                listOf(sessionExerciseId) + listOfNotNull(previous?.takeIf { it.supersetGroup == null }?.id)
            }
            // One write for the whole group: a partial one would leave half a superset, which is
            // the state this action exists to avoid (ROADMAP B27).
            handle(workoutRepository.setSupersetGroup(changed, group))
        }
    }

    fun onUpdateSet(
        setId: String,
        reps: Int,
        weightGrams: Long,
        rpeHalves: Int? = null,
        note: String? = null,
        setType: SetType = SetType.NORMAL,
        assistanceGrams: Long = 0,
    ) {
        viewModelScope.launch {
            handle(
                workoutRepository.updateSet(
                    setId = setId,
                    reps = reps,
                    weightGrams = weightGrams,
                    rpeHalves = rpeHalves,
                    note = note,
                    setType = setType,
                    assistanceGrams = assistanceGrams,
                ),
            )
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
        // The reason this reports rather than no-ops (ROADMAP B3): the set can only
        // come back onto a live exercise, and the repository's loggable guard would
        // otherwise refuse the write and leave the user with nothing on screen —
        // the reported bug was exactly this, with the Undo still visible.
        if (!uiState.value.hasLiveExercise(set.sessionExerciseId)) {
            lastError.value = GONE_FROM_SESSION
            return
        }
        viewModelScope.launch {
            handle(
                workoutRepository.logSet(
                    sessionExerciseId = set.sessionExerciseId,
                    reps = set.reps,
                    weightGrams = set.weightGrams,
                    setType = set.setType,
                    // An undo puts back the set that was deleted, help included (B7).
                    assistanceGrams = set.assistanceGrams,
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
        }
    }

    fun onAdjustRest(deltaSeconds: Int) {
        viewModelScope.launch {
            // No scheduling branch: the rest is in-app only now (ROADMAP N26), so a success has
            // nothing left to do beyond the end instant the repository just wrote.
            handle(workoutRepository.adjustRest(deltaSeconds))
        }
    }

    /**
     * Finishes the workout, with the comment the prompt collected (ROADMAP N11).
     *
     * A null or blank [note] writes nothing at all, so *Skip* and *Save* on an empty
     * field are the same thing — there is no second representation of "no comment"
     * to get out of step.
     */
    fun onFinish(note: String? = null) {
        viewModelScope.launch {
            val sessionId = uiState.value.sessionId
            if (sessionId == null) {
                lastError.value = DataError.NotFound
                return@launch
            }
            if (!note.isNullOrBlank()) {
                val written = workoutRepository.setWorkoutNotes(sessionId, note)
                if (written is DataResult.Failure) {
                    // The comment is not worth losing the workout over, but it must
                    // not disappear silently either.
                    lastError.value = written.error
                    return@launch
                }
            }
            // Captured *before* finishing, and this is the bug a device found: the state
            // describes the *live* session, so the moment the workout is stored its
            // exercises and sets leave the flow — a review built afterwards read
            // "Sets 0 · reps 0 · 0 kg" and called a set that was just logged "not
            // performed". The review is about work that exists only until this call
            // returns, so it is taken here.
            val finishedState = uiState.value
            val plan = plannedExercises.first()

            when (val result = workoutRepository.finishSession(sessionId)) {
                is DataResult.Success -> {
                    lastError.value = null
                    // The workout is over and stored; the review is what the user sees
                    // next, and dismissing it is what closes the screen (N20).
                    _summary.value = buildSummary(finishedState, plan, note)
                }

                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    /** Dismisses the review, which is what actually closes the screen (ROADMAP N20). */
    fun onDismissSummary() {
        _summary.value = null
        closeSession()
    }


    /**
     * What "this workout is over" means, in one place.
     *
     * Every path that closes the screen goes through here: a workout that is over must
     * not leave a rest alarm armed, and a path that forgot the cancel would be silent —
     * exactly what happened when N11 rewrote `onFinish` and skipped `write`.
     */
    private fun closeSession() {
        _closed.value = true
    }

    fun onDiscard() = write(closeAfterwards = true) { sessionId ->
        workoutRepository.deleteSession(sessionId)
    }

    private suspend fun startRest(seconds: Int) {
        handle(workoutRepository.startRest(seconds))
    }

    /** True while the exercise is still part of the open session's list. */
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
                    if (closeAfterwards) closeSession()
                }

                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    private fun Snapshot.toUiState(
        error: DataError?,
        undo: SetEntry?,
        readinessPromptVisible: Boolean,
        pendingFinishedExerciseId: String?,
    ): ActiveWorkoutUiState =
        ActiveWorkoutUiState(
            isLoading = false,
            sessionId = session?.id,
            startedAt = session?.let {
                WorkoutFormat.clockTime(it.startedAt, zone = it.zoneIdOrNull() ?: ZoneId.systemDefault())
            }.orEmpty(),
            exercises = exercises.map {
                it.toRow(
                    sets = sets,
                    previous = previous[it.exerciseId],
                    planned = planned,
                    supersetLabels = supersetLabelsFor(exercises),
                )
            },
            pendingUndo = undo,
            pendingFinishedExerciseId = pendingFinishedExerciseId,
            readinessNote = session?.readinessNote,
            isReadinessPromptVisible = readinessPromptVisible,
            error = error,
        )

    private fun SessionExercise.toRow(
        sets: List<SetEntry>,
        previous: PreviousPerformance?,
        planned: List<TemplateExercise>,
        supersetLabels: Map<String, String>,
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
                    rpeHalves = set.rpeHalves,
                    note = set.note,
                    setType = set.setType,
                    assistanceGrams = set.assistanceGrams,
                )
            }

        return SessionExerciseRow(
            id = id,
            exerciseId = exerciseId,
            name = exerciseName,
            subtitle = taxonomySubtitle(primaryMuscle, equipment),
            techniqueNote = techniqueNote,
            supersetGroup = supersetGroup,
            supersetLabel = supersetLabels[id],
            restSeconds = restSeconds,
            isFinished = isFinished,
            muscleFeel = muscleFeel,
            jointPain = jointPain,
            jointPainNote = jointPainNote,
            sets = loggedSets,
            suggestion = suggestionForNextSet(
                loggedSets = loggedSets,
                previous = previous,
                planned = plannedTargetFor(planned, position = position, nextIndex = loggedSets.size),
            ),
            lastTime = previous?.sets?.firstOrNull()?.let { first ->
                SetRow(
                    id = first.id,
                    number = 1,
                    reps = first.reps,
                    weightGrams = first.weightGrams,
                    assistanceGrams = first.assistanceGrams,
                )
            },
        )
    }

    private companion object {
        /**
         * Reported when an undo's subject has gone. `Invalid` because it is the only
         * error that carries a sentence written for the user, which is what this is
         * — the one case where no write should be attempted at all.
         */
        val GONE_FROM_SESSION = DataError.Invalid("That exercise is no longer in this workout.")

        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TICK_MILLIS = 1_000L
    }
}

/** What the screen shows once a workout is finished (ROADMAP N20). */
data class WorkoutReview(
    val note: String?,
    val readinessNote: String?,
    val totalSets: Int,
    val totalReps: Int,
    val totalVolumeGrams: Long,
    /** Only the exercises that were rated — an unrated one is not a zero. */
    val ratings: List<ExerciseRating>,
    /** The plan next to the performance; empty when the workout came from no plan. */
    val comparisons: List<PlanComparison>,
)

/** How one exercise felt, as the summary reads it back. */
data class ExerciseRating(
    val name: String,
    val muscleFeel: Int?,
    val jointPain: Int?,
)

/**
 * The review, assembled from what is already in memory (ROADMAP N20).
 *
 * A file-level function rather than a member: the class is at the function ceiling detekt
 * enforces, and this only translates the state it is given into a value — it reads nothing
 * from the ViewModel and writes nothing back, so it does not belong to it.
 */
private fun buildSummary(
    state: ActiveWorkoutUiState,
    plan: List<TemplateExercise>,
    note: String?,
): WorkoutReview {
    val actual = state.exercises.map { row ->
        ExerciseActual(
            exerciseId = row.exerciseId,
            name = row.name,
            sets = row.sets.map {
                PerformedSetSpec(
                    role = it.setType,
                    weightGrams = it.weightGrams,
                    assistanceGrams = it.assistanceGrams,
                    reps = it.reps,
                )
            },
        )
    }
    val planned = plan.map { plannedExercise ->
        ExercisePlan(
            exerciseId = plannedExercise.exerciseId,
            name = plannedExercise.exerciseName,
            sets = plannedExercise.sets.map {
                PlannedSetSpec(
                    role = it.role,
                    weightGrams = it.targetWeightGrams,
                    assistanceGrams = it.targetAssistanceGrams,
                    minReps = it.targetRepsMin,
                    maxReps = it.targetRepsMax,
                )
            },
        )
    }
    val allSets = state.exercises.flatMap { it.sets }
    return WorkoutReview(
        note = note?.takeIf { it.isNotBlank() },
        readinessNote = state.readinessNote,
        totalSets = allSets.size,
        totalReps = allSets.sumOf { it.reps },
        // The same definition the history and the database use (ROADMAP B10): added
        // weight times reps, with assistance and bodyweight contributing zero.
        totalVolumeGrams = allSets.sumOf { it.weightGrams * it.reps },
        ratings = state.exercises.mapNotNull { row ->
            val feel = row.muscleFeel
            val pain = row.jointPain
            if (feel == null && pain == null) {
                null
            } else {
                ExerciseRating(name = row.name, muscleFeel = feel, jointPain = pain)
            }
        },
        comparisons = comparePlanToActual(planned = planned, performed = actual),
    )
}

/**
 * True while this exercise is still part of the session (ROADMAP N7's undo).
 *
 * A file-level extension rather than a member: the class sits at the function ceiling
 * detekt enforces, and this is a question about the state, not about the ViewModel.
 */
private fun ActiveWorkoutUiState.hasLiveExercise(sessionExerciseId: String): Boolean =
    exercises.any { it.id == sessionExerciseId }

/**
 * True when the round this exercise is part of has nothing left behind it (ROADMAP N24).
 *
 * A grouped exercise only rests once every other member of its group has logged at least as
 * many sets, or has been marked done — a member that is finished is not going to catch up, and
 * waiting for it would leave the user resting between the two exercises the grouping exists to
 * pair. An ungrouped exercise is always its own complete round, which is the old behaviour.
 */
private fun ActiveWorkoutUiState.roundIsCompleteFor(row: SessionExerciseRow): Boolean {
    // `row` was captured before the write, and this state may not have caught up with the write
    // either — so the count comes from what this action actually logged rather than from a read
    // that can be one short. Both ways of trusting the read were live bugs: the round looked
    // finished, so the app rested between the two exercises the user had just paired.
    val logged = row.sets.size + 1

    return row.supersetGroup == null || exercises
        .filter { it.supersetGroup == row.supersetGroup && it.id != row.id }
        .all { it.isFinished || it.sets.size >= logged }
}

/**
 * `A1`, `A2` … for a grouped exercise, or null (ROADMAP N24).
 *
 * Giant-set notation, and the group letter follows the order the groups appear in the workout
 * so the first pair the user makes is always A — a label that changed as exercises moved would
 * be worse than no label.
 */
private fun supersetLabelsFor(exercises: List<SessionExercise>): Map<String, String> {
    val groups = exercises.mapNotNull { it.supersetGroup }.distinct().sorted()
    return exercises.mapNotNull { exercise ->
        val group = exercise.supersetGroup ?: return@mapNotNull null
        val letter = 'A' + groups.indexOf(group)
        if (letter !in 'A'..'Z') return@mapNotNull null
        // The member's place in its own group, in the order the workout shows them.
        val member = exercises.filter { it.supersetGroup == group }.indexOfFirst { it.id == exercise.id } + 1
        exercise.id to "$letter$member"
    }.toMap()
}

/**
 * The longest rest any member of this exercise's group prescribes, or null (ROADMAP B15).
 *
 * Null for an ungrouped exercise, and null for a group where nobody prescribes one, so the
 * caller falls back to the exercise's own rest and then to the app-wide setting.
 */
private fun ActiveWorkoutUiState.longestRestInRound(row: SessionExerciseRow): Int? {
    val group = row.supersetGroup ?: return null
    return exercises
        .filter { it.supersetGroup == group }
        .mapNotNull { it.restSeconds }
        .maxOrNull()
}
