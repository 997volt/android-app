package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.WorkoutSessionEntity
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.repository.StartedSession
import com.example.androidapp.domain.ZoneOffsetSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Repeating the last workout, against a real database (ROADMAP N29).
 *
 * Two of this feature's three edge cases are SQL rules — a library row deleted since is skipped, and
 * the same exercise twice stays twice — so a fake cannot hold them: it would agree with whatever the
 * query was supposed to do. These seed rows and read back what the app would show.
 */
class RepeatLastSessionTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutRepository

    private val clock = TimeSource { Instant.parse("2026-10-01T08:00:00Z") }

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomWorkoutRepository(database, clock, ZoneOffsetSource { 0 })
        database.exerciseDao().insertAll(
            listOf(exercise("back-squat"), exercise("bench-press"), exercise("row")),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun itCopiesTheExercises_inTheOrderTheyWerePerformed() = runTest {
        seedFinishedWorkout("back-squat", "bench-press")

        val started = opened()
        assertEquals(
            listOf("back-squat", "bench-press"),
            repository.observeSessionExercises(started).first().map { it.exerciseId },
        )
    }

    @Test
    fun anExerciseDeletedFromTheLibrary_isSkipped_whileTheRestRepeat() = runTest {
        seedFinishedWorkout("back-squat", "bench-press")
        // Deleted from the library since the workout was performed.
        database.exerciseDao().softDelete("bench-press", deletedAt = 2_000L)

        val started = opened()
        assertEquals(
            "the surviving exercise repeats, the deleted one is skipped",
            listOf("back-squat"),
            repository.observeSessionExercises(started).first().map { it.exerciseId },
        )
    }

    @Test
    fun anExercisePerformedTwice_repeatsTwice() = runTest {
        seedFinishedWorkout("back-squat", "bench-press", "back-squat")

        val started = opened()
        assertEquals(
            "what was performed twice was performed twice",
            listOf("back-squat", "bench-press", "back-squat"),
            repository.observeSessionExercises(started).first().map { it.exerciseId },
        )
    }

    @Test
    fun withNoFinishedWorkout_itOpensAnEmptySession() = runTest {
        // The action is hidden in this case (a screen rule), and the repository's answer is an empty
        // session rather than a failure: there is simply nothing to copy.
        val started = opened()
        assertTrue(repository.observeSessionExercises(started).first().isEmpty())
    }

    /** Opens a session by repeating, and returns its id. */
    private suspend fun opened(): String {
        val result = repository.repeatLastSession()
        assertTrue("repeating opens a session: $result", result is DataResult.Success)
        return (result as DataResult.Success<StartedSession>).data.id
    }

    /**
     * A workout holding [exerciseIds] in order — finished unless [finishedAt] says otherwise, which is
     * how the query's "only finished" rule becomes testable rather than assumed.
     */
    private suspend fun seedFinishedWorkout(
        vararg exerciseIds: String,
        finishedAt: Long? = 2_000L,
    ) {
        val dao = database.workoutDao()
        dao.insertSession(
            WorkoutSessionEntity(
                id = "past",
                startedAt = 1_000L,
                finishedAt = finishedAt,
                notes = null,
                restEndsAt = null,
                readinessNote = null,
                createdAt = 1_000L,
                updatedAt = 2_000L,
                deletedAt = null,
            ),
        )
        exerciseIds.forEachIndexed { index, exerciseId ->
            dao.insertSessionExercise(
                SessionExerciseEntity(
                    id = "past-$index",
                    sessionId = "past",
                    exerciseId = exerciseId,
                    position = index,
                    restSeconds = null,
                    techniqueNote = null,
                    finishedAt = null,
                    supersetGroup = null,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    deletedAt = null,
                ),
            )
        }
    }

    private fun exercise(id: String) = ExerciseEntity(
        id = id,
        name = id,
        primaryMuscle = MuscleGroup.QUADS,
        secondaryMuscles = emptyList(),
        equipment = Equipment.BARBELL,
        movementPattern = MovementPattern.SQUAT,
        isCustom = false,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )

    @Test
    fun openingASession_recordsTheZoneItIsIn() = runTest {
        // ROADMAP N25: the capture, on a device. A session opened while the source says Tokyo (+540)
        // is stored as Tokyo, which is what every screen then reads back.
        val tokyo = RoomWorkoutRepository(database, clock, ZoneOffsetSource { 540 })

        val started = (tokyo.startOrResumeSession() as DataResult.Success<StartedSession>).data

        assertEquals(540, database.workoutDao().findSession(started.id)?.zoneOffsetMinutes)
    }

    @Test
    fun resumingASession_keepsTheZoneItOpenedIn() = runTest {
        // The "captured once" rule, and the one a DST-spanning session depends on: opening in Tokyo
        // and resuming hours later from a device that has since moved must NOT rewrite the row. The
        // DAO returns early for an existing session, which is where this is enforced.
        val tokyo = RoomWorkoutRepository(database, clock, ZoneOffsetSource { 540 })
        val started = (tokyo.startOrResumeSession() as DataResult.Success<StartedSession>).data

        val home = RoomWorkoutRepository(database, clock, ZoneOffsetSource { 0 })
        home.startOrResumeSession()

        assertEquals(
            "the session still remembers Tokyo",
            540,
            database.workoutDao().findSession(started.id)?.zoneOffsetMinutes,
        )
    }

    @Test
    fun itCarriesTheRest_theNote_andTheGrouping() = runTest {
        // ROADMAP B41: only the movement used to be copied, so a repeated superset lost its grouping —
        // and with a null group the round logic short-circuits, degrading the pair into unrelated
        // exercises resting separately, which is what N24 exists to prevent.
        val dao = database.workoutDao()
        seedFinishedWorkout("back-squat", "bench-press")
        // The past workout prescribed a 3-minute rest, a note, and performed the two as a superset.
        database.openHelper.writableDatabase.execSQL(
            """
            UPDATE session_exercises
            SET restSeconds = 180, techniqueNote = 'pause at the bottom', supersetGroup = 1
            WHERE sessionId = 'past'
            """.trimIndent(),
        )

        val started = opened()
        val copied = repository.observeSessionExercises(started).first()

        assertEquals(listOf(180, 180), copied.map { it.restSeconds })
        assertEquals(
            listOf("pause at the bottom", "pause at the bottom"),
            copied.map { it.techniqueNote },
        )
        assertEquals("still a superset", listOf(1, 1), copied.map { it.supersetGroup })
    }

    @Test
    fun theQuery_ignoresAWorkoutStillInProgress() = runTest {
        // ROADMAP B43: the existing "no finished workout" case seeds no rows at all, so dropping
        // `finishedAt IS NOT NULL` would still have passed it. This seeds a workout with exercises and
        // no finish time — and asks the **query** directly, because through the repository it cannot be
        // observed: a session with no finish time *is* the open session, so repeating resumes it,
        // which is correct and a different rule entirely. My first version of this test asserted
        // through both and caught my own misunderstanding rather than a defect.
        seedFinishedWorkout("back-squat", "bench-press", finishedAt = null)

        assertTrue(
            "a workout still in progress is not the last workout",
            database.sessionExerciseDao().lastFinishedSessionExercises().isEmpty(),
        )
    }
}
