package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.Exercise
import kotlinx.coroutines.flow.Flow

/**
 * Read and write access to the exercise library.
 *
 * The UI depends only on this interface, so the in-memory implementation
 * shipped today can be swapped for a Room-backed one (ROADMAP F5) without
 * touching a single composable or ViewModel.
 */
interface ExerciseRepository {

    /**
     * Observes the whole library, re-emitting whenever it changes.
     *
     * A [DataResult] rather than a bare list (ROADMAP B4): a database failure used
     * to escape this flow and take the screen down, and a failure the UI can render
     * beats an exception that disappears inside a coroutine. The same argument F7
     * made for the writes, applied to the two reads that were still exempt.
     */
    fun observeExercises(): Flow<DataResult<List<Exercise>>>

    /**
     * The exercise with [id], as a value: `Success(null)` means it does not exist,
     * `Failure` means the read itself failed. The two used to be indistinguishable.
     */
    suspend fun getExercise(id: String): DataResult<Exercise?>

    /**
     * Stores a new custom exercise named [name] and returns it (ROADMAP N2).
     *
     * Creation deliberately asks for the name only: the taxonomy fields are
     * non-nullable on disk, so an unedited custom exercise is stored as
     * unspecified (`OTHER`) and can be filled in later from the detail screen.
     * A UUID id and `isCustom = true` are the two things that make it a
     * user-created entry rather than a seeded one.
     */
    suspend fun createCustomExercise(name: String): DataResult<Exercise>

    /**
     * Saves the editable attributes of an existing exercise (ROADMAP N2).
     *
     * Fails with [com.example.androidapp.domain.DataError.NotFound] when [exercise]
     * no longer exists, so a stale detail screen cannot silently write nothing.
     */
    suspend fun updateExercise(exercise: Exercise): DataResult<Unit>
}
