package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.repository.StartedSession
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Session-level writes, end to end through the repository (ROADMAP N4).
 *
 * The readiness prompt is driven by [StartedSession.isNew], so that flag is
 * asserted exactly rather than assumed: getting it wrong would either nag on every
 * resume or never ask at all.
 */
@RunWith(AndroidJUnit4::class)
class RoomWorkoutSessionTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutRepository

    private val clock = TimeSource { Instant.parse("2026-09-29T08:00:00Z") }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomWorkoutRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aFreshlyOpenedSession_isNew_andKeepsItsReadinessNote() = runTest {
        val started = start()
        assertTrue(started.isNew)

        assertTrue(repository.setReadinessNote(started.id, "Shoulders still sore") is DataResult.Success)

        assertEquals(
            "Shoulders still sore",
            repository.observeSession(started.id).first()?.readinessNote,
        )
    }

    @Test
    fun resuming_doesNotReportANewSession() = runTest {
        val first = start()
        val second = start()

        assertEquals("the open session must be reused", first.id, second.id)
        // This is what stops the prompt re-appearing on every resume (N4).
        assertFalse(second.isNew)
    }

    @Test
    fun aBlankNote_isStoredAsNull_ratherThanAnEmptyString() = runTest {
        val started = start()
        repository.setReadinessNote(started.id, "Shoulders sore")
        assertEquals(
            "Shoulders sore",
            repository.observeSession(started.id).first()?.readinessNote,
        )

        repository.setReadinessNote(started.id, "   ")

        assertNull(
            "clearing the field must leave one representation of nothing",
            repository.observeSession(started.id).first()?.readinessNote,
        )
    }

    @Test
    fun setReadinessNote_onAGoneSession_isNotFound() = runTest {
        val failure = repository.setReadinessNote("no-such-session", "x") as DataResult.Failure

        assertEquals(DataError.NotFound, failure.error)
    }

    private suspend fun start(): StartedSession =
        (repository.startOrResumeSession() as DataResult.Success).data
}
