package com.example.androidapp.ui.history

import com.example.androidapp.domain.model.SetType
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.WorkoutSession
import com.example.androidapp.domain.repository.WorkoutRepository
import com.example.androidapp.ui.navigation.WorkoutDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import androidx.navigation.toRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A set as the history detail shows it. */
data class HistorySet(
    val id: String,
    val reps: Int,
    val weightGrams: Long,
    /** 1–10, or null when none was recorded (ROADMAP N6). */
    val rpeHalves: Int? = null,
    /** The set's comment, or null (ROADMAP N6). */
    val note: String? = null,
    /** The machine's assistance, 0 for none (ROADMAP N15). */
    val assistanceGrams: Long = 0,
)

/** An exercise within a past workout, with everything that was logged for it. */
data class HistoryExercise(
    val id: String,
    val name: String,
    val sets: List<HistorySet>,
    /** How well the target muscle was worked, 1–10, or null (ROADMAP N8). */
    val muscleFeel: Int? = null,
    /** Joint or connective-tissue discomfort, 1–10, or null (ROADMAP N8). */
    val jointPain: Int? = null,
    /** Which joints, or null (ROADMAP N9). */
    val jointPainNote: String? = null,
)

data class WorkoutDetailUiState(
    val isLoading: Boolean = true,
    val session: WorkoutSession? = null,
    val exercises: List<HistoryExercise> = emptyList(),
    val error: DataError? = null,
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
 * One past workout, with the edits that make a mis-tap recoverable (P1.6, P1.7).
 *
 * Reads the session, its exercises and its sets — the same three the live workout
 * screen uses — and derives the totals here rather than duplicating the list's
 * aggregate SQL for a single row.
 *
 * Correcting a set matters more than it looks: before this existed, `Finish` was a
 * one-way door. A mistyped weight was visible in history and permanently wrong.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.toRoute<WorkoutDetail>().sessionId

    private val lastError = MutableStateFlow<DataError?>(null)

    /** True once the workout is gone, so the screen can leave rather than sit on a blank page. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    private val session = workoutRepository.observeSession(sessionId)
    private val exercises = workoutRepository.observeSessionExercises(sessionId)
    private val sets = workoutRepository.observeSets(sessionId)

    val uiState: StateFlow<WorkoutDetailUiState> =
        combine(session, exercises, sets, lastError) { current, exerciseRows, logged, error ->
            WorkoutDetailUiState(
                isLoading = false,
                session = current,
                exercises = exerciseRows.map { row ->
                    HistoryExercise(
                        id = row.id,
                        name = row.exerciseName,
                        muscleFeel = row.muscleFeel,
                        jointPain = row.jointPain,
                        jointPainNote = row.jointPainNote,
                        sets = logged
                            .filter { it.sessionExerciseId == row.id }
                            .sortedBy { it.setIndex }
                            .map {
                                HistorySet(
                                    id = it.id,
                                    reps = it.reps,
                                    weightGrams = it.weightGrams,
                                    rpeHalves = it.rpeHalves,
                                    note = it.note,
                                    // Every column the row has, or the screen silently
                                    // renders a default (ROADMAP B5, N15).
                                    assistanceGrams = it.assistanceGrams,
                                )
                            },
                    )
                },
                error = error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = WorkoutDetailUiState(),
        )

    fun onUpdateSet(
        setId: String,
        reps: Int,
        weightGrams: Long,
        rpeHalves: Int? = null,
        note: String? = null,
        setType: SetType = SetType.NORMAL,
        assistanceGrams: Long = 0,
    ) = write {
        workoutRepository.updateSet(
            setId = setId,
            reps = reps,
            weightGrams = weightGrams,
            rpeHalves = rpeHalves,
            note = note,
            setType = setType,
            assistanceGrams = assistanceGrams,
        )
    }

    fun onDeleteSet(setId: String) = write {
        workoutRepository.deleteSet(setId)
    }

    /**
     * Saves how an exercise felt (ROADMAP N8), from the workout detail.
     *
     * This is the "editable later" half of the decision: the ratings are captured
     * when an exercise is marked done, but they can be filled in or corrected
     * afterwards without reopening the workout.
     */
    fun onRateExercise(
        sessionExerciseId: String,
        muscleFeel: Int?,
        jointPain: Int?,
        jointPainNote: String?,
    ) = write {
        workoutRepository.rateExercise(sessionExerciseId, muscleFeel, jointPain, jointPainNote)
    }

    /**
     * Removes the whole workout.
     *
     * A soft delete, so the rows stay for the export to carry — but with no restore
     * in the UI, so the screen confirms first.
     */
    fun onDeleteWorkout() {
        viewModelScope.launch {
            when (val result = workoutRepository.deleteSession(sessionId)) {
                is DataResult.Success -> {
                    lastError.value = null
                    _deleted.value = true
                }

                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    private fun write(block: suspend () -> DataResult<Unit>) {
        viewModelScope.launch {
            when (val result = block()) {
                is DataResult.Success -> lastError.value = null
                is DataResult.Failure -> lastError.value = result.error
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
