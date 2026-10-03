package com.example.androidapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named, ordered list of training slots (ROADMAP P3.3).
 *
 * Written **sync-ready** like every other table: a stable string id, and
 * `createdAt`/`updatedAt`/`deletedAt` so a future sync (P4.9) can resolve conflicts
 * and propagate deletions without a migration. Nothing reads those columns yet.
 *
 * [isActive] is the "one active program only" rule. It is a flag on the row rather
 * than an id in settings because a program is training data — it rides in the backup
 * like a plan or a measurement does — and because a boolean makes "at most one" a
 * property of a single statement rather than an invariant two stores have to agree
 * on. With no live row active the home screen falls back to the template pins it
 * read before this feature existed.
 */
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** The program the home screen follows, or false for one on the shelf (P3.3). */
    val isActive: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
