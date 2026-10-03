package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One exercise of one program slot, and the rest and cue that slot prescribes for it
 * (ROADMAP P3.8).
 *
 * The parent of a slot's [ProgramSlotSetEntity] rows, mirroring the template's
 * `template_exercises`/`template_sets` split: the rest and cue belong to the exercise, the
 * targets belong to the set, and denormalising the former onto every set row would make one
 * idea live in several places. The exercise is named by [exerciseId] rather than by a
 * `template_exercises` row, so the prescription survives editing the template (N16's living
 * template) — an exercise the template no longer has simply goes unread.
 */
@Entity(
    tableName = "program_slot_exercises",
    indices = [Index(value = ["slotId"])],
    foreignKeys = [
        ForeignKey(
            entity = ProgramSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProgramSlotExerciseEntity(
    @PrimaryKey val id: String,
    val slotId: String,
    val exerciseId: String,
    /** The rest this slot prescribes, or null (P3.8). */
    val restSeconds: Int? = null,
    /** The cue this slot prescribes, or null (P3.8). */
    val techniqueNote: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
