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
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
