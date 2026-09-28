package com.example.androidapp.data

import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.toDomain
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.repository.ExerciseRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [ExerciseRepository] (ROADMAP F5).
 *
 * This replaced `InMemoryExerciseRepository` without a single change to a
 * composable or a ViewModel, which is what the interface was for.
 *
 * Mapping to the domain type at this boundary means the soft-delete filter and
 * the sync columns stay entirely inside the data layer.
 */
@Singleton
class RoomExerciseRepository @Inject constructor(
    database: WorkoutDatabase,
) : ExerciseRepository {

    private val dao = database.exerciseDao()

    override fun observeExercises(): Flow<List<Exercise>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getExercise(id: String): Exercise? =
        dao.findById(id)?.toDomain()
}
