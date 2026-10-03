package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recorded skip: this slot's occurrence in this week was passed over (ROADMAP P3.3).
 *
 * An **event keyed by slot and week**, never a boolean on the slot. The same weekday
 * recurs, so a flag would need resetting and would be wrong the moment two weeks in a
 * row were missed. [weekStart] is the Monday of the week, as an epoch day — a day
 * rather than an instant, because "which week" is a calendar question and a millisecond
 * would drag a timezone into it.
 *
 * These rows are also exactly what P3.5 needs: without them "skipped" is unknowable,
 * because a standing weekday pin carries no history.
 */
@Entity(
    tableName = "program_skips",
    indices = [Index(value = ["slotId"]), Index(value = ["weekStart"])],
    foreignKeys = [
        ForeignKey(
            entity = ProgramSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProgramSkipEntity(
    @PrimaryKey val id: String,
    val slotId: String,
    /** Monday of the week this skip belongs to, as `LocalDate.toEpochDay()` (P3.3). */
    val weekStart: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
