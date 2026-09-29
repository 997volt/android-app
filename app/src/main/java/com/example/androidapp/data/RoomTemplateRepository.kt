package com.example.androidapp.data

import com.example.androidapp.data.local.TemplateEntity
import com.example.androidapp.data.local.TemplateExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.toDomain
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.InvalidInputException
import com.example.androidapp.domain.NotFoundException
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.dataResultOf
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.domain.nowEpochMillis
import com.example.androidapp.domain.repository.TemplateRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Room-backed [TemplateRepository] (ROADMAP N3).
 *
 * Read and write access to templates is entirely here; the only other place that
 * touches the tables is starting a workout from one, which lives in
 * `RoomWorkoutRepository` and reads the ordered exercise ids.
 */
@Singleton
class RoomTemplateRepository @Inject constructor(
    database: WorkoutDatabase,
    private val timeSource: TimeSource,
) : TemplateRepository {

    private val dao = database.templateDao()

    override fun observeTemplates(): Flow<List<WorkoutTemplate>> =
        dao.observeTemplates().map { rows -> rows.map { it.toDomain() } }

    override fun observeTemplate(templateId: String): Flow<WorkoutTemplate?> =
        dao.observeTemplate(templateId).map { it?.toDomain() }

    override fun observeExercises(templateId: String): Flow<List<TemplateExercise>> =
        dao.observeTemplateExercises(templateId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun createTemplate(name: String): DataResult<String> = dataResultOf {
        val trimmed = requireName(name)
        val id = UUID.randomUUID().toString()
        val now = timeSource.nowEpochMillis()
        dao.insertTemplate(
            TemplateEntity(
                id = id,
                name = trimmed,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        id
    }

    override suspend fun renameTemplate(templateId: String, name: String): DataResult<Unit> =
        dataResultOf {
            if (dao.rename(id = templateId, name = requireName(name), at = timeSource.nowEpochMillis()) == 0) {
                throw NotFoundException("template $templateId")
            }
        }

    override suspend fun deleteTemplate(templateId: String): DataResult<Unit> = dataResultOf {
        // A soft delete, so the rows stay for the export to carry (P1.12).
        if (dao.softDeleteTemplate(id = templateId, at = timeSource.nowEpochMillis()) == 0) {
            throw NotFoundException("template $templateId")
        }
    }

    override suspend fun addExercise(templateId: String, exerciseId: String): DataResult<Unit> =
        dataResultOf {
            if (dao.findById(templateId) == null) {
                throw NotFoundException("template $templateId")
            }
            val now = timeSource.nowEpochMillis()
            dao.insertTemplateExercise(
                TemplateExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    templateId = templateId,
                    exerciseId = exerciseId,
                    // Appended, so the template is edited in the order it is built.
                    position = dao.maxPosition(templateId) + 1,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            )
        }

    override suspend fun removeExercise(templateExerciseId: String): DataResult<Unit> =
        dataResultOf {
            val updated = dao.softDeleteTemplateExercise(
                id = templateExerciseId,
                at = timeSource.nowEpochMillis(),
            )
            if (updated == 0) throw NotFoundException("template exercise $templateExerciseId")
        }

    override suspend fun moveExercise(templateExerciseId: String, delta: Int): DataResult<Unit> =
        dataResultOf {
            val row = dao.findTemplateExercise(templateExerciseId)
                ?: throw NotFoundException("template exercise $templateExerciseId")
            val ordered = dao.observeTemplateExercises(row.templateId).first()
            val index = ordered.indexOfFirst { it.id == templateExerciseId }
            val neighbour = ordered.getOrNull(index + delta)
            // At the top or the bottom: nothing to do, and not an error.
            if (index >= 0 && neighbour != null) {
                dao.swapPositions(
                    firstId = row.id,
                    firstPosition = neighbour.position,
                    secondId = neighbour.id,
                    secondPosition = row.position,
                    at = timeSource.nowEpochMillis(),
                )
            }
        }

    /** A template with no name is a list row nobody can tell apart from the next. */
    private fun requireName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw InvalidInputException("Give the template a name.")
        return trimmed
    }
}
