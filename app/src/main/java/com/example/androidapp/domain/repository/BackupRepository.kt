package com.example.androidapp.domain.repository

import com.example.androidapp.domain.DataResult

/**
 * Backup and restore to a file the user chooses (ROADMAP P1.12).
 *
 * This exists because platform backup is off (F1): it is the *only* way data
 * survives an uninstall, a lost signing key or a signature change. Because the
 * app has no `INTERNET` permission, the destination is always a local file the
 * user picked through the Storage Access Framework — never a server.
 */
interface BackupRepository {

    /** The whole database as a JSON document. */
    suspend fun export(): DataResult<String>

    /**
     * Brings the database up to what [text] describes.
     *
     * The rule is **"bring back what is gone; never overwrite what is there"**,
     * which gives three cases per row:
     *
     *  - **Missing locally** → inserted.
     *  - **Present but deleted** → restored from the file, `deletedAt` cleared.
     *  - **Present and live** → left alone. A local edit could be newer than the
     *    file, and guessing wrong there loses work the user did after exporting.
     *
     * The middle case is why this cannot be insert-only. A delete in this app is a
     * *soft* delete, so the row and its id survive: an insert-only import skipped
     * exactly the rows a restore is for, and reported "nothing to do" — the bug
     * that motivated this shape.
     *
     * The file's metric targets (N39) follow the same rule even though they are settings rather
     * than rows: one this device lacks comes back, one it already has is left alone. The other
     * settings are device preferences and are not in the file at all (N21).
     */
    suspend fun import(text: String): DataResult<ImportSummary>

    /**
     * Deletes everything the user made and puts the seeded library back (ROADMAP N18).
     *
     * **Hard, not soft.** Every other delete in this app sets `deletedAt`, which is right
     * for a mistaken tap and wrong for "start over": a soft-deleted row survives, and an
     * export taken afterwards would still carry it. This is the one action that removes
     * rows.
     *
     * It leaves the library the app ships (those are app content, not the user's), the
     * database file itself, and any file the user has exported — that one belongs to them
     * and lives outside the app, which is exactly why the screen offers an export first.
     */
    suspend fun clearAllUserData(): DataResult<Unit>
}

/**
 * What an import actually did, so the UI can say something specific rather than
 * "done" — which reads as failure when nothing was added because it was already
 * there.
 */
data class ImportSummary(
    /** Rows the file had and the database did not. */
    val added: Int,
    /** Rows that were deleted locally and came back from the file. */
    val restored: Int,
) {
    val total: Int get() = added + restored

    /** Everything in the file was already present and live. A success, not a no-op. */
    val wasAlreadyComplete: Boolean get() = total == 0
}
