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
     * Merges [text] into the database.
     *
     * Additive by design, never destructive: ids are stable, so anything already
     * present is skipped and nothing on the device is overwritten. That makes the
     * operation idempotent and means a mistaken import cannot cost the user data.
     */
    suspend fun import(text: String): DataResult<ImportSummary>
}

/**
 * What an import actually did, so the UI can say something specific rather than
 * "done" — which reads as failure when nothing was added because it was already
 * there.
 */
data class ImportSummary(
    val exercises: Int,
    val sessions: Int,
    val sessionExercises: Int,
    val sets: Int,
) {
    val total: Int get() = exercises + sessions + sessionExercises + sets

    /** Everything in the file was already present. A success, not a no-op. */
    val wasAlreadyComplete: Boolean get() = total == 0
}
