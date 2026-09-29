package com.example.androidapp.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.ui.navigation.ExerciseDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the detail screen's edit form produces (ROADMAP N2, N5).
 *
 * Deliberately a screen-level draft rather than an [Exercise]: the form edits the
 * attributes a user can change, and secondary muscles or `isCustom` are not among
 * them, so passing a whole exercise around would invite editing the rest by
 * accident.
 */
data class ExerciseEdit(
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val movementPattern: MovementPattern,
    /** The exercise's own rest, or null for the app default (ROADMAP N5). */
    val restSeconds: Int? = null,
    /** A cue to read while lifting, or null (ROADMAP N5). */
    val techniqueNote: String? = null,
)

data class ExerciseDetailUiState(
    val exercise: Exercise? = null,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val error: DataError? = null,
) {
    /** Loaded, but no such exercise — a real state, not an error to hide. */
    val notFound: Boolean get() = !isLoading && exercise == null

    /**
     * Any exercise can be corrected here (ROADMAP N5). Seeded rows included, and
     * that is safe: the seeder tops up with `INSERT OR IGNORE` and never updates an
     * existing row, so an edit survives every future top-up.
     */
    val canEdit: Boolean get() = !isLoading && !isEditing && exercise != null
}

/**
 * Loads one exercise for the detail screen, and saves edits to it.
 *
 * The id comes out of [SavedStateHandle] via the type-safe route, so the
 * ViewModel is recreated correctly after process death with the same argument.
 */
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val repository: ExerciseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: String = savedStateHandle.toRoute<ExerciseDetail>().exerciseId

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // A plain one-shot read, held in state so a successful edit is
            // reflected without a second round trip. Reads can still throw out of
            // the repository (the quality-bar gap the roadmap records); this is
            // the previous behaviour in a coroutine, not a new failure mode.
            _uiState.update {
                it.copy(exercise = repository.getExercise(exerciseId), isLoading = false)
            }
        }
    }

    fun onEdit() {
        _uiState.update { it.copy(isEditing = true, error = null) }
    }

    fun onCancelEdit() {
        _uiState.update { it.copy(isEditing = false, error = null) }
    }

    /**
     * Writes the edited attributes, keeping the row's id, secondary muscles and
     * custom flag. Leaving edit mode is driven by the write, so a failure keeps
     * the form open with the user's input rather than pretending it saved.
     */
    fun onSave(edit: ExerciseEdit) {
        val current = _uiState.value.exercise ?: return
        viewModelScope.launch {
            val updated = current.copy(
                name = edit.name.trim(),
                primaryMuscle = edit.primaryMuscle,
                equipment = edit.equipment,
                movementPattern = edit.movementPattern,
                restSeconds = edit.restSeconds,
                techniqueNote = edit.techniqueNote,
            )
            when (val result = repository.updateExercise(updated)) {
                is DataResult.Success -> _uiState.update {
                    it.copy(exercise = updated, isEditing = false, error = null)
                }

                is DataResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }
}
