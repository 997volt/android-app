package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.TemplateExercise
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.model.TemplateSet
import com.example.androidapp.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Reading and writing workout templates (ROADMAP N3).
 *
 * Writes return [DataResult] for the same reason every other repository's do (F7):
 * a template that silently failed to save is a plan the user thinks they have.
 */
interface TemplateRepository {

    /** Every live template, name-ordered, with its exercise count. */
    fun observeTemplates(): Flow<List<WorkoutTemplate>>

    /** One template, re-emitting when it is renamed. Null when it is gone. */
    fun observeTemplate(templateId: String): Flow<WorkoutTemplate?>

    /**
     * The template's exercises in their stored order, each carrying its planned sets
     * (ROADMAP N14).
     */
    fun observeExercises(templateId: String): Flow<List<TemplateExercise>>

    /**
     * The template's planned sets on their own, ordered by exercise position then set
     * index — what a workout started from this plan prefills its targets from.
     */
    fun observeSets(templateId: String): Flow<List<TemplateSet>>

    /**
     * Stores a new template named [name] and returns its id.
     *
     * Creation asks for the name only, exactly as creating an exercise does
     * (ROADMAP N2): the exercises are added afterwards from the editor.
     */
    suspend fun createTemplate(name: String): DataResult<String>

    suspend fun renameTemplate(templateId: String, name: String): DataResult<Unit>

    /** Soft-deletes the template; its rows stay for an export to carry. */
    suspend fun deleteTemplate(templateId: String): DataResult<Unit>

    /** Appends [exerciseId] to the end of the template. */
    suspend fun addExercise(templateId: String, exerciseId: String): DataResult<Unit>

    suspend fun removeExercise(templateExerciseId: String): DataResult<Unit>

    /** Appends a planned set to a template exercise (ROADMAP N14). */
    suspend fun addSet(templateExerciseId: String, edit: TemplateSetEdit): DataResult<Unit>

    /** Overwrites a planned set's targets. */
    suspend fun updateSet(templateSetId: String, edit: TemplateSetEdit): DataResult<Unit>

    suspend fun removeSet(templateSetId: String): DataResult<Unit>

    /**
     * **Copy forward**: duplicates an exercise's planned sets after themselves, so a
     * shape authored once becomes four or five sets in two taps (ROADMAP N14).
     *
     * Without it, a plan of six exercises and thirty sets is thirty forms on a phone,
     * which is where a plan stops being written down at all.
     */
    suspend fun duplicateSets(templateExerciseId: String): DataResult<Unit>

    /**
     * Writes what a plan prescribes for one exercise: a rest and a cue, either of
     * which may be null to fall back to the library's (N5).
     */
    suspend fun setExercisePlan(
        templateExerciseId: String,
        restSeconds: Int?,
        techniqueNote: String?,
    ): DataResult<Unit>

    /**
     * Moves an exercise one slot: [delta] -1 for up, +1 for down.
     *
     * Moving past either end is a no-op rather than a failure — the button at the
     * edge should do nothing, not report an error.
     */
    suspend fun moveExercise(templateExerciseId: String, delta: Int): DataResult<Unit>
}

/**
 * The editable targets of a planned set (ROADMAP N14).
 *
 * A parameter object rather than six arguments: everything here is optional except
 * the role, and a call site that lists them positionally would be unreadable.
 */
data class TemplateSetEdit(
    val role: SetType = SetType.NORMAL,
    val targetWeightGrams: Long? = null,
    /** The assistance the plan prescribes, as a magnitude (ROADMAP N15). */
    val targetAssistanceGrams: Long? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpe: Int? = null,
    val note: String? = null,
)
