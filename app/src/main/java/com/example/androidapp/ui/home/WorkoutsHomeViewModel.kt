package com.example.androidapp.ui.home

import java.time.ZoneId
import java.time.DayOfWeek
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.domain.model.WorkoutTemplate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.model.WorkoutSummary
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * How many recent workouts the home screen shows (ROADMAP N1).
 *
 * A home-sized view, not the history screen: enough to answer "where was I", with a
 * link through for the rest. Five fits on one screenful on a small phone without
 * scrolling past the button that matters.
 */
private const val RECENT_LIMIT = 5

/**
 * A workout already in progress.
 *
 * Holds no elapsed time: that ticks, and folding a ticking value into this state
 * would rebuild the list below it once a second — the trap F16 fixed on the workout
 * screen. The clock is a separate flow the button alone reads.
 */
data class ActiveWorkoutInfo(
    val startedAt: Instant,
    val exerciseCount: Int,
)

data class WorkoutsHomeUiState(
    val isLoading: Boolean = true,
    val recent: List<WorkoutSummary> = emptyList(),
    val activeWorkout: ActiveWorkoutInfo? = null,
    /** The device's weekday, for the "Today" heading (ROADMAP N16). */
    val today: DayOfWeek = DayOfWeek.MONDAY,
    /** Plans pinned to today, in the repository's order. */
    val todaysPlans: List<WorkoutTemplate> = emptyList(),
    /** Whether repeating the last workout would copy something (ROADMAP B43's tail). */
    val canRepeatLast: Boolean = false,
) {
    /**
     * Nothing logged and nothing running — the first-run case, which should point at
     * Start rather than showing an empty list with no explanation.
     */
    val isFirstRun: Boolean get() = !isLoading && recent.isEmpty() && activeWorkout == null
}

/**
 * The home screen (ROADMAP N1).
 *
 * Replaces the exercise library as the start destination. The library is a
 * *reference* — you visit it to look something up — so opening the app on it put a
 * catalogue in front of the thing the user came to do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutsHomeViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    templateRepository: TemplateRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val activeSession = workoutRepository.observeActiveSession()

    private val activeWorkout: Flow<ActiveWorkoutInfo?> = activeSession
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                workoutRepository.observeSessionExercises(session.id).map { exercises ->
                    ActiveWorkoutInfo(startedAt = session.startedAt, exerciseCount = exercises.size)
                }
            }
        }

    /**
     * What today's plan is, by the device's own calendar (ROADMAP N16).
     *
     * The day is read once per composition of this flow rather than recomputed on every
     * emission: a phone left open across midnight is a rounding error, and re-reading a
     * clock inside a `combine` would make the state unstable for no gain.
     */
    private val today: DayOfWeek = timeSource.now()
        .atZone(ZoneId.systemDefault())
        .dayOfWeek

    val uiState: StateFlow<WorkoutsHomeUiState> = combine(
        workoutRepository.observeHistory(),
        activeWorkout,
        templateRepository.observeTemplates(),
    ) { history, workout, templates ->
        WorkoutsHomeUiState(
            isLoading = false,
            recent = history.take(RECENT_LIMIT),
            // `history` is newest-first, so its head is the workout a repeat would copy — the same
            // rule the repeat query applies, answered from the row the screen is already showing.
            canRepeatLast = history.firstOrNull()?.isRepeatable == true,
            activeWorkout = workout,
            today = today,
            todaysPlans = templates.filter { it.weekday == today },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = WorkoutsHomeUiState(),
    )

    /**
     * Ticks once a second, and **only while a workout is running**: an idle home
     * screen should not hold a timer open for a button that says "Start workout".
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

    private fun elapsedSince(session: WorkoutSession): String =
        WorkoutFormat.elapsed(Duration.between(session.startedAt, timeSource.now()))

    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TICK_MILLIS = 1_000L
    }
}
