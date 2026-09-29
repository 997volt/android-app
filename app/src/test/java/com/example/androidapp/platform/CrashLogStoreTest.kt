package com.example.androidapp.platform

import com.example.androidapp.domain.TimeSource
import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for local crash capture (ROADMAP F11).
 *
 * `CrashLogStore` takes a directory rather than a `Context` precisely so this runs
 * in milliseconds with a temp folder — no device, no Robolectric.
 *
 * The redaction test is the one that earns its place: the quality bar says a crash
 * record must never carry set values, notes or body measurements, and the design
 * answer is to store no exception *message* at all (a parse failure quotes the value
 * it choked on). That is a claim, so it is asserted rather than trusted.
 */
class CrashLogStoreTest {

    private lateinit var directory: File
    private lateinit var store: CrashLogStore

    private val clock = TimeSource { Instant.parse("2026-09-29T12:00:00Z") }

    private val metadata = CrashMetadata(
        appVersion = "1.2",
        versionCode = 3,
        androidVersion = "16",
        deviceModel = "Pixel 6",
    )

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("crash-logs-test").toFile()
        store = CrashLogStore(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun record(throwable: Throwable): CrashRecorder {
        val recorder = CrashRecorder(store, clock, { metadata })
        recorder.install()
        // Drive the installed handler the way the JVM would, without killing the test.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        try {
            previous!!.uncaughtException(Thread.currentThread(), throwable)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(null)
        }
        return recorder
    }

    @Test
    fun anUncaughtException_isRecorded() {
        record(IllegalStateException("boom"))

        val log = store.latest()
        assertNotNull("nothing was recorded", log)
        assertEquals(IllegalStateException::class.java.name, log!!.exceptionClass)
        assertEquals(Instant.parse("2026-09-29T12:00:00Z").toEpochMilli(), log.timestamp)
    }

    @Test
    fun theRecordCarriesTheBuildAndDeviceThatProducedIt() {
        record(RuntimeException("boom"))

        val log = store.latest()!!
        assertEquals("1.2", log.appVersion)
        assertEquals(3, log.versionCode)
        assertEquals("16", log.androidVersion)
        assertEquals("Pixel 6", log.deviceModel)
    }

    @Test
    fun noUserDataLeaksIntoTheRecord() {
        // The message is exactly where user data would appear: this is what a weight
        // parse failure looks like. It must not survive into the log.
        record(IllegalArgumentException("cannot parse weight 102.5 kg for set 7 of Bench"))

        val onDisk = directory.listFiles()!!.joinToString("\n") { it.readText() }

        assertFalse("a weight leaked into the crash log", onDisk.contains("102.5"))
        assertFalse("a note leaked", onDisk.contains("Bench"))
        assertTrue("the stack trace should still be there", onDisk.contains("CrashLogStoreTest"))
    }

    @Test
    fun severalCrashes_areReturnedNewestFirst() {
        store.record(logAt(1_000L, "a.First"))
        store.record(logAt(3_000L, "c.Third"))
        store.record(logAt(2_000L, "b.Second"))

        assertEquals(
            listOf("c.Third", "b.Second", "a.First"),
            store.all().map { it.exceptionClass },
        )
    }

    @Test
    fun anUnreadableFile_isSkipped_ratherThanLosingTheRest() {
        store.record(logAt(1_000L, "a.First"))
        File(directory, "crash-corrupt.json").writeText("this is not json")

        assertEquals(1, store.all().size)
    }

    @Test
    fun clear_removesEverything() {
        store.record(logAt(1_000L, "a.First"))
        store.clear()

        assertTrue(store.all().isEmpty())
        assertEquals(null, store.latest())
    }

    @Test
    fun recording_survivesAMissingDirectory() {
        val fresh = CrashLogStore(File(directory, "nested/deeper"))

        assertTrue(fresh.record(logAt(1_000L, "a.First")))
        assertEquals(1, fresh.all().size)
    }

    @Test
    fun thePreviousHandlerStillRuns_soCrashesStayLoud() {
        // Swallowing the default handler would turn a crash into a silent hang.
        val seen = mutableListOf<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, t -> seen += t }
        try {
            val recorder = CrashRecorder(store, clock, { metadata })
            recorder.install()
            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("boom"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }

        assertEquals("the chained handler was not called", 1, seen.size)
        assertNotNull(store.latest())
    }

    private fun logAt(timestamp: Long, className: String) = CrashLog(
        timestamp = timestamp,
        exceptionClass = className,
        stackTrace = "at com.example.Whatever.method(File.kt:1)",
        appVersion = "1.2",
        versionCode = 3,
        androidVersion = "16",
        deviceModel = "Pixel 6",
    )
}
