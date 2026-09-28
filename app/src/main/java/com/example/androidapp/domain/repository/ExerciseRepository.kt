package com.example.androidapp.domain.repository

import com.example.androidapp.domain.model.Exercise
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the exercise library.
 *
 * The UI depends only on this interface, so the in-memory implementation
 * shipped today can be swapped for a Room-backed one (ROADMAP F5) without
 * touching a single composable or ViewModel.
 */
interface ExerciseRepository {

    /** Observes the whole library, re-emitting whenever it changes. */
    fun observeExercises(): Flow<List<Exercise>>

    /** Returns the exercise with [id], or null when it does not exist. */
    suspend fun getExercise(id: String): Exercise?
}
