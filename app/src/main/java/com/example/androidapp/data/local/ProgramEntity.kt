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
 * [isActive] means "home follows this program". It is a flag on the row rather than an id
 * in settings because a program is training data — it rides in the backup like a plan or a
 * measurement does — and **more than one row may carry it** (ROADMAP P3.12, amending P3.3):
 * a lifting block and a conditioning one are two schedules at once, and the union is what
 * every reader downstream now takes. With no live row active the home screen falls back to
 * the template pins it read before programs existed.
 *
 * [position] is the order active programs are followed in — authored by moving them rather
 * than derived from a name or a creation time (P3.12), so "which one comes first" is a
 * decision the lifter made.
 */
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Whether home follows this program; more than one may be true (P3.12). */
    val isActive: Boolean = false,
    /** The program's place in the list and in the union, low first (P3.12). */
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
