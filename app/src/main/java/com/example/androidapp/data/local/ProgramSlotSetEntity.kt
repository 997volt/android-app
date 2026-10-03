package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.androidapp.domain.model.SetType

/**
 * One set a program slot prescribes (ROADMAP P3.8).
 *
 * Rows of their own so a slot can prescribe what it trains without touching the template every
 * week that references it (N16). The vocabulary is the plan's — [role], the split load, the rep
 * range, the RPE — with [targetPercentOf1Rm] the one addition, the load a template's planned set
 * cannot express. Every target is nullable for N14's reason: "work up to a heavy single" has no
 * weight to write down, and a zero is a claim the app cannot check.
 */
@Entity(
    tableName = "program_slot_sets",
    indices = [Index(value = ["slotExerciseId"])],
    foreignKeys = [
        ForeignKey(
            entity = ProgramSlotExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProgramSlotSetEntity(
    @PrimaryKey val id: String,
    val slotExerciseId: String,
    val setIndex: Int,
    val role: SetType,
    val targetWeightGrams: Long? = null,
    val targetAssistanceGrams: Long? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpeHalves: Int? = null,
    /** A percentage of the estimated one-rep max, or null (P3.8). */
    val targetPercentOf1Rm: Int? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
