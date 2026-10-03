package com.example.androidapp.ui.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.PendingOccurrence
import com.example.androidapp.domain.model.ProgramSchedule
import com.example.androidapp.domain.repository.ProgramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the user asked to start, before the program's missed days are settled (ROADMAP P3.3).
 *
 * [label] is the name of the thing being started, used only to word the prompt's second
 * answer — *"continue with Bench"*. It is null for an empty workout, where there is
 * nothing to name.
 */
data class StartIntent(
    val templateId: String? = null,
    val repeatLast: Boolean = false,
    val label: String? = null,
    /**
     * The program slot the start came from, if any (ROADMAP P3.8).
     *
     * Carried through the question so a start that answers *Do it now* seeds the missed slot's
     * prescription, and one that carries on from a row seeds that row's.
     */
    val slotId: String? = null,
)

/** The missed day the prompt names, and the name of what was being started instead. */
data class SkipPromptUi(
    val missedTemplateName: String,
    val missedWeekday: DayOfWeek,
    val nextLabel: String?,
)

/**
 * The point-of-start question (ROADMAP P3.3).
 *
 * *Asked at the point of starting, not at launch*: an app that questions you when you open
 * it is one you stop opening. It intercepts a start, asks only when a scheduled day this
 * week has neither a session nor a recorded skip, and then either starts the missed day
 * (**Do it now**) or records a skip for **every** pending occurrence this week and starts
 * what was asked for (**Continue**) — asking again for the next one would turn two misses
 * into two interrogations.
 *
 * It is its own small ViewModel, shared by the home screen and the template list, because
 * both are places a workout starts and neither should own the rule.
 */
@HiltViewModel
class ProgramStartGateViewModel @Inject constructor(
    private val programs: ProgramRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    /** The question on screen, or null when there is nothing to ask. */
    private val _prompt = MutableStateFlow<SkipPromptUi?>(null)
    val prompt: StateFlow<SkipPromptUi?> = _prompt.asStateFlow()

    /**
     * The start the route should carry out, or null.
     *
     * A value the route consumes rather than a callback the ViewModel calls: navigation is
     * the route's, and a ViewModel holding one would outlive the screen it navigates.
     */
    private val _started = MutableStateFlow<StartIntent?>(null)
    val started: StateFlow<StartIntent?> = _started.asStateFlow()

    /** Set when recording the skips failed, so the route can say so (F7). */
    private val _error = MutableStateFlow<DataError?>(null)
    val error: StateFlow<DataError?> = _error.asStateFlow()

    /** Everything *Continue* has to settle, kept with the week it belongs to. */
    private var pending: Pending? = null

    private data class Pending(
        val occurrences: List<PendingOccurrence>,
        val intent: StartIntent,
        val weekStart: LocalDate,
    )

    /**
     * Asks the question if the active program has a pending occurrence this week, and
     * otherwise starts [intent] at once.
     *
     * A failed read does not block the start: the question is an interruption, and losing
     * the ability to train over a schedule lookup would be a worse failure than not asking.
     */
    fun requestStart(intent: StartIntent) {
        viewModelScope.launch {
            val now = timeSource.now()
            val zone = ZoneId.systemDefault()
            when (val result = programs.pendingOccurrences(now.atZone(zone).toLocalDate(), zone)) {
                is DataResult.Success -> {
                    val missed = result.data
                    if (missed.isEmpty()) {
                        _started.value = intent
                    } else {
                        pending = Pending(
                            occurrences = missed,
                            intent = intent,
                            weekStart = ProgramSchedule.weekStartOf(now.atZone(zone).toLocalDate()),
                        )
                        _prompt.value = SkipPromptUi(
                            missedTemplateName = missed.first().templateName,
                            missedWeekday = missed.first().weekday,
                            nextLabel = intent.label,
                        )
                    }
                }

                is DataResult.Failure -> _started.value = intent
            }
        }
    }

    /** Starts the missed day instead, which is what resolves its occurrence. */
    fun onDoItNow() {
        val current = pending ?: return
        clearPrompt()
        val missed = current.occurrences.first()
        _started.value = StartIntent(
            templateId = missed.templateId,
            label = missed.templateName,
            // The missed slot is the one being started, so its own prescription seeds it (P3.8).
            slotId = missed.slotId,
        )
    }

    /**
     * Records a skip for **every** pending occurrence this week, then starts what was asked
     * for.
     *
     * The workout is the point, so a failed skip write reports and still proceeds: the
     * consequence is being asked again, which is honest, while refusing to start would not be.
     */
    fun onContinue() {
        val current = pending ?: return
        clearPrompt()
        viewModelScope.launch {
            val recorded = programs.skipOccurrences(
                slotIds = current.occurrences.map { it.slotId },
                weekStart = current.weekStart,
            )
            if (recorded is DataResult.Failure) _error.value = recorded.error
            _started.value = current.intent
        }
    }

    /** Backing out of the question cancels the start rather than choosing for the user. */
    fun onDismiss() {
        clearPrompt()
    }

    /** Called once the route has navigated, so a recomposition cannot start twice. */
    fun onStartHandled() {
        _started.value = null
    }

    fun onErrorShown() {
        _error.value = null
    }

    private fun clearPrompt() {
        pending = null
        _prompt.value = null
    }
}
