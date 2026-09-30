package com.example.androidapp.data.local

import java.time.DayOfWeek

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named, reusable workout (ROADMAP N3).
 *
 * Written **sync-ready** like every other table: a stable string id, and
 * `createdAt`/`updatedAt`/`deletedAt` so a future sync (P4.9) can resolve conflicts
 * and propagate deletions without a migration. Nothing reads those columns yet.
 *
 * The soft delete matters here for the same reason it does for exercises: a
 * template the user deletes is still part of the data an export carries.
 */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    /**
     * The weekday this plan belongs to, or null for an unscheduled one (ROADMAP N16).
     *
     * Stored by name like every other enum: reordering `DayOfWeek` could never
     * reinterpret a row, and a name is what a person reading the database expects.
     * Several templates may share a day.
     */
    val weekday: DayOfWeek? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
