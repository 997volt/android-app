package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup

/**
 * A persisted library exercise (ROADMAP F5).
 *
 * The schema is deliberately written **sync-ready** even though storage is
 * local-only today:
 *
 *  - [id] is a stable string key (a slug for seeded rows, a UUID for
 *    user-created ones) rather than an autoincrement rowid, so a row can be
 *    created on any device without negotiating a key with a server.
 *  - [createdAt] / [updatedAt] / [deletedAt] are what a future sync (P4.9) needs
 *    to resolve conflicts and propagate deletions. [deletedAt] is a soft delete
 *    because a hard delete is invisible to a peer that has not synced yet.
 *
 * Retrofitting those three columns later would mean a migration *plus* a
 * backfill with invented timestamps, which is precisely the cost this avoids.
 * Nothing reads the sync columns yet — that is intentional, not dead weight.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val equipment: Equipment,
    val movementPattern: MovementPattern,
    val isCustom: Boolean,
    /**
     * This exercise's own rest between sets, or null to use the app default
     * (ROADMAP N5). Nullable with no default, so the migration that adds it is a
     * plain ALTER and existing rows read as "use the default".
     */
    val restSeconds: Int? = null,
    /** A short cue to read *while* lifting, not a description (ROADMAP N5). */
    val techniqueNote: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
