package com.example.androidapp.data

import com.example.androidapp.data.local.TemplateEntity
import com.example.androidapp.data.local.TemplateExerciseEntity
import com.example.androidapp.data.local.TemplateSetEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.toDomain
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.InvalidInputException
import com.example.androidapp.domain.NotFoundException
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.dataResultOf
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.WorkoutTemplate
import com.example.androidapp.domain.model.TemplateSet
import com.example.androidapp.domain.model.TenPointScale
import com.example.androidapp.domain.nowEpochMillis
import com.example.androidapp.domain.repository.TemplateRepository
import com.example.androidapp.domain.repository.TemplateSetEdit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        combine(
            dao.observeTemplateExercises(templateId),
            dao.observeTemplateSets(templateId),
        ) { exercises, sets ->
            val byExercise = sets.groupBy { it.templateExerciseId }
            exercises.map { row ->
                row.toDomain(byExercise[row.id].orEmpty().map { it.toDomain() })
            }
        }

    override fun observeSets(templateId: String): Flow<List<TemplateSet>> =
        dao.observeTemplateSets(templateId).map { rows -> rows.map { it.toDomain() } }

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

    override suspend fun addSet(
        templateExerciseId: String,
        edit: TemplateSetEdit,
    ): DataResult<Unit> = dataResultOf {
        validate(edit)
        if (dao.findTemplateExercise(templateExerciseId) == null) {
            throw NotFoundException("template exercise $templateExerciseId")
        }
        val now = timeSource.nowEpochMillis()
        dao.insertTemplateSet(
            TemplateSetEntity(
                id = UUID.randomUUID().toString(),
                templateExerciseId = templateExerciseId,
                // Appended, so the plan reads in the order it was written.
                setIndex = dao.maxSetIndex(templateExerciseId) + 1,
                role = edit.role,
                targetWeightGrams = edit.targetWeightGrams,
                targetRepsMin = edit.targetRepsMin,
                targetRepsMax = edit.targetRepsMax,
                targetRpe = edit.targetRpe,
                note = edit.note?.trim()?.ifEmpty { null },
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    override suspend fun updateSet(templateSetId: String, edit: TemplateSetEdit): DataResult<Unit> =
        dataResultOf {
            validate(edit)
            val stored = dao.findTemplateSet(templateSetId)
                ?: throw NotFoundException("template set $templateSetId")
            val updated = dao.updateTemplateSet(
                stored.copy(
                    role = edit.role,
                    targetWeightGrams = edit.targetWeightGrams,
                    targetRepsMin = edit.targetRepsMin,
                    targetRepsMax = edit.targetRepsMax,
                    targetRpe = edit.targetRpe,
                    note = edit.note?.trim()?.ifEmpty { null },
                    updatedAt = timeSource.nowEpochMillis(),
                ),
            )
            if (updated == 0) throw NotFoundException("template set $templateSetId")
        }

    override suspend fun removeSet(templateSetId: String): DataResult<Unit> = dataResultOf {
        val updated = dao.softDeleteTemplateSet(
            id = templateSetId,
            at = timeSource.nowEpochMillis(),
        )
        if (updated == 0) throw NotFoundException("template set $templateSetId")
    }

    override suspend fun duplicateSets(templateExerciseId: String): DataResult<Unit> =
        dataResultOf {
            val existing = dao.findSetsForExercise(templateExerciseId)
            if (existing.isEmpty()) throw NotFoundException("no planned sets to duplicate")
            var index = dao.maxSetIndex(templateExerciseId) + 1
            val now = timeSource.nowEpochMillis()
            // Copies rather than references: the whole point is to adjust them
            // afterwards, and sharing a row would edit both.
            existing.forEach { original ->
                dao.insertTemplateSet(
                    original.copy(
                        id = UUID.randomUUID().toString(),
                        setIndex = index++,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }

    override suspend fun setExercisePlan(
        templateExerciseId: String,
        restSeconds: Int?,
        techniqueNote: String?,
    ): DataResult<Unit> = dataResultOf {
        // A rest of zero is not a rest; leaving it unset is how "use the library's"
        // is expressed (the same rule N5 applies to the library itself).
        if (restSeconds != null && restSeconds <= 0) {
            throw InvalidInputException("Rest must be a positive number of seconds.")
        }
        val updated = dao.setExerciseRestAndCue(
            id = templateExerciseId,
            restSeconds = restSeconds,
            techniqueNote = techniqueNote?.trim()?.ifEmpty { null },
            at = timeSource.nowEpochMillis(),
        )
        if (updated == 0) throw NotFoundException("template exercise $templateExerciseId")
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

    /**
     * A plan's targets have to be usable numbers.
     *
     * Nothing here checks the plan against what the user actually lifts — that is
     * deliberate (N14): a plan describes a shape, and a logged set is expected to
     * differ. What it does check is that the shape is not nonsense: a negative
     * weight, a zero-rep set, or a range that runs backwards.
     */
    private fun validate(edit: TemplateSetEdit) {
        val problem = when {
            edit.targetWeightGrams != null && edit.targetWeightGrams < 0 ->
                "A target weight cannot be negative."

            edit.targetRepsMin != null && edit.targetRepsMin < 1 -> "Target reps must be at least 1."
            edit.targetRepsMax != null && edit.targetRepsMax < 1 -> "Target reps must be at least 1."
            edit.targetRepsMin != null && edit.targetRepsMax != null &&
                edit.targetRepsMin > edit.targetRepsMax ->
                "The low end of a rep range cannot exceed the high end."

            !TenPointScale.isValid(edit.targetRpe) ->
                "Target RPE must be between ${TenPointScale.MIN} and ${TenPointScale.MAX}."

            else -> null
        }
        // One throw: the caller's contract is the same whichever target is unusable,
        // and the message is what the user reads.
        if (problem != null) throw InvalidInputException(problem)
    }

    /** A template with no name is a list row nobody can tell apart from the next. */
    private fun requireName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw InvalidInputException("Give the template a name.")
        return trimmed
    }
}
