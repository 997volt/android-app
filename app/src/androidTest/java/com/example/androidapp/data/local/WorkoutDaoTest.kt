package com.example.androidapp.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the session DAO (ROADMAP P1.2, P1.8).
 *
 * The idempotency test is the important one: two open sessions would make "the
 * active session" ambiguous in every screen that asks.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var dao: WorkoutDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        dao = database.workoutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun findOrCreate_isIdempotent_returningTheSameOpenSession() = runTest {
        val first = dao.findOrCreateActiveSession(id = "session-a", now = 1_000L)
        val second = dao.findOrCreateActiveSession(id = "session-b", now = 2_000L)

        assertEquals("session-a", first.session.id)
        assertEquals("a second call must not open a second workout", "session-a", second.session.id)
        assertEquals(1, dao.sessionCount())
        // N4's prompt fires from this flag, so it has to be exact: only the call
        // that actually inserted the row reports a new session.
        assertEquals(true, first.created)
        assertEquals(false, second.created)
    }

    /** The active session, for the tests that only need a row to work with. */
    private suspend fun WorkoutDao.startSession(id: String = "s", now: Long = 1_000L) =
        findOrCreateActiveSession(id, now).session

    @Test
    fun finishingASession_clearsTheActiveSession() = runTest {
        val session = dao.startSession()
        assertNotNull(dao.findActiveSession())

        val updated = dao.markFinished(id = session.id, at = 2_000L)

        assertEquals(1, updated)
        assertNull("a finished session is no longer active", dao.findActiveSession())
        assertNull(dao.observeActiveSession().first())
    }

    @Test
    fun updateReadinessNote_setsAndClearsTheNote() = runTest {
        val session = dao.startSession()

        assertEquals(
            1,
            dao.updateReadinessNote(id = session.id, note = "Shoulders sore", at = 2_000L),
        )
        assertEquals(
            "Shoulders sore",
            dao.findSession(session.id)?.readinessNote,
        )

        // A null note is how the header clears the field.
        assertEquals(1, dao.updateReadinessNote(id = session.id, note = null, at = 3_000L))
        assertNull(dao.findSession(session.id)?.readinessNote)
    }

    @Test
    fun updateReadinessNote_reportsZeroRowsForAnUnknownSession() = runTest {
        assertEquals(0, dao.updateReadinessNote(id = "nope", note = "x", at = 1L))
    }

    @Test
    fun markFinished_reportsZeroRowsForAnUnknownSession() = runTest {
        // This is what lets the repository answer DataError.NotFound instead of
        // pretending the write succeeded.
        assertEquals(0, dao.markFinished(id = "nope", at = 1L))
    }

    @Test
    fun softDeletedSession_isNotTheActiveSession() = runTest {
        val session = dao.startSession()

        assertEquals(1, dao.softDeleteSession(id = session.id, at = 2_000L))

        assertNull(dao.findActiveSession())
    }

    @Test
    fun sessionExercises_comeBackInPositionOrder_notInsertionOrder() = runTest {
        val session = dao.startSession()
        insertExercise(session.id, "back-squat", position = 0)
        insertExercise(session.id, "deadlift", position = 2)
        insertExercise(session.id, "barbell-row", position = 1)

        val ordered = dao.observeSessionExerciseDetails(session.id).first()

        assertEquals(listOf("back-squat", "barbell-row", "deadlift"), ordered.map { it.exerciseId })
    }

    @Test
    fun maxPosition_isMinusOneOnAnEmptySession() = runTest {
        // Callers add 1 to get the next slot, so an empty session must yield -1
        // rather than 0.
        val session = dao.startSession()

        assertEquals(-1, dao.maxPosition(session.id))

        insertExercise(session.id, "back-squat", position = 0)
        assertEquals(0, dao.maxPosition(session.id))
    }

    @Test
    fun removingASessionExercise_hidesItFromTheQuery() = runTest {
        val session = dao.startSession()
        insertExercise(session.id, "back-squat", position = 0)

        val row = dao.observeSessionExerciseDetails(session.id).first().single()
        assertEquals(1, dao.softDeleteSessionExercise(id = row.id, at = 5L))

        assertEquals(emptyList<SessionExerciseDetail>(), dao.observeSessionExerciseDetails(session.id).first())
    }

    @Test
    fun countLoggableSessionExercise_requiresALiveExerciseInAnOpenSession() = runTest {
        val session = dao.startSession()
        insertExercise(session.id, "back-squat", position = 0)
        val rowId = dao.observeSessionExerciseDetails(session.id).first().single().id

        assertEquals(1, dao.countLoggableSessionExercise(rowId))

        // A finished session must not accept more sets: a stale screen could
        // otherwise file history the user can never reach.
        dao.markFinished(id = session.id, at = 2_000L)

        assertEquals(0, dao.countLoggableSessionExercise(rowId))
    }

    @Test
    fun countLoggableSessionExercise_excludesARemovedExercise() = runTest {
        val session = dao.startSession()
        insertExercise(session.id, "back-squat", position = 0)
        val rowId = dao.observeSessionExerciseDetails(session.id).first().single().id

        dao.softDeleteSessionExercise(id = rowId, at = 2_000L)

        assertEquals(0, dao.countLoggableSessionExercise(rowId))
    }

    private suspend fun insertExercise(sessionId: String, exerciseId: String, position: Int) {
        // Seed the library row first: session_exercises has a foreign key to it.
        database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = exerciseId,
                    name = exerciseId,
                    primaryMuscle = com.example.androidapp.domain.model.MuscleGroup.BACK,
                    secondaryMuscles = emptyList(),
                    equipment = com.example.androidapp.domain.model.Equipment.BARBELL,
                    movementPattern = com.example.androidapp.domain.model.MovementPattern.HINGE,
                    isCustom = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                ),
            ),
        )
        dao.insertSessionExercise(
            SessionExerciseEntity(
                id = "$sessionId-$exerciseId",
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = position,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null,
            ),
        )
    }
}
