package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One exercise performed within a session, at an explicit [position].
 *
 * Foreign keys are declared so the database itself enforces integrity: deleting
 * a session cannot leave orphaned rows, and an exercise referenced by history
 * cannot be hard-deleted out from under it. (Library exercises are soft-deleted,
 * so `RESTRICT` never fires in normal use — it exists to catch a bug.)
 *
 * Room requires an index on every foreign-key column; without one, every
 * cascade check would be a full table scan.
 */
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    /**
     * A rest this session's plan prescribed, or null to use the library's (N14).
     *
     * Seeded when the workout is started from a template, because a plan that says
     * "3m break" and a workout that counts 90 seconds is the plan being ignored.
     */
    val restSeconds: Int? = null,
    /** A cue the plan prescribed, or null to use the library's (N14). */
    val techniqueNote: String? = null,
    /**
     * When this exercise was marked done, or null while it is still open
     * (ROADMAP N7).
     *
     * A session state, not a delete: the sets and their history stay exactly where
     * they are, and this only stops more sets being added and dims what is there.
     */
    val finishedAt: Long? = null,
    /**
     * How well the target muscle was worked, 1–10, or null (ROADMAP N8). Captured
     * when the exercise is marked done and editable from the workout detail.
     */
    val muscleFeel: Int? = null,
    /** Discomfort in joints or connective tissue, 1–10, or null (ROADMAP N8). */
    val jointPain: Int? = null,
    /**
     * Which joints hurt, or null (ROADMAP N9). Free text, and only meaningful
     * beside [jointPain] — but stored either way, because the note is often what
     * makes a past rating legible a month later.
     */
    val jointPainNote: String? = null,
    /**
     * Which superset or circuit this exercise belongs to, or null (ROADMAP N24).
     *
     * An ordinal within the session rather than a foreign key: the exercises sharing a number
     * are performed in rounds, and nothing else needs to be known about the group.
     */
    val supersetGroup: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
