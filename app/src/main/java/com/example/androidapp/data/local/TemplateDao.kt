package com.example.androidapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Templates and the exercises they hold (ROADMAP N3).
 *
 * Its own DAO rather than more methods on `WorkoutDao`: that interface was already
 * at its ceiling, and a template is a different aggregate from a session — the two
 * only meet when a workout is started from one.
 */
@Dao
interface TemplateDao {

    /** Every live template with its exercise count, name-ordered. */
    @Query(
        """
        SELECT t.id AS id,
               t.name AS name,
               (
                   SELECT COUNT(*) FROM template_exercises te
                   WHERE te.templateId = t.id AND te.deletedAt IS NULL
               ) AS exerciseCount
        FROM templates t
        WHERE t.deletedAt IS NULL
        ORDER BY t.name ASC
        """,
    )
    fun observeTemplates(): Flow<List<TemplateSummaryRow>>

    @Query("SELECT * FROM templates WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): TemplateEntity?

    /** One template with its count, re-emitting when it is renamed (N3). */
    @Query(
        """
        SELECT t.id AS id,
               t.name AS name,
               (
                   SELECT COUNT(*) FROM template_exercises te
                   WHERE te.templateId = t.id AND te.deletedAt IS NULL
               ) AS exerciseCount
        FROM templates t
        WHERE t.id = :id AND t.deletedAt IS NULL
        """,
    )
    fun observeTemplate(id: String): Flow<TemplateSummaryRow?>

    @Query("SELECT * FROM template_exercises WHERE id = :id AND deletedAt IS NULL")
    suspend fun findTemplateExercise(id: String): TemplateExerciseEntity?

    @Insert
    suspend fun insertTemplate(template: TemplateEntity)

    /** Rows updated: 0 means the template is gone or was already deleted. */
    @Query("UPDATE templates SET name = :name, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun rename(id: String, name: String, at: Long): Int

    @Query("UPDATE templates SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteTemplate(id: String, at: Long): Int

    /** The template's exercises with their library details, in stored order. */
    @Query(
        """
        SELECT te.id AS id,
               te.templateId AS templateId,
               te.exerciseId AS exerciseId,
               te.position AS position,
               e.name AS exerciseName,
               e.primaryMuscle AS primaryMuscle,
               e.equipment AS equipment
        FROM template_exercises te
        JOIN exercises e ON e.id = te.exerciseId
        WHERE te.templateId = :templateId
          AND te.deletedAt IS NULL
          AND e.deletedAt IS NULL
        ORDER BY te.position ASC
        """,
    )
    fun observeTemplateExercises(templateId: String): Flow<List<TemplateExerciseDetail>>

    /** The same list, one shot — what starting a workout from a template needs. */
    @Query(
        """
        SELECT te.exerciseId AS exerciseId
        FROM template_exercises te
        JOIN exercises e ON e.id = te.exerciseId
        WHERE te.templateId = :templateId
          AND te.deletedAt IS NULL
          AND e.deletedAt IS NULL
        ORDER BY te.position ASC
        """,
    )
    suspend fun findExerciseIdsInOrder(templateId: String): List<String>

    /** Next free position; -1 on an empty template, so callers add 1. */
    @Query("SELECT COALESCE(MAX(position), -1) FROM template_exercises WHERE templateId = :templateId")
    suspend fun maxPosition(templateId: String): Int

    @Insert
    suspend fun insertTemplateExercise(row: TemplateExerciseEntity)

    @Query(
        """
        UPDATE template_exercises
        SET deletedAt = :at, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteTemplateExercise(id: String, at: Long): Int

    @Query(
        """
        UPDATE template_exercises
        SET position = :position, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun setPosition(id: String, position: Int, at: Long): Int

    /**
     * Swaps two rows' positions in one transaction, so a reorder cannot leave the
     * list half-moved if the second write fails.
     */
    @Transaction
    suspend fun swapPositions(
        firstId: String,
        firstPosition: Int,
        secondId: String,
        secondPosition: Int,
        at: Long,
    ) {
        setPosition(id = firstId, position = firstPosition, at = at)
        setPosition(id = secondId, position = secondPosition, at = at)
    }
}
