package com.example.androidapp.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.ExerciseSearch
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.domain.repository.WorkoutRepository
import com.example.androidapp.ui.workout.WorkoutClock
import com.example.androidapp.ui.workout.WorkoutFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
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
import kotlinx.coroutines.flow.stateIn

/** One row of the library list, flattened so the composable does no domain work. */
data class ExerciseListItem(
    val id: String,
    val name: String,
    val muscleLabel: String,
    val equipmentLabel: String,
)

/**
 * A workout already in progress (ROADMAP P1.16).
 *
 * Deliberately holds no elapsed time: that ticks, and folding a ticking value into
 * this state would rebuild the list below it once a second — the trap F16 fixed on
 * the workout screen. The clock is a separate flow that only the button reads.
 */
data class ActiveWorkoutInfo(
    val startedAt: Instant,
    val exerciseCount: Int,
)

data class ExerciseLibraryUiState(
    val query: String = "",
    val items: List<ExerciseListItem> = emptyList(),
    val isLoading: Boolean = true,
    val activeWorkout: ActiveWorkoutInfo? = null,
) {
    /**
     * A search that matched nothing — deliberately distinct from [isLoading] so
     * the UI shows "no results" rather than a spinner that never resolves.
     */
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

/**
 * Holds the library's search state, the filtered list, and whether a workout is
 * already running (ROADMAP F3, P1.16).
 *
 * Backing out of a workout leaves the session active in the database. Without this
 * the library screen gave no sign of it and its button still read "Start workout",
 * which quietly undid P1.8's crash recovery on the ordinary path — the one users
 * actually take.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository,
    workoutRepository: WorkoutRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val activeSession = workoutRepository.observeActiveSession()

    private val workoutInfo: Flow<ActiveWorkoutInfo?> = activeSession
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                workoutRepository.observeSessionExercises(session.id).map { exercises ->
                    ActiveWorkoutInfo(startedAt = session.startedAt, exerciseCount = exercises.size)
                }
            }
        }

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        exerciseRepository.observeExercises(),
        query,
        workoutInfo,
    ) { exercises, currentQuery, workout ->
        ExerciseLibraryUiState(
            query = currentQuery,
            items = ExerciseSearch.filter(exercises, currentQuery).map { it.toListItem() },
            isLoading = false,
            activeWorkout = workout,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ExerciseLibraryUiState(),
    )

    /**
     * Ticks once a second, and **only while a workout is running**: an idle library
     * screen should not hold a one-second timer open for a button that says
     * "Start workout".
     */
    val clock: StateFlow<WorkoutClock> = activeSession
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(WorkoutClock())
            } else {
                ticker.map { WorkoutClock(elapsed = elapsedSince(session)) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = WorkoutClock(),
        )

    fun onQueryChange(value: String) {
        query.value = value
    }

    private fun elapsedSince(session: WorkoutSession): String =
        WorkoutFormat.elapsed(Duration.between(session.startedAt, timeSource.now()))

    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    private companion object {
        /** Keeps the upstream flow warm across a configuration change. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TICK_MILLIS = 1_000L
    }
}

/** Shared with the workout screens' exercise picker, which renders the same rows. */
internal fun Exercise.toListItem() = ExerciseListItem(
    id = id,
    name = name,
    muscleLabel = primaryMuscle.label,
    equipmentLabel = equipment.label,
)
