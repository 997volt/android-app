package com.example.androidapp.data.transfer

import com.example.androidapp.domain.InvalidInputException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads and writes the backup file (ROADMAP P1.12).
 *
 * Pure: it takes and returns strings, so the format is covered by fast JVM tests
 * and the file IO stays in the UI layer where the Storage Access Framework lives.
 */
object BackupCodec {

    /**
     * The version this build writes, and the newest it will read.
     *
     * Bump it whenever a [BackupFile] field changes meaning or is removed, so an
     * older app refuses the file instead of dropping data it cannot represent.
     */
    const val CURRENT_SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        // A file from a newer app may carry fields this build has never heard of.
        // Ignoring them is only safe because schemaVersion is checked first.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(file)

    /**
     * Parses [text], or throws [InvalidInputException] with a message worth showing.
     *
     * The version gate is explicit rather than best-effort: a file from a newer app
     * may hold data this build cannot represent, and importing it partially would
     * look like success while silently losing whatever it did not understand.
     */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString<BackupFile>(text)
        } catch (malformed: SerializationException) {
            // The cause is kept, not discarded: the user sees the plain message,
            // while a debugger or a bug report still has the parser's complaint.
            throw InvalidInputException("That does not look like a backup file.", malformed)
        }

        if (file.schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw InvalidInputException(
                "That backup was written by a newer version of the app " +
                    "(file v${file.schemaVersion}, this build reads v$CURRENT_SCHEMA_VERSION).",
            )
        }
        return file
    }
}
