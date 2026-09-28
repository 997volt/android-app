package com.example.androidapp.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.repository.ExerciseRepository
import com.example.androidapp.ui.navigation.ExerciseDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.navigation.toRoute

data class ExerciseDetailUiState(
    val exercise: Exercise? = null,
    val isLoading: Boolean = true,
) {
    /** Loaded, but no such exercise — a real state, not an error to hide. */
    val notFound: Boolean get() = !isLoading && exercise == null
}

/**
 * Loads one exercise for the detail screen.
 *
 * The id comes out of [SavedStateHandle] via the type-safe route, so the
 * ViewModel is recreated correctly after process death with the same argument.
 */
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    repository: ExerciseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: String = savedStateHandle.toRoute<ExerciseDetail>().exerciseId

    val uiState: StateFlow<ExerciseDetailUiState> =
        flow { emit(repository.getExercise(exerciseId)) }
            .map { ExerciseDetailUiState(exercise = it, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ExerciseDetailUiState(),
            )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
