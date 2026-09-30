package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.androidapp.domain.model.SetType

/**
 * A logged set (ROADMAP P1.3).
 *
 * [weightGrams] is a whole-gram `Long`; see `Weight` for why. `setType` is stored
 * by name via `Converters`, like every other enum here.
 *
 * The foreign key to `session_exercises` is what makes "delete this exercise"
 * unable to strand its sets.
 */
@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId")],
)
data class SetEntryEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val setIndex: Int,
    val reps: Int,
    val weightGrams: Long,
    /**
     * How much the machine took off, in grams, or 0 for none (ROADMAP N15).
     *
     * A separate column rather than a signed weight: assistance is not negative
     * tonnage, and a sign would make an assisted set subtract from volume — quietly
     * corrupting every trend built on it. Volume stays `weightGrams * reps`, so an
     * assisted set contributes nothing, exactly as bodyweight does.
     */
    val assistanceGrams: Long = 0,
    val setType: SetType,
    /**
     * Perceived effort, 1–10, or null when the set was logged without one
     * (ROADMAP N6). Nullable so the one-tap **Log set** path can keep writing
     * neither this nor [note].
     */
    val rpeHalves: Int? = null,
    /** A short comment on the set, or null (ROADMAP N6). */
    val note: String? = null,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
