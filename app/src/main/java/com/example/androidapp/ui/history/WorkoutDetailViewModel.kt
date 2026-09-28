package com.example.androidapp.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.repository.WorkoutRepository
import com.example.androidapp.ui.navigation.WorkoutDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import androidx.navigation.toRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A set as the read-only history shows it. */
data class HistorySet(val id: String, val reps: Int, val weightGrams: Long)

/** An exercise within a past workout, with everything that was logged for it. */
data class HistoryExercise(
    val id: String,
    val name: String,
    val sets: List<HistorySet>,
)

data class WorkoutDetailUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
    val exercises: List<HistoryExercise> = emptyList(),
) {
    val notFound: Boolean get() = !isLoading && session == null

    val duration: Duration?
        get() = session?.let { s -> s.finishedAt?.let { Duration.between(s.startedAt, it) } }

    val setCount: Int get() = exercises.sumOf { it.sets.size }

    /**
     * Gram-reps, the same figure the list's SQL computes. Kept in step deliberately:
     * this is one formula in two places, and the instrumented history test asserts
     * they agree.
     */
    val volumeGrams: Long get() = exercises.sumOf { exercise ->
        exercise.sets.sumOf { it.weightGrams * it.reps }
    }
}

/**
 * One past workout (ROADMAP P1.6).
 *
 * Reads the session, its exercises and its sets — the same three the live workout
 * screen uses — and derives the totals here rather than duplicating the list's
 * aggregate SQL for a single row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.toRoute<WorkoutDetail>().sessionId

    private val session = workoutRepository.observeSession(sessionId)

    private val exercises = workoutRepository.observeSessionExercises(sessionId)

    private val sets = workoutRepository.observeSets(sessionId)

    val uiState: StateFlow<WorkoutDetailUiState> = combine(session, exercises, sets) { s, ex, logged ->
        WorkoutDetailUiState(
            isLoading = false,
            session = s,
            exercises = ex.map { row ->
                HistoryExercise(
                    id = row.id,
                    name = row.exerciseName,
                    sets = logged
                        .filter { it.sessionExerciseId == row.id }
                        .sortedBy { it.setIndex }
                        .map { HistorySet(id = it.id, reps = it.reps, weightGrams = it.weightGrams) },
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = WorkoutDetailUiState(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
