package com.example.androidapp.platform

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One recorded crash (ROADMAP F11).
 *
 * **There is deliberately no exception message here.** A message is the one field
 * that can carry user data: a parse failure quotes the value it choked on, so
 * "cannot parse 102.5" would put a weight in the crash log — exactly what the
 * quality bar forbids. Class name plus stack trace is enough to find the fault;
 * frames carry types and methods, not values.
 *
 * No notes, no set values, no body measurements, and no `INTERNAL` permission story
 * to reason about: this never leaves the device unless the user exports it.
 */
@Serializable
data class CrashLog(
    val timestamp: Long,
    val exceptionClass: String,
    val stackTrace: String,
    val appVersion: String,
    val versionCode: Int,
    val androidVersion: String,
    val deviceModel: String,
)

/** The device and build facts worth attaching to a crash, kept injectable for tests. */
data class CrashMetadata(
    val appVersion: String,
    val versionCode: Int,
    val androidVersion: String,
    val deviceModel: String,
)

/**
 * Crash logs on disk, one small JSON file each.
 *
 * Files rather than the database on purpose: the crash may *be* a database failure,
 * and a recorder that needs Room to record a Room crash records nothing. One file
 * per crash also means a partial write can only lose the newest record.
 *
 * Takes a directory rather than a `Context` so the whole thing is testable on the
 * JVM with a temp folder — no device, no Robolectric.
 */
class CrashLogStore(private val directory: File) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Best-effort: a recorder that throws while recording is worse than none. */
    fun record(log: CrashLog): Boolean = runCatching {
        if (!directory.exists() && !directory.mkdirs()) return false
        File(directory, "crash-${log.timestamp}-${log.exceptionClass.hashCode()}.json")
            .writeText(json.encodeToString(log))
        true
    }.getOrDefault(false)

    /** Newest first. Unreadable files are skipped rather than throwing. */
    fun all(): List<CrashLog> = runCatching {
        directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<CrashLog>(file.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.timestamp }
            .orEmpty()
    }.getOrDefault(emptyList())

    /** The most recent crash, or null. What a "did we crash last time?" check wants. */
    fun latest(): CrashLog? = all().firstOrNull()

    fun clear(): Boolean = runCatching {
        directory.listFiles()?.forEach { it.delete() }
        true
    }.getOrDefault(false)
}
