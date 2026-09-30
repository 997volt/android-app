package com.example.androidapp.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.ExerciseSearch
import com.example.androidapp.domain.getOrNull
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.taxonomySubtitle
import com.example.androidapp.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One row of the library list, flattened so the composable does no domain work.
 *
 * [subtitle] is null for an exercise whose taxonomy has not been filled in — a
 * custom exercise created from just a name. The row then renders nothing under
 * the title rather than the "Other · Other" the raw labels would produce
 * (ROADMAP N2).
 */
data class ExerciseListItem(
    val id: String,
    val name: String,
    val subtitle: String?,
)

data class ExerciseLibraryUiState(
    val query: String = "",
    val items: List<ExerciseListItem> = emptyList(),
    val isLoading: Boolean = true,
    /**
     * No exercises exist at all, as opposed to none matching the query.
     *
     * The two looked identical on screen — both rendered "No exercises match \"\"" —
     * which told the user nothing about whether to clear the search or something
     * was actually wrong (ROADMAP P1.1a).
     */
    val libraryIsEmpty: Boolean = false,
    /**
     * The library could not be read (ROADMAP B4). Shown in place of the list, since
     * an empty list would be a lie about what is on the device.
     */
    val error: DataError? = null,
) {
    /**
     * A search that matched nothing — deliberately distinct from [isLoading] so
     * the UI shows "no results" rather than a spinner that never resolves.
     */
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

/**
 * Holds the library's search state, the filtered list, and whether a workout is
 * already running (ROADMAP F3, P1.16).
 *
 * Backing out of a workout leaves the session active in the database. Without this
 * the library screen gave no sign of it and its button still read "Start workout",
 * which quietly undid P1.8's crash recovery on the ordinary path — the one users
 * actually take.
 */
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        exerciseRepository.observeExercises(),
        query,
    ) { result, currentQuery ->
        // A read failure is rendered where the list would have been (ROADMAP B4),
        // instead of escaping the flow and taking the screen down.
        val exercises = result.getOrNull().orEmpty()
        ExerciseLibraryUiState(
            query = currentQuery,
            items = ExerciseSearch.filter(exercises, currentQuery).map { it.toListItem() },
            isLoading = false,
            libraryIsEmpty = exercises.isEmpty(),
            error = (result as? DataResult.Failure)?.error,
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
        const val TICK_MILLIS = 1_000L
    }
}

/** Shared with the workout screens' exercise picker, which renders the same rows. */
internal fun Exercise.toListItem() = ExerciseListItem(
    id = id,
    name = name,
    subtitle = taxonomySubtitle(primaryMuscle, equipment),
)
