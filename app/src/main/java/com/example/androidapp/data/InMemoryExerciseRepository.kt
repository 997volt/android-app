package com.example.androidapp.data

import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.repository.ExerciseRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [ExerciseRepository] over [SeedExercises].
 *
 * A deliberate placeholder: Room arrives in F5, which is exactly why the UI
 * talks to the interface instead of this class. Reads are already suspend and
 * already expose a [Flow], so swapping in a database-backed implementation is
 * a DI-binding change, not a UI rewrite.
 *
 * State lives only for the process lifetime — nothing here is persisted, which
 * keeps the backup-exclusion guarantee trivially true for the library.
 */
@Singleton
class InMemoryExerciseRepository @Inject constructor() : ExerciseRepository {

    private val exercises = MutableStateFlow(SeedExercises.all)

    override fun observeExercises(): Flow<List<Exercise>> = exercises.asStateFlow()

    override suspend fun getExercise(id: String): Exercise? =
        exercises.value.firstOrNull { it.id == id }
}
