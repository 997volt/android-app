package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One occurrence trained with a different workout (ROADMAP P3.11).
 *
 * An **event keyed by slot and week**, the shape of a skip and a deload: "the rack is taken today,
 * do the dumbbell version" cannot be answered by editing the program, which changes every week
 * that references the template (N16, inherited by P3.3). [weekStart] is the Monday as an epoch day
 * for the same reason a skip's is: "which week" is a calendar question.
 *
 * A session started from [templateId] in that week settles the slot's occurrence, so the app stops
 * asking about a day that was trained — whatever it was trained with (P3.3's matching extended).
 */
@Entity(
    tableName = "program_substitutions",
    indices = [
        Index(value = ["slotId"]),
        Index(value = ["weekStart"]),
        // Room requires an index on every foreign-key column; the template is one (P3.11).
        Index(value = ["templateId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ProgramSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class ProgramSubstitutionEntity(
    @PrimaryKey val id: String,
    val slotId: String,
    /** Monday of the week this substitution belongs to, as `LocalDate.toEpochDay()` (P3.11). */
    val weekStart: Long,
    /** The workout that stands in for the slot's own template this week. */
    val templateId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
