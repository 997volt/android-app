package com.example.androidapp.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.ExerciseSearch
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One row of the library list, flattened so the composable does no domain work. */
data class ExerciseListItem(
    val id: String,
    val name: String,
    val muscleLabel: String,
    val equipmentLabel: String,
)

data class ExerciseLibraryUiState(
    val query: String = "",
    val items: List<ExerciseListItem> = emptyList(),
    val isLoading: Boolean = true,
) {
    /**
     * A search that matched nothing — deliberately distinct from [isLoading] so
     * the UI shows "no results" rather than a spinner that never resolves.
     */
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

/**
 * Holds the library's search state and the filtered list (ROADMAP F3).
 *
 * The repository flow and the query are combined here rather than in the
 * composable, so filtering is testable on the JVM with no device involved.
 */
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    repository: ExerciseRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<ExerciseLibraryUiState> =
        combine(repository.observeExercises(), query) { exercises, currentQuery ->
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

    private companion object {
        /** Keeps the upstream flow warm across a configuration change. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Shared with the workout screens' exercise picker, which renders the same rows. */
internal fun Exercise.toListItem() = ExerciseListItem(
    id = id,
    name = name,
    muscleLabel = primaryMuscle.label,
    equipmentLabel = equipment.label,
)
