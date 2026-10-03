package com.example.androidapp.ui.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.domain.repository.ProgramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgramsUiState(
    val isLoading: Boolean = true,
    val programs: List<WorkoutProgram> = emptyList(),
    /** Set when a write failed, so the screen can say so instead of lying. */
    val error: DataError? = null,
)

/**
 * The program list (ROADMAP P3.3).
 *
 * Creating a program takes a name from the screen — a ViewModel cannot read a string
 * resource — and then reports the new id so the list can open its editor, which is where
 * its slots are added.
 */
@HiltViewModel
class ProgramsViewModel @Inject constructor(
    private val repository: ProgramRepository,
) : ViewModel() {

    private val error = MutableStateFlow<DataError?>(null)

    /** Emits the id of a just-created program, so the list can open its editor. */
    private val _created = MutableStateFlow<String?>(null)
    val createdProgramId: StateFlow<String?> = _created

    val uiState: StateFlow<ProgramsUiState> = combine(
        repository.observePrograms(),
        error,
    ) { programs, currentError ->
        ProgramsUiState(isLoading = false, programs = programs, error = currentError)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ProgramsUiState(),
    )

    fun onCreateProgram(name: String) {
        viewModelScope.launch {
            when (val result = repository.createProgram(name)) {
                is DataResult.Success -> {
                    error.value = null
                    _created.value = result.data
                }

                is DataResult.Failure -> error.value = result.error
            }
        }
    }

    /** Starts following this program, without stopping any other (P3.12). */
    fun onSetActive(programId: String) {
        viewModelScope.launch {
            error.value = (repository.activateProgram(programId) as? DataResult.Failure)?.error
        }
    }

    /** [delta] -1 moves the program up, +1 down (P3.12). */
    fun onMoveProgram(programId: String, delta: Int) {
        viewModelScope.launch {
            error.value = (repository.moveProgram(programId, delta) as? DataResult.Failure)?.error
        }
    }

    /** Called once the screen has navigated, so a recomposition cannot re-open it. */
    fun onCreatedHandled() {
        _created.value = null
    }

    fun onErrorShown() {
        error.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
