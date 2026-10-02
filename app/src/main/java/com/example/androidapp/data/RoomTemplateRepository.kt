package com.example.androidapp.data

import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.TemplateDao
import androidx.room.withTransaction
import com.example.androidapp.domain.model.Rpe
import java.time.DayOfWeek
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
    private val database: WorkoutDatabase,
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

    override suspend fun createTemplateFromSession(
        sessionId: String,
        name: String,
    ): DataResult<String> = dataResultOf {
        val trimmed = requireName(name)
        database.withTransaction {
            val exercises = database.sessionExerciseDao().findLiveSessionExercises(sessionId)
            if (exercises.isEmpty()) {
                throw InvalidInputException("That workout has no exercises to copy")
            }
            val sets = database.sessionExerciseDao()
                .findLiveSetsForSession(sessionId)
                .groupBy { it.sessionExerciseId }

            val now = timeSource.nowEpochMillis()
            val templateId = UUID.randomUUID().toString()
            dao.insertTemplate(
                TemplateEntity(
                    id = templateId,
                    name = trimmed,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            )
            val plan = PlanBeingBuilt(dao = dao, templateId = templateId, now = now)
            exercises.forEachIndexed { index, exercise ->
                copyExerciseInto(
                    plan = plan,
                    exercise = exercise,
                    position = index,
                    sets = sets[exercise.id].orEmpty(),
                )
            }
            templateId
        }
    }

    override suspend fun renameTemplate(templateId: String, name: String): DataResult<Unit> =
        dataResultOf {
            if (dao.rename(id = templateId, name = requireName(name), at = timeSource.nowEpochMillis()) == 0) {
                throw NotFoundException("template $templateId")
            }
        }

    override suspend fun setWeekday(templateId: String, weekday: DayOfWeek?): DataResult<Unit> =
        dataResultOf {
            val updated = dao.setWeekday(
                id = templateId,
                weekday = weekday,
                at = timeSource.nowEpochMillis(),
            )
            if (updated == 0) throw NotFoundException("template $templateId")
        }

    override suspend fun deleteTemplate(templateId: String): DataResult<Unit> = dataResultOf {
        // A soft delete, so the rows stay for the export to carry (P1.12).
        if (dao.softDeleteTemplate(id = templateId, at = timeSource.nowEpochMillis()) == 0) {
            throw NotFoundException("template $templateId")
        }
    }

    override suspend fun setSupersetGroup(
        templateExerciseIds: List<String>,
        group: Int?,
    ): DataResult<Unit> = dataResultOf {
        if (templateExerciseIds.isEmpty()) return@dataResultOf

        val updated = dao.setSupersetGroup(
            templateExerciseIds,
            group,
            timeSource.nowEpochMillis(),
        )
        if (updated != templateExerciseIds.size) {
            throw NotFoundException("$updated of ${templateExerciseIds.size} planned exercises were written")
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

    override suspend fun prependSets(
        templateExerciseId: String,
        edits: List<TemplateSetEdit>,
    ): DataResult<Unit> = dataResultOf {
        if (edits.isEmpty()) return@dataResultOf
        edits.forEach(::validate)
        if (dao.findTemplateExercise(templateExerciseId) == null) {
            throw NotFoundException("template exercise $templateExerciseId")
        }
        val now = timeSource.nowEpochMillis()
        database.withTransaction {
            // One statement moves the existing sets down; the ramp then takes indices 0..n-1 in the
            // order it was computed, so the plan reads warm-ups first and work after (ROADMAP B34).
            dao.shiftSetIndexes(templateExerciseId, by = edits.size, at = now)
            edits.forEachIndexed { index, edit ->
                dao.insertTemplateSet(
                    TemplateSetEntity(
                        id = UUID.randomUUID().toString(),
                        templateExerciseId = templateExerciseId,
                        setIndex = index,
                        role = edit.role,
                        targetWeightGrams = edit.targetWeightGrams,
                        targetAssistanceGrams = edit.targetAssistanceGrams,
                        targetRepsMin = edit.targetRepsMin,
                        targetRepsMax = edit.targetRepsMax,
                        targetRpeHalves = edit.targetRpeHalves,
                        note = edit.note,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                    ),
                )
            }
        }
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
                targetAssistanceGrams = edit.targetAssistanceGrams,
                targetRepsMin = edit.targetRepsMin,
                targetRepsMax = edit.targetRepsMax,
                targetRpeHalves = edit.targetRpeHalves,
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
                    targetAssistanceGrams = edit.targetAssistanceGrams,
                    targetRepsMin = edit.targetRepsMin,
                    targetRepsMax = edit.targetRepsMax,
                    targetRpeHalves = edit.targetRpeHalves,
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
        validateLoad(edit)
        validateEffort(edit)
    }

    /** A load is a weight or a magnitude of assistance, never a negative either way. */
    private fun validateLoad(edit: TemplateSetEdit) {
        val problem = when {
            edit.targetWeightGrams != null && edit.targetWeightGrams < 0 ->
                "A target weight cannot be negative."

            edit.targetAssistanceGrams != null && edit.targetAssistanceGrams < 0 ->
                "Assistance is a magnitude, not a negative weight."

            else -> null
        }
        if (problem != null) throw InvalidInputException(problem)
    }

    /** Reps are a range that can be absent at either end, and RPE sits on the scale. */
    private fun validateEffort(edit: TemplateSetEdit) {
        val problem = when {
            edit.targetRepsMin != null && edit.targetRepsMin < 1 -> "Target reps must be at least 1."
            edit.targetRepsMax != null && edit.targetRepsMax < 1 -> "Target reps must be at least 1."
            edit.targetRepsMin != null && edit.targetRepsMax != null &&
                edit.targetRepsMin > edit.targetRepsMax ->
                "The low end of a rep range cannot exceed the high end."

            !Rpe.isValid(edit.targetRpeHalves) ->
                "Target RPE must be between 1 and 10, in half steps."

            else -> null
        }
        if (problem != null) throw InvalidInputException(problem)
    }

    /** A template with no name is a list row nobody can tell apart from the next. */
    private fun requireName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw InvalidInputException("Give the template a name.")
        return trimmed
    }
}

/**
 * Copies one performed exercise and its sets into a plan being built (ROADMAP N31).
 *
 * File-level rather than a private member, for the same reason the workout repository's append helper
 * is: the class is at its function ceiling, and this keeps the copy a readable sequence rather than one
 * long method.
 */
/** The plan a copy is being written into: what every exercise shares. */
private class PlanBeingBuilt(
    val dao: TemplateDao,
    val templateId: String,
    val now: Long,
)

private suspend fun copyExerciseInto(
    plan: PlanBeingBuilt,
    exercise: SessionExerciseEntity,
    position: Int,
    sets: List<SetEntryEntity>,
) {
    val plannedId = UUID.randomUUID().toString()
    plan.dao.insertTemplateExercise(
        TemplateExerciseEntity(
            id = plannedId,
            templateId = plan.templateId,
            exerciseId = exercise.exerciseId,
            position = position,
            // The rest and the note are part of how it was performed, so the plan carries them; the
            // grouping is what makes a copied superset arrive together (N24).
            restSeconds = exercise.restSeconds,
            techniqueNote = exercise.techniqueNote,
            supersetGroup = exercise.supersetGroup,
            createdAt = plan.now,
            updatedAt = plan.now,
            deletedAt = null,
        ),
    )
    sets.forEachIndexed { setIndex, set ->
        plan.dao.insertTemplateSet(
            TemplateSetEntity(
                id = UUID.randomUUID().toString(),
                templateExerciseId = plannedId,
                setIndex = setIndex,
                role = set.setType,
                targetWeightGrams = set.weightGrams,
                // A set stores assistance as a magnitude and so does a plan; zero means "none", which
                // is what a plan should say rather than zero.
                targetAssistanceGrams = set.assistanceGrams.takeIf { it > 0L },
                targetRepsMin = set.reps,
                targetRepsMax = set.reps,
                targetRpeHalves = set.rpeHalves,
                note = set.note,
                createdAt = plan.now,
                updatedAt = plan.now,
                deletedAt = null,
            ),
        )
    }
}
