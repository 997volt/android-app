package com.example.androidapp.data.local

import java.time.DayOfWeek

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One slot of a program: a template, and the weekday it is done on (ROADMAP P3.3).
 *
 * [weekday] is nullable and means the same thing it does on a template: a slot with
 * no day is **order-only**. It is part of the program's running order — "which one is
 * next" reads it — but it can never be missed, because there is no day to miss.
 *
 * [position] is the program's order, the thing a standing weekday pin cannot express:
 * a pin says what happens on a Tuesday but nothing orders the pins against each other.
 * A slot is deleted softly for the same reason a template is: an export carries it.
 */
@Entity(
    tableName = "program_slots",
    indices = [Index(value = ["programId"]), Index(value = ["templateId"])],
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
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
data class ProgramSlotEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val templateId: String,
    val position: Int,
    val weekday: DayOfWeek? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
