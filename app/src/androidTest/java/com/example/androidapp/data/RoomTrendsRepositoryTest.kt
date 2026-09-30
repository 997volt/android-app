package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.WorkoutSessionEntity
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The trends repository against a real database (ROADMAP B10).
 *
 * This is the only place RPE halves are divided back into the points a chart shows, and
 * nothing referenced it: the DAO test asserts the average *in halves* and the ViewModel
 * test is handed values that are already converted, so a wrong divisor would have
 * mislabelled every RPE chart in silence.
 */
@RunWith(AndroidJUnit4::class)
class RoomTrendsRepositoryTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomTrendsRepository

    private val clock = TimeSource { Instant.parse("2026-09-30T08:00:00Z") }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomTrendsRepository(database)
        runTest { database.exerciseDao().insertAll(listOf(exercise())) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theAverages_areInPoints_notHalves() = runTest {
        // 9.5 and 8.0 as recorded, which is 19 and 16 halves. The chart must read 9.5
        // and 8.0 — half of that is a wrong divisor, and double it is the stored count.
        seedSession(id = "s1", startedAt = 1_000L, rpeHalves = listOf(19, 16))

        val point = (repository.observeTrends(limit = 10).first() as DataResult.Success)
            .data
            .single()

        assertEquals("19 and 16 halves average to 17.5, which is 8.75 points", 8.75, point.averageRpe!!, 0.0001)
    }

    @Test
    fun aWholeRpe_averagesToItsPointValue() = runTest {
        seedSession(id = "s1", startedAt = 1_000L, rpeHalves = listOf(18, 18))

        val point = (repository.observeTrends(limit = 10).first() as DataResult.Success)
            .data
            .single()

        assertEquals(9.0, point.averageRpe!!, 0.0001)
    }

    @Test
    fun theRatings_areNotHalved() = runTest {
        // Muscle feel and joint pain are a different, whole-number scale (N8): dividing
        // them would be the same class of bug in the other direction.
        seedSession(id = "s1", startedAt = 1_000L, rpeHalves = listOf(16), muscleFeel = 8, jointPain = 4)

        val point = (repository.observeTrends(limit = 10).first() as DataResult.Success)
            .data
            .single()

        assertEquals(8.0, point.averageMuscleFeel!!, 0.0001)
        assertEquals(4.0, point.averageJointPain!!, 0.0001)
    }

    @Test
    fun aWorkoutRatedForOneSignalOnly_stillAppears() = runTest {
        // The union-merge: RPE and the ratings arrive from two queries, and a session
        // that has one but not the other is a point on the chart, not a gap.
        seedSession(id = "s1", startedAt = 1_000L, rpeHalves = emptyList(), muscleFeel = 8)

        val points = (repository.observeTrends(limit = 10).first() as DataResult.Success).data

        assertEquals(1, points.size)
        assertEquals(null, points.single().averageRpe)
        assertEquals(8.0, points.single().averageMuscleFeel!!, 0.0001)
    }

    @Test
    fun nothingRated_isNoPoints_ratherThanAFailure() = runTest {
        seedSession(id = "s1", startedAt = 1_000L, rpeHalves = emptyList())

        val result = repository.observeTrends(limit = 10).first()

        assertTrue(result is DataResult.Success)
        assertEquals(emptyList<Any>(), (result as DataResult.Success).data)
    }

    /** A session with one exercise and one set per RPE value. */
    private suspend fun seedSession(
        id: String,
        startedAt: Long,
        rpeHalves: List<Int>,
        muscleFeel: Int? = null,
        jointPain: Int? = null,
    ) {
        database.workoutDao().insertSession(
            WorkoutSessionEntity(
                id = id,
                startedAt = startedAt,
                finishedAt = startedAt + 1,
                notes = null,
                restEndsAt = null,
                readinessNote = null,
                createdAt = startedAt,
                updatedAt = startedAt,
                deletedAt = null,
            ),
        )
        database.workoutDao().insertSessionExercise(
            SessionExerciseEntity(
                id = "$id-se",
                sessionId = id,
                exerciseId = "back-squat",
                position = 0,
                muscleFeel = muscleFeel,
                jointPain = jointPain,
                createdAt = startedAt,
                updatedAt = startedAt,
                deletedAt = null,
            ),
        )
        rpeHalves.forEachIndexed { index, halves ->
            database.workoutDao().insertSet(
                SetEntryEntity(
                    id = "$id-set$index",
                    sessionExerciseId = "$id-se",
                    setIndex = index,
                    reps = 5,
                    weightGrams = 100_000L,
                    setType = SetType.NORMAL,
                    rpeHalves = halves,
                    completedAt = startedAt,
                    createdAt = startedAt,
                    updatedAt = startedAt,
                    deletedAt = null,
                ),
            )
        }
    }

    private fun exercise() = ExerciseEntity(
        id = "back-squat",
        name = "Back Squat",
        primaryMuscle = MuscleGroup.QUADS,
        secondaryMuscles = emptyList(),
        equipment = Equipment.BARBELL,
        movementPattern = MovementPattern.SQUAT,
        isCustom = false,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )
}
