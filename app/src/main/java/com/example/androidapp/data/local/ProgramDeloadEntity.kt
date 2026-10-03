package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A week a program's lifter marked as a deload (ROADMAP P3.10).
 *
 * An **event keyed by program and week**, never a dated plan (N16) and never a calculated week: a
 * deload is a decision about how the block is going, not something the app infers. The shape is
 * [ProgramSkipEntity]'s — the week's Monday as an epoch day rather than an instant, because "which
 * week" is a calendar question and a millisecond would drag a timezone into it.
 *
 * A deload week's scheduled occurrences are **neither done, skipped nor missed**: a deliberate
 * back-off must not read as a failure. Its sessions still mark their days, and the missed-day
 * question still asks, because the week is exempt from judgement rather than from the schedule.
 */
@Entity(
    tableName = "program_deloads",
    indices = [Index(value = ["programId"]), Index(value = ["weekStart"])],
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProgramDeloadEntity(
    @PrimaryKey val id: String,
    val programId: String,
    /** Monday of the deloaded week, as `LocalDate.toEpochDay()` (P3.10). */
    val weekStart: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
