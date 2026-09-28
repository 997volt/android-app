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
     */
    suspend fun import(text: String): DataResult<ImportSummary>
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
