package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One exercise in a template, at an explicit [position] (ROADMAP N3).
 *
 * The foreign keys are the same shape `session_exercises` uses: deleting a template
 * cannot strand its rows, and an exercise referenced by a template cannot be
 * hard-deleted out from under it. Library exercises are soft-deleted, so `RESTRICT`
 * exists to catch a bug rather than to fire in normal use.
 *
 * [position] is stored rather than inferred, so reordering a template never depends
 * on row ids sorting usefully — the same reason a session exercise stores it.
 */
@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateExerciseEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val exerciseId: String,
    val position: Int,
    /**
     * A rest this exercise prescribes, or null to fall back to the library's (N5).
     *
     * A plan says "3m break" where the library only knows the movement.
     */
    val restSeconds: Int? = null,
    /** A cue this exercise prescribes, or null to fall back to the library's (N5). */
    val techniqueNote: String? = null,
    /** The superset or circuit this exercise is planned in, or null (ROADMAP N24, B16). */
    val supersetGroup: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
