package com.example.androidapp.ui.measurements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.BodyMeasurement
import com.example.androidapp.domain.repository.MeasurementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the measurements screen renders (ROADMAP N32). */
data class MeasurementsUiState(
    val isLoading: Boolean = true,
    /** Entries, newest first — the order the list and the chart both read in. */
    val entries: List<BodyMeasurement> = emptyList(),
    val error: DataError? = null,
)

/**
 * The measurements screen's state (ROADMAP N32).
 *
 * Small on purpose: the rules are the repository's — one entry to a day, an unmeasured site left blank —
 * and this only carries what was typed to it and surfaces what came back.
 */
@HiltViewModel
class MeasurementsViewModel @Inject constructor(
    private val repository: MeasurementRepository,
) : ViewModel() {

    private val lastError = MutableStateFlow<DataError?>(null)

    val uiState: StateFlow<MeasurementsUiState> = repository.observeAll()
        .map { entries -> MeasurementsUiState(isLoading = false, entries = entries) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MeasurementsUiState(),
        )

    val error: StateFlow<DataError?> = lastError.asStateFlow()

    /**
     * Saves an entry.
     *
     * An entry with a blank id is a new one, placed by its day; one with an id edits that row. Which is
     * which is the repository's decision to make, since it is the one that can see the day.
     */
    fun onSave(measurement: BodyMeasurement) {
        viewModelScope.launch {
            when (val result = repository.save(measurement)) {
                is DataResult.Success -> lastError.value = null
                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    fun onDelete(id: String) {
        viewModelScope.launch {
            when (val result = repository.delete(id)) {
                is DataResult.Success -> lastError.value = null
                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    fun onDismissError() {
        lastError.value = null
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
