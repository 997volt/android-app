package com.example.androidapp.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.SessionExercise
import com.example.androidapp.domain.model.WorkoutSession
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
import kotlinx.coroutines.launch

/** One exercise row on the active-workout screen. */
data class SessionExerciseRow(
    val id: String,
    val exerciseId: String,
    val name: String,
    val muscleLabel: String,
    val equipmentLabel: String,
)

data class ActiveWorkoutUiState(
    val isLoading: Boolean = true,
    val sessionId: String? = null,
    val startedAt: String = "",
    val elapsed: String = "",
    val exercises: List<SessionExerciseRow> = emptyList(),
    /** Set when a write failed, so the screen can say so instead of lying. */
    val error: DataError? = null,
) {
    /** An open session with nothing in it — the state a user can abandon. */
    val isEmpty: Boolean get() = !isLoading && sessionId != null && exercises.isEmpty()

    /** No open session at all: finished, discarded, or never started. */
    val hasNoSession: Boolean get() = !isLoading && sessionId == null
}

/**
 * Owns the active workout (ROADMAP P1.2, P1.8).
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
) : ViewModel() {

    private val lastError = MutableStateFlow<DataError?>(null)

    /** Emits true once a finish or discard succeeds, so the screen can leave. */
    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed

    /**
     * Shared so the session is not queried twice — once for the header and once
     * as the key for the exercise list.
     */
    private val activeSession = workoutRepository.observeActiveSession()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val exerciseRows: Flow<List<SessionExerciseRow>> = activeSession
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                workoutRepository.observeSessionExercises(session.id).map { rows ->
                    rows.sortedBy { it.position }.map { it.toRow() }
                }
            }
        }

    /** Drives the elapsed clock; the flow stops when nothing is subscribed. */
    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        activeSession,
        exerciseRows,
        lastError,
        ticker,
    ) { session, rows, error, _ ->
        session.toUiState(rows, error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ActiveWorkoutUiState(),
    )

    init {
        viewModelScope.launch {
            when (val result = workoutRepository.startOrResumeSession()) {
                is DataResult.Success -> lastError.value = null
                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    fun onAddExercise(exerciseId: String) = write { sessionId ->
        workoutRepository.addExercise(sessionId, exerciseId)
    }

    fun onRemoveExercise(sessionExerciseId: String) = write {
        workoutRepository.removeExercise(sessionExerciseId)
    }

    fun onFinish() = write(closeAfterwards = true) { sessionId ->
        workoutRepository.finishSession(sessionId)
    }

    fun onDiscard() = write(closeAfterwards = true) { sessionId ->
        workoutRepository.discardSession(sessionId)
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
                    if (closeAfterwards) _closed.value = true
                }

                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    private fun WorkoutSession?.toUiState(
        rows: List<SessionExerciseRow>,
        error: DataError?,
    ): ActiveWorkoutUiState = ActiveWorkoutUiState(
        isLoading = false,
        sessionId = this?.id,
        startedAt = this?.let { WorkoutFormat.clockTime(it.startedAt) }.orEmpty(),
        elapsed = this?.let { WorkoutFormat.elapsed(Duration.between(it.startedAt, timeSource.now())) }.orEmpty(),
        exercises = rows,
        error = error,
    )

    private fun SessionExercise.toRow() = SessionExerciseRow(
        id = id,
        exerciseId = exerciseId,
        name = exerciseName,
        muscleLabel = primaryMuscle.label,
        equipmentLabel = equipment.label,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TICK_MILLIS = 1_000L
    }
}
