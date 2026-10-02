package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.BodyMeasurement
import com.example.androidapp.domain.model.at
import com.example.androidapp.domain.model.TapeSite
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Measurements against a real database (ROADMAP N32).
 *
 * The two rules worth this suite are the ones DECISIONS.md settled, and both are the kind a fake would
 * agree with whatever it was told: a day has one entry, and a site nobody measured stays blank.
 */
class MeasurementRepositoryTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomMeasurementRepository

    private val clock = TimeSource { Instant.parse("2026-10-02T09:00:00Z") }

    /** Morning and evening of the same local day, whatever zone the device is in. */
    private val today = LocalDate.now(ZoneId.systemDefault())
    private fun localHour(hour: Int): Instant =
        today.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomMeasurementRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savingTwiceOnOneDay_editsTheFirstEntry() = runTest {
        repository.save(entry(at = localHour(8), weightGrams = 82_000L))
        repository.save(entry(at = localHour(20), weightGrams = 81_500L))

        val all = repository.observeAll().first()
        assertEquals("one entry for the day, not two", 1, all.size)
        assertEquals("and it holds the later reading", 81_500L, all.single().weightGrams)
    }

    @Test
    fun savingOnAnotherDay_addsAnEntry() = runTest {
        repository.save(entry(at = localHour(8), weightGrams = 82_000L))
        val tomorrow = localHour(8).plusSeconds(86_400L)
        repository.save(entry(at = tomorrow, weightGrams = 81_000L))

        assertEquals(2, repository.observeAll().first().size)
    }

    @Test
    fun aSiteThatWasNotMeasured_staysBlank() = runTest {
        repository.save(entry(at = localHour(8), weightGrams = 82_000L, waistMm = 860L))
        // The same day, this time with no tape at all: it is a correction, and the waist it did not
        // measure must not survive from the entry it replaces.
        repository.save(entry(at = localHour(20), weightGrams = 81_500L))

        assertNull(
            "a site nobody measured draws no line",
            repository.observeAll().first().single().at(TapeSite.WAIST),
        )
    }

    @Test
    fun deleting_removesItFromTheList() = runTest {
        repository.save(entry(at = localHour(8), weightGrams = 82_000L))
        val saved = repository.observeAll().first().single()

        repository.delete(saved.id)

        assertEquals(emptyList<BodyMeasurement>(), repository.observeAll().first())
    }

    private fun entry(
        at: Instant,
        weightGrams: Long,
        waistMm: Long? = null,
    ) = BodyMeasurement(
        id = "",
        measuredAt = at,
        weightGrams = weightGrams,
        tape = listOfNotNull(waistMm?.let { TapeSite.WAIST to it }).toMap(),
    )
}
