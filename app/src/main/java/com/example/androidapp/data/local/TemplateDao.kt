package com.example.androidapp.data.local

import java.time.DayOfWeek
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
               t.weekday AS weekday,
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
               t.weekday AS weekday,
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
               e.equipment AS equipment,
               te.restSeconds AS restSeconds,
               te.techniqueNote AS techniqueNote
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

    /**
     * The same list with what the plan prescribes for each exercise (ROADMAP N14),
     * which is what a session started from this template seeds onto its rows.
     */
    @Query(
        """
        SELECT te.exerciseId AS exerciseId, te.restSeconds AS restSeconds,
               te.techniqueNote AS techniqueNote
        FROM template_exercises te
        WHERE te.templateId = :templateId AND te.deletedAt IS NULL
        ORDER BY te.position ASC
        """,
    )
    suspend fun findPlannedExercises(templateId: String): List<PlannedExercise>

    /** One exercise of a plan, as seeding needs it. */
    @Suppress("LongParameterList")
    data class PlannedExercise(
        val exerciseId: String,
        val restSeconds: Int?,
        val techniqueNote: String?,
    )

    /**
     * Every planned set of a template, ordered by the exercise's position then the
     * set's index (ROADMAP N14).
     *
     * One query for the whole template rather than one per exercise: the editor shows
     * them all at once, and N+1 flows would be a subscription per exercise.
     */
    @Query(
        """
        SELECT ts.* FROM template_sets ts
        JOIN template_exercises te ON te.id = ts.templateExerciseId
        WHERE te.templateId = :templateId
          AND ts.deletedAt IS NULL
          AND te.deletedAt IS NULL
        ORDER BY te.position ASC, ts.setIndex ASC
        """,
    )
    fun observeTemplateSets(templateId: String): Flow<List<TemplateSetEntity>>

    @Query("SELECT * FROM template_sets WHERE id = :id AND deletedAt IS NULL")
    suspend fun findTemplateSet(id: String): TemplateSetEntity?

    @Query(
        """
        SELECT * FROM template_sets
        WHERE templateExerciseId = :templateExerciseId AND deletedAt IS NULL
        ORDER BY setIndex ASC
        """,
    )
    suspend fun findSetsForExercise(templateExerciseId: String): List<TemplateSetEntity>

    @Query(
        """
        SELECT COALESCE(MAX(setIndex), -1) FROM template_sets
        WHERE templateExerciseId = :templateExerciseId AND deletedAt IS NULL
        """,
    )
    suspend fun maxSetIndex(templateExerciseId: String): Int

    @Insert
    suspend fun insertTemplateSet(row: TemplateSetEntity)

    @Update
    suspend fun updateTemplateSet(row: TemplateSetEntity): Int

    @Query(
        """
        UPDATE template_sets
        SET deletedAt = :at, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun softDeleteTemplateSet(id: String, at: Long): Int

    /** Writes a template exercise's prescribed rest and cue (ROADMAP N14). */
    @Query(
        """
        UPDATE template_exercises
        SET restSeconds = :restSeconds, techniqueNote = :techniqueNote, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun setExerciseRestAndCue(
        id: String,
        restSeconds: Int?,
        techniqueNote: String?,
        at: Long,
    ): Int

    /** Pins a plan to a weekday, or unpins it (ROADMAP N16). */
    @Query(
        """
        UPDATE templates
        SET weekday = :weekday, updatedAt = :at
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun setWeekday(id: String, weekday: DayOfWeek?, at: Long): Int

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
