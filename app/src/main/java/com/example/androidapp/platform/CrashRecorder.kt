package com.example.androidapp.platform

import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.nowEpochMillis

/**
 * Records uncaught exceptions to disk (ROADMAP F11).
 *
 * Local only, by design. The app has no `INTERNET` permission and the quality bar
 * makes that a line rather than an oversight, so nothing is transmitted: a crash is
 * written to a file the user can read, and travels no further than an export they
 * choose to make. The honest limitation is that nothing arrives without them
 * looking — fine for an app whose only user is the person holding the phone.
 *
 * Two rules this follows:
 *
 *  - **Chain to the previous handler.** Android's default handler is what prints the
 *    crash and lets the process die properly. Swallowing it would turn a loud crash
 *    into a silent hang.
 *  - **Never throw.** Everything here is wrapped: a crash handler that fails while
 *    handling a crash loses the very record it exists to keep.
 */
class CrashRecorder(
    private val store: CrashLogStore,
    private val timeSource: TimeSource,
    private val metadata: () -> CrashMetadata,
) {

    /**
     * Installs the handler. Idempotent enough to call once from `Application.onCreate`.
     *
     * Note what is *not* recorded: the exception message. See [CrashLog] — a message
     * can quote the value that caused the failure, which is how a weight or a note
     * ends up in a crash log.
     */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(throwable) }
            // Always hand over, whatever happened above.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun record(throwable: Throwable) {
        val info = runCatching(metadata).getOrDefault(
            CrashMetadata("unknown", 0, "unknown", "unknown"),
        )
        store.record(
            CrashLog(
                timestamp = timeSource.nowEpochMillis(),
                exceptionClass = throwable.javaClass.name,
                stackTrace = throwable.framesWithTypes(),
                appVersion = info.appVersion,
                versionCode = info.versionCode,
                androidVersion = info.androidVersion,
                deviceModel = info.deviceModel,
            ),
        )
    }
}

/**
 * The throwable chain's types and frames — deliberately **not** `printStackTrace()`.
 *
 * `printStackTrace` writes the exception *message* as its first line, and a message
 * is exactly where user data appears: `cannot parse weight 102.5 kg` puts a weight
 * in the crash log. Building from `stackTrace` keeps the diagnostic value — class
 * names, methods, line numbers, and the cause chain — while dropping the one field
 * that can carry a weight, a note or a measurement.
 *
 * The stack trace is also the only place these types and frames appear; the message
 * is not needed to locate the fault.
 */
private fun Throwable.framesWithTypes(): String = buildString {
    var current: Throwable? = this@framesWithTypes
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (depth > 0) append("Caused by: ")
        appendLine(current.javaClass.name)
        current.stackTrace.forEach { appendLine("\tat $it") }
        current = current.cause
        depth++
    }
}

/** A cycle in the cause chain would otherwise loop forever. */
private const val MAX_CAUSE_DEPTH = 8
