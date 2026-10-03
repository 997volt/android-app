package com.example.androidapp.ui.adherence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.MonthAdherence
import com.example.androidapp.domain.repository.ProgramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * What the Adherence screen shows (ROADMAP P3.5).
 *
 * [month] is the month the grid draws and the ratio covers, so the two cannot disagree about
 * what they are counting; [currentMonth] is what stops the calendar walking into the future.
 * [hasActiveProgram] is why the ratio can be absent for two different reasons, which the screen
 * words differently: a program was never followed, or the month scheduled nothing.
 */
data class AdherenceUiState(
    val month: YearMonth,
    val currentMonth: YearMonth,
    val isLoading: Boolean = true,
    val hasActiveProgram: Boolean = true,
    val adherence: MonthAdherence = MonthAdherence(),
    val error: DataError? = null,
) {
    /** False on the current month: the calendar navigates back through history, never forward. */
    val canGoForward: Boolean get() = month.isBefore(currentMonth)
}

/**
 * The month of adherence, one read per shown month (ROADMAP P3.5).
 *
 * The read is keyed by the month rather than held as a stream: what it answers is a question
 * about a window, and the window is the screen's own state. Every read remembers the day it was
 * taken on, so a month left open across midnight stops scoring today as a miss.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdherenceViewModel @Inject constructor(
    private val programs: ProgramRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val shown = MutableStateFlow(currentMonth())

    val uiState: StateFlow<AdherenceUiState> = shown
        .mapLatest { month -> load(month) }
        .stateIn(
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

    private suspend fun load(month: YearMonth): AdherenceUiState {
        val zone = ZoneId.systemDefault()
        val today = today(zone)
        return when (val result = programs.monthAdherence(month, today, zone)) {
            is DataResult.Success -> AdherenceUiState(
                month = month,
                currentMonth = YearMonth.from(today),
                isLoading = false,
                hasActiveProgram = result.data.hasActiveProgram,
                adherence = result.data.adherence,
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
