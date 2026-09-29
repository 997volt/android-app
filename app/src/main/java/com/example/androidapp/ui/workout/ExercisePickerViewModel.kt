package com.example.androidapp.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.ExerciseSearch
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.domain.repository.WorkoutRepository
import com.example.androidapp.ui.exercises.ExerciseLibraryUiState
import com.example.androidapp.ui.exercises.toListItem
import com.example.androidapp.ui.navigation.ExercisePicker
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
 *
 * It also owns creating a custom exercise here, mid-workout, because that is
 * where the gap is felt (ROADMAP N2): the new exercise is stored and appended to
 * the session in one step, so the user never leaves the picker.
 *
 * The same screen also fills a template (ROADMAP N3). Rather than a second picker,
 * the route carries an optional `templateId` and only the *destination* of the
 * chosen exercise changes — the search, the list and the create dialog are shared,
 * which is why the two paths cannot drift apart.
 */
@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val templateRepository: TemplateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null when picking for the open session, set when picking for a template. */
    private val templateId: String? = savedStateHandle.toRoute<ExercisePicker>().templateId

    private val query = MutableStateFlow("")

    /** Emits true once the exercise is stored, so the screen can pop itself. */
    private val _added = MutableStateFlow(false)
    val added: StateFlow<Boolean> = _added

    /**
     * Set when a write failed, so the picker can say so instead of dropping it.
     *
     * Both halves of the create path can fail — the library write and the append
     * to the session — and the dialog stays open on the first so the typed name
     * is not lost.
     */
    private val _error = MutableStateFlow<DataError?>(null)
    val error: StateFlow<DataError?> = _error

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
        viewModelScope.launch { add(exerciseId) }
    }

    /**
     * Saves a custom exercise named [name] and immediately adds it to the target
     * (ROADMAP N2).
     *
     * The order matters: the exercise exists in the library before anything
     * references it, so a failure of the second step still leaves a usable
     * library entry rather than a dangling reference.
     */
    fun onCreateExercise(name: String) {
        viewModelScope.launch {
            when (val created = exerciseRepository.createCustomExercise(name)) {
                is DataResult.Success -> add(created.data.id)
                is DataResult.Failure -> _error.value = created.error
            }
        }
    }

    /** Clears a shown failure, so a dismissal does not linger on the next open. */
    fun onErrorShown() {
        _error.value = null
    }

    private suspend fun add(exerciseId: String) {
        val target = templateId?.let { id -> templateRepository.addExercise(id, exerciseId) }
            ?: addToSession(exerciseId)
        when (target) {
            is DataResult.Success -> {
                _error.value = null
                _added.value = true
            }

            is DataResult.Failure -> _error.value = target.error
        }
    }

    private suspend fun addToSession(exerciseId: String): DataResult<Unit> {
        // No active session means the picker was reached without one; reporting it
        // is better than writing a session exercise that belongs to nothing.
        val sessionId = workoutRepository.observeActiveSession().first()?.id
            ?: return DataResult.Failure(DataError.NotFound)
        return workoutRepository.addExercise(sessionId, exerciseId)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
