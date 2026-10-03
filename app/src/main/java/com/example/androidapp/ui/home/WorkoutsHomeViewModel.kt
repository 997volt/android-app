package com.example.androidapp.ui.home

import java.time.ZoneId
import java.time.DayOfWeek
import java.time.LocalDate
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.repository.ProgramRepository
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.domain.model.ProgramRun
import com.example.androidapp.domain.model.ProgramSchedule
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
 *
 * [slotId] is set only for a program slot, and it is what carries the slot's prescription into
 * the workout (P3.8): the template is what is started, the slot is what it was scheduled as.
 */
data class TodayPlan(
    val id: String,
    val templateId: String,
    val name: String,
    val exerciseCount: Int,
    val slotId: String? = null,
)

/**
 * A program's run, offered as home's next-up row (ROADMAP P3.9).
 *
 * [plan] is what a start needs — the slot's id travels with it so the slot's prescription seeds
 * the workout (P3.8) — and [programName] is there because more than one program may be active
 * (P3.12), so two next-up rows have to be tellable apart.
 */
data class NextUp(
    val plan: TodayPlan,
    val programName: String,
    /** True when nothing has been done yet, so the row words itself as a start (P3.9). */
    val isAtStart: Boolean,
)

data class WorkoutsHomeUiState(
    val isLoading: Boolean = true,
    val recent: List<WorkoutSummary> = emptyList(),
    val activeWorkout: ActiveWorkoutInfo? = null,
    /** The device's weekday, for the "Today" heading (ROADMAP N16). */
    val today: DayOfWeek = DayOfWeek.MONDAY,
    /**
     * What is scheduled today: the union of the active programs' slots for this weekday, or —
     * with no program active — the plans pinned to it (ROADMAP P3.3, unioned by P3.12).
     */
    val todaysPlan: List<TodayPlan> = emptyList(),
    /**
     * Where each active program's run is, for a program with nothing scheduled today (ROADMAP P3.9).
     *
     * Empty when every active program has a weekday slot today: the today plan is then the answer,
     * and a next-up row would be a second, contradictory one.
     */
    val nextUp: List<NextUp> = emptyList(),
    /** The templates a substitution can choose from (ROADMAP P3.11). */
    val templates: List<WorkoutTemplate> = emptyList(),
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
    private val programRepository: ProgramRepository,
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
     * Every program home follows, in the authored order (ROADMAP P3.12).
     *
     * A list rather than one row: more than one program may be active, and the union of their
     * slots is today's plan. Empty is the state the pins below answer in.
     */
    private val activePrograms: Flow<List<WorkoutProgram>> = programRepository.observeActivePrograms()

    /**
     * The active programs' slots, concatenated in program order (ROADMAP P3.12).
     *
     * Read only while at least one program is active, so a shelf full of programs costs the
     * home screen nothing. The order is the programs' authored order, each program's own slots
     * in their own order; [todaysPlanFor] re-states that ordering rather than trusting it.
     */
    private val activeProgramSlots: Flow<List<ProgramSlot>> = activePrograms
        .flatMapLatest { programs ->
            if (programs.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(programs.map { program -> programRepository.observeSlots(program.id) }) { perProgram ->
                    perProgram.toList().flatten()
                }
            }
        }

    /** Each active program's run, keyed by program id (ROADMAP P3.9). */
    private val programRuns: Flow<Map<String, ProgramRun?>> = activePrograms
        .flatMapLatest { programs ->
            if (programs.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    programs.map { program ->
                        programRepository.observeProgramRun(program.id).map { run -> program.id to run }
                    },
                ) { perProgram -> perProgram.toMap() }
            }
        }

    /** The three program-shaped sources, kept together so the combine below stays four deep. */
    private data class ProgramsPart(
        val programs: List<WorkoutProgram>,
        val slots: List<ProgramSlot>,
        val runs: Map<String, ProgramRun?>,
    )

    private val programsPart: Flow<ProgramsPart> =
        combine(activePrograms, activeProgramSlots, programRuns) { programs, slots, runs ->
            ProgramsPart(programs, slots, runs)
        }

    val uiState: StateFlow<WorkoutsHomeUiState> = combine(
        workoutRepository.observeHistory(),
        activeWorkout,
        templateRepository.observeTemplates(),
        programsPart,
    ) { history, workout, templates, programs ->
        WorkoutsHomeUiState(
            isLoading = false,
            recent = history.take(RECENT_LIMIT),
            // `history` is newest-first, so its head is the workout a repeat would copy — the same
            // rule the repeat query applies, answered from the row the screen is already showing.
            canRepeatLast = history.firstOrNull()?.isRepeatable == true,
            activeWorkout = workout,
            today = today,
            todaysPlan = todaysPlanFor(
                programs = programs.programs,
                slots = programs.slots,
                templates = templates,
                day = today,
            ),
            nextUp = nextUpFor(
                programs = programs.programs,
                slots = programs.slots,
                runs = programs.runs,
                day = today,
            ),
            templates = templates,
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

    /**
     * Records that one occurrence is trained with a different workout, or clears it (ROADMAP P3.11).
     *
     * The week is the current one, taken from the device's own clock: the substitute is chosen at
     * the point of starting, so "which week" means the week being started now. [templateId] null
     * restores the slot's own workout.
     */
    suspend fun setSubstitution(slotId: String, templateId: String?): DataResult<Unit> =
        programRepository.setSubstitution(
            slotId = slotId,
            weekStart = ProgramSchedule.weekStartOf(todayDate()),
            templateId = templateId,
        )

    private fun todayDate(): LocalDate = timeSource.now().atZone(ZoneId.systemDefault()).toLocalDate()

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
 * What is scheduled for [day] (ROADMAP P3.3, unioned by P3.12).
 *
 * With anything active the union of the active programs is the schedule, even on a day they
 * schedule nothing — an empty day is rest, not a fallback. Only **no active program** returns
 * the N16 pins, which is the rule the roadmap states and the reason the pins stay editable.
 *
 * File-level and pure so the union and the fallback can be tested without a database or a
 * ViewModel.
 */
internal fun todaysPlanFor(
    programs: List<WorkoutProgram>,
    slots: List<ProgramSlot>,
    templates: List<WorkoutTemplate>,
    day: DayOfWeek,
): List<TodayPlan> = if (programs.isNotEmpty()) {
    slots.scheduledFor(day, programs)
} else {
    templates.pinnedFor(day)
}

/**
 * Where each active program's run is, as home's next-up rows (ROADMAP P3.9).
 *
 * A program with a weekday slot today is left out: the today plan already says what to do, and a
 * next-up row beside it would be a second, contradictory answer. The order follows the programs'
 * authored order (P3.12), so several rows read the way the union does.
 *
 * File-level and pure so the rule can be tested without a database or a ViewModel.
 */
internal fun nextUpFor(
    programs: List<WorkoutProgram>,
    slots: List<ProgramSlot>,
    runs: Map<String, ProgramRun?>,
    day: DayOfWeek,
): List<NextUp> = programs.mapNotNull { program ->
    val programSlots = slots.filter { it.programId == program.id }
    if (programSlots.any { it.weekday == day }) return@mapNotNull null
    val run = runs[program.id] ?: return@mapNotNull null
    NextUp(
        plan = TodayPlan(
            id = run.slot.id,
            templateId = run.slot.templateId,
            name = run.slot.templateName,
            exerciseCount = run.slot.exerciseCount,
            // The slot travels with the start, so its prescription seeds the workout (P3.8).
            slotId = run.slot.id,
        ),
        programName = program.name,
        isAtStart = run.isAtStart,
    )
}

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
            // A pin is not a slot, so there is no prescription to carry (P3.8).
            slotId = null,
        )
    }

/**
 * The active programs' slots that fall on [day], in the union's order (ROADMAP P3.12).
 *
 * Ordered by the **program's authored position first, the slot's position second**: two
 * programs each number their slots from zero, so ordering by the slot alone would interleave
 * them. The row's identity is the slot's, not the template's: a program may schedule the same
 * workout twice, and two rows keyed by one template would collide.
 */
private fun List<ProgramSlot>.scheduledFor(
    day: DayOfWeek,
    programs: List<WorkoutProgram>,
): List<TodayPlan> {
    val programOrder = programs.withIndex().associate { (index, program) -> program.id to index }
    return filter { it.weekday == day }
        .sortedWith(
            compareBy(
                { programOrder[it.programId] ?: Int.MAX_VALUE },
                { it.position },
            ),
        )
        .map { slot ->
            TodayPlan(
                id = slot.id,
                templateId = slot.templateId,
                name = slot.templateName,
                exerciseCount = slot.exerciseCount,
                slotId = slot.id,
            )
        }
}
