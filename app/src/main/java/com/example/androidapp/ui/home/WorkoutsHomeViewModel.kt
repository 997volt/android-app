package com.example.androidapp.ui.home

import java.time.ZoneId
import java.time.DayOfWeek
import com.example.androidapp.domain.repository.ProgramRepository
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.WorkoutProgram
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

/**
 * One row of today's plan (ROADMAP P3.3).
 *
 * [id] is the row's own identity — a program slot's id when a program is active, a
 * template's id under the weekday pins. It is deliberately not [templateId]: a program may
 * put the same template in two slots, and a list keyed by template would collide.
 */
data class TodayPlan(
    val id: String,
    val templateId: String,
    val name: String,
    val exerciseCount: Int,
)

data class WorkoutsHomeUiState(
    val isLoading: Boolean = true,
    val recent: List<WorkoutSummary> = emptyList(),
    val activeWorkout: ActiveWorkoutInfo? = null,
    /** The device's weekday, for the "Today" heading (ROADMAP N16). */
    val today: DayOfWeek = DayOfWeek.MONDAY,
    /**
     * What is scheduled today: the active program's slots for this weekday, or — with no
     * program active — the plans pinned to it (ROADMAP P3.3, falling back to N16).
     */
    val todaysPlan: List<TodayPlan> = emptyList(),
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
    programRepository: ProgramRepository,
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

    /**
     * The active program, and its slots (ROADMAP P3.3).
     *
     * A program's slots are read only while one is active, so a shelf full of programs costs
     * the home screen nothing. With none active the plan falls back to the pins below.
     */
    private val activeProgram: Flow<WorkoutProgram?> = programRepository.observeActiveProgram()

    private val activeProgramSlots: Flow<List<ProgramSlot>> = activeProgram
        .flatMapLatest { program ->
            if (program == null) flowOf(emptyList()) else programRepository.observeSlots(program.id)
        }

    val uiState: StateFlow<WorkoutsHomeUiState> = combine(
        workoutRepository.observeHistory(),
        activeWorkout,
        templateRepository.observeTemplates(),
        activeProgram,
        activeProgramSlots,
    ) { history, workout, templates, program, slots ->
        WorkoutsHomeUiState(
            isLoading = false,
            recent = history.take(RECENT_LIMIT),
            // `history` is newest-first, so its head is the workout a repeat would copy — the same
            // rule the repeat query applies, answered from the row the screen is already showing.
            canRepeatLast = history.firstOrNull()?.isRepeatable == true,
            activeWorkout = workout,
            today = today,
            todaysPlan = todaysPlanFor(
                program = program,
                slots = slots,
                templates = templates,
                day = today,
            ),
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

/**
 * What is scheduled for [day] (ROADMAP P3.3).
 *
 * With a program active the program is the schedule, even on a day it schedules nothing —
 * an empty day is rest, not a fallback. Only **no active program** returns the N16 pins,
 * which is the rule the roadmap states and the reason the pins stay editable.
 *
 * File-level and pure so the fallback can be tested without a database or a ViewModel.
 */
internal fun todaysPlanFor(
    program: WorkoutProgram?,
    slots: List<ProgramSlot>,
    templates: List<WorkoutTemplate>,
    day: DayOfWeek,
): List<TodayPlan> = if (program != null) slots.scheduledFor(day) else templates.pinnedFor(day)

/**
 * The plans pinned to [day], as home rows — the fallback when no program is active
 * (ROADMAP N16, kept by P3.3).
 */
private fun List<WorkoutTemplate>.pinnedFor(day: DayOfWeek): List<TodayPlan> =
    filter { it.weekday == day }.map { template ->
        TodayPlan(
            id = template.id,
            templateId = template.id,
            name = template.name,
            exerciseCount = template.exerciseCount,
        )
    }

/**
 * A program's slots that fall on [day], in the program's own order (ROADMAP P3.3).
 *
 * The row's identity is the slot's, not the template's: a program may schedule the same
 * workout twice, and two rows keyed by one template would collide.
 */
private fun List<ProgramSlot>.scheduledFor(day: DayOfWeek): List<TodayPlan> =
    filter { it.weekday == day }
        // The repository already orders by position; sorting here too means the rule is the
        // function's rather than its caller's.
        .sortedBy { it.position }
        .map { slot ->
        TodayPlan(
            id = slot.id,
            templateId = slot.templateId,
            name = slot.templateName,
            exerciseCount = slot.exerciseCount,
        )
    }
