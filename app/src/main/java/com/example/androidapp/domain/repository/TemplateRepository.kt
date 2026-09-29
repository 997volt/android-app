package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.model.TemplateExercise
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

    /** The template's exercises, in their stored order. */
    fun observeExercises(templateId: String): Flow<List<TemplateExercise>>

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

    /**
     * Moves an exercise one slot: [delta] -1 for up, +1 for down.
     *
     * Moving past either end is a no-op rather than a failure — the button at the
     * edge should do nothing, not report an error.
     */
    suspend fun moveExercise(templateExerciseId: String, delta: Int): DataResult<Unit>
}
