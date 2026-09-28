package com.example.androidapp.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.ExerciseSearch
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.domain.repository.WorkoutRepository
import com.example.androidapp.ui.exercises.ExerciseLibraryUiState
import com.example.androidapp.ui.exercises.toListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the exercise picker shown over an active workout.
 *
 * It resolves the session itself from the repository rather than receiving it
 * through navigation arguments. The open session is already the single source of
 * truth for "which workout am I in", so passing an id through the back stack
 * would just create a second, staleable copy of that fact.
 */
@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /** Emits true once the exercise is stored, so the screen can pop itself. */
    private val _added = MutableStateFlow(false)
    val added: StateFlow<Boolean> = _added

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        exerciseRepository.observeExercises(),
        query,
    ) { exercises, currentQuery ->
        ExerciseLibraryUiState(
            query = currentQuery,
            items = ExerciseSearch.filter(exercises, currentQuery).map { it.toListItem() },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ExerciseLibraryUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onExerciseSelected(exerciseId: String) {
        viewModelScope.launch {
            val sessionId = workoutRepository.observeActiveSession().first()?.id ?: return@launch
            if (workoutRepository.addExercise(sessionId, exerciseId) is DataResult.Success) {
                _added.value = true
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
