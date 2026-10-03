package com.example.androidapp.ui.adherence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.MonthAdherence
import com.example.androidapp.domain.model.ProgramSchedule
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.domain.repository.AdherenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Adherence screen shows (ROADMAP P3.5, P3.10).
 *
 * [month] is the month the grid draws and the ratio covers, so the two cannot disagree about
 * what they are counting; [currentMonth] is what stops the calendar walking into the future.
 * [hasActiveProgram] is why the ratio can be absent for two different reasons, which the screen
 * words differently: a program was never followed, or the month scheduled nothing.
 *
 * [markableWeeks] are the weeks of [month] that have started, which is the range a deload can be
 * marked over: the app has no forward view to hang a future week on, and a deload is decided by
 * how the block is going (P3.10).
 */
data class AdherenceUiState(
    val month: YearMonth,
    val currentMonth: YearMonth,
    val isLoading: Boolean = true,
    val hasActiveProgram: Boolean = true,
    val adherence: MonthAdherence = MonthAdherence(),
    /** The active programs, so a deload toggle can say which program's week it marks (P3.10). */
    val programs: List<WorkoutProgram> = emptyList(),
    /** Which weeks each program marked as a deload, by program id (P3.10). */
    val deloadWeeks: Map<String, Set<LocalDate>> = emptyMap(),
    /** The started weeks of [month], in order, that can be marked (P3.10). */
    val markableWeeks: List<LocalDate> = emptyList(),
    val error: DataError? = null,
) {
    /** False on the current month: the calendar navigates back through history, never forward. */
    val canGoForward: Boolean get() = month.isBefore(currentMonth)
}

/** The Monday-start weeks of [month] that have started by [today] (ROADMAP P3.10). */
internal fun markableWeeksOf(month: YearMonth, today: LocalDate): List<LocalDate> {
    val first = ProgramSchedule.weekStartOf(month.atDay(1))
    val last = ProgramSchedule.weekStartOf(month.atEndOfMonth())
    val currentWeek = ProgramSchedule.weekStartOf(today)
    return generateSequence(first) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(last) }
        .filter { !it.isAfter(currentWeek) }
        .toList()
}

/**
 * The month of adherence, one read per shown month (ROADMAP P3.5).
 *
 * The read is keyed by the month rather than held as a stream: what it answers is a question
 * about a window, and the window is the screen's own state. Every read remembers the day it was
 * taken on, so a month left open across midnight stops scoring today as a miss.
 *
 * A deload write re-reads the shown month (P3.10): the ratio and the toggle have to agree, and one
 * is derived from the other's rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdherenceViewModel @Inject constructor(
    private val adherence: AdherenceRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val shown = MutableStateFlow(currentMonth())

    /** Bumped after a deload write, so the shown month is read again. */
    private val refresh = MutableStateFlow(0)

    /** A write failure, kept apart from a read failure so it clears on the next action. */
    private val writeError = MutableStateFlow<DataError?>(null)

    private val shownMonth = combine(shown, refresh) { month, _ -> month }

    val uiState: StateFlow<AdherenceUiState> = combine(
        shownMonth.mapLatest { month -> load(month) },
        writeError,
    ) { loaded, failure ->
        loaded.copy(error = failure ?: loaded.error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AdherenceUiState(month = shown.value, currentMonth = shown.value),
    )

    /** Back through history, a month at a time. There is no lower bound worth inventing one for. */
    fun onPreviousMonth() {
        shown.value = shown.value.minusMonths(1)
    }

    /** Forward only up to the month it is now: a month that has not happened cannot be scored. */
    fun onNextMonth() {
        val next = shown.value.plusMonths(1)
        if (!next.isAfter(currentMonth())) shown.value = next
    }

    /**
     * Marks or unmarks one program's week as a deload (ROADMAP P3.10).
     *
     * The week is one the screen offered, which is one that has started: a week that has not begun
     * cannot be marked, because the app has no forward view and a deload is decided by how the
     * block is going.
     */
    fun onToggleDeload(programId: String, weekStart: LocalDate, marked: Boolean) {
        viewModelScope.launch {
            when (val result = adherence.setDeloadWeek(programId, weekStart, marked)) {
                is DataResult.Success -> {
                    writeError.value = null
                    refresh.update { it + 1 }
                }

                is DataResult.Failure -> writeError.value = result.error
            }
        }
    }

    fun onErrorShown() {
        writeError.value = null
    }

    private suspend fun load(month: YearMonth): AdherenceUiState {
        val zone = ZoneId.systemDefault()
        val today = today(zone)
        return when (val result = adherence.monthAdherence(month, today, zone)) {
            is DataResult.Success -> AdherenceUiState(
                month = month,
                currentMonth = YearMonth.from(today),
                isLoading = false,
                hasActiveProgram = result.data.hasActiveProgram,
                adherence = result.data.adherence,
                programs = result.data.programs,
                deloadWeeks = result.data.deloadWeeks,
                markableWeeks = markableWeeksOf(month, today),
            )

            is DataResult.Failure -> AdherenceUiState(
                month = month,
                currentMonth = YearMonth.from(today),
                isLoading = false,
                error = result.error,
            )
        }
    }

    private fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        timeSource.now().atZone(zone).toLocalDate()

    private fun currentMonth(): YearMonth = YearMonth.from(today())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
