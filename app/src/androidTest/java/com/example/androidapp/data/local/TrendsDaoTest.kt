package com.example.androidapp.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The trend aggregates (ROADMAP N13).
 *
 * Two things here are load-bearing. `AVG` must skip what was not recorded — a
 * workout that rated only joint pain must not drag the muscle-feel average down — and
 * the join to `set_entries` must not multiply rows, or the RPE average silently
 * becomes a weighted one.
 */
@RunWith(AndroidJUnit4::class)
class TrendsDaoTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var dao: TrendsDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        dao = database.trendsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rpe_isAveragedPerWorkout_overItsRatedSets() = runTest {
        seedWorkout(sessionId = "s1", startedAt = 1_000L, rpes = listOf(6, 8))
        seedWorkout(sessionId = "s2", startedAt = 2_000L, rpes = listOf(9))

        val rows = dao.observeRpeTrend(limit = 10).first()

        // Newest first: this is the order the LIMIT wants, not the chart's.
        assertEquals(listOf("s2", "s1"), rows.map { it.sessionId })
        assertEquals(9.0, rows[0].averageRpe)
        assertEquals(7.0, rows[1].averageRpe)
    }

    @Test
    fun setsFromSeveralExercises_averageTogether_withoutBeingCountedTwice() = runTest {
        // One workout, two exercises, three rated sets. A single statement averaging
        // the sets *and* the session exercises would multiply the rows and weight the
        // RPE by how many exercises happen to be rated — which is why the two
        // aggregates are separate queries merged in the repository.
        val sessionId = "s1"
        database.workoutDao().insertSession(session(id = sessionId, startedAt = 1_000L))
        exercise(sessionId, "se1")
        exercise(sessionId, "se2")
        database.workoutDao().insertSet(set("set1", "se1", rpe = 4))
        database.workoutDao().insertSet(set("set2", "se1", rpe = 8))
        database.workoutDao().insertSet(set("set3", "se2", rpe = 10))

        val row = dao.observeRpeTrend(limit = 10).first().single()

        assertEquals("(4 + 8 + 10) / 3", 7.333333333333333, row.averageRpe!!, 0.0001)
    }

    @Test
    fun aWorkoutWithNothingRated_doesNotAppear() = runTest {
        seedWorkout(sessionId = "s1", startedAt = 1_000L, rpes = emptyList())
        seedWorkout(sessionId = "s2", startedAt = 2_000L, rpes = listOf(7))

        assertEquals(listOf("s2"), dao.observeRpeTrend(limit = 10).first().map { it.sessionId })
        assertEquals(
            "a workout with no ratings is a gap, not a row of nulls",
            emptyList<String>(),
            dao.observeFeelTrend(limit = 10).first().map { it.sessionId },
        )
    }

    @Test
    fun anUnfinishedOrDeletedWorkout_isNotATrend() = runTest {
        seedWorkout(sessionId = "open", startedAt = 3_000L, rpes = listOf(7), finishedAt = null)
        seedWorkout(sessionId = "gone", startedAt = 4_000L, rpes = listOf(7), deleted = true)
        seedWorkout(sessionId = "real", startedAt = 5_000L, rpes = listOf(7))

        assertEquals(listOf("real"), dao.observeRpeTrend(limit = 10).first().map { it.sessionId })
    }

    @Test
    fun theFeelAverage_skipsWhatWasNotRecorded() = runTest {
        val sessionId = "s1"
        database.workoutDao().insertSession(session(id = sessionId, startedAt = 1_000L))
        exercise(sessionId, "se1", feel = 6, pain = null)
        exercise(sessionId, "se2", feel = 8, pain = 4)

        val row = dao.observeFeelTrend(limit = 10).first().single()

        assertEquals(7.0, row.averageMuscleFeel)
        // Only one of the two recorded pain; averaging over both would halve it.
        assertEquals(4.0, row.averageJointPain)
    }

    @Test
    fun aWorkoutThatRecordedOnlyPain_hasNoFeelAverage() = runTest {
        val sessionId = "s1"
        database.workoutDao().insertSession(session(id = sessionId, startedAt = 1_000L))
        exercise(sessionId, "se1", feel = null, pain = 3)

        val row = dao.observeFeelTrend(limit = 10).first().single()

        assertNull(row.averageMuscleFeel)
        assertEquals(3.0, row.averageJointPain)
    }

    @Test
    fun theWindow_keepsTheNewestWorkouts() = runTest {
        (1..12).forEach { index ->
            seedWorkout(sessionId = "s$index", startedAt = index * 1_000L, rpes = listOf(7))
        }

        val rows = dao.observeRpeTrend(limit = 10).first()

        assertEquals(10, rows.size)
        assertEquals("newest first, so the limit keeps the recent ones", "s12", rows.first().sessionId)
        assertEquals("s3", rows.last().sessionId)
    }

    private suspend fun seedWorkout(
        sessionId: String,
        startedAt: Long,
        rpes: List<Int>,
        finishedAt: Long? = startedAt + 1,
        deleted: Boolean = false,
    ) {
        database.workoutDao().insertSession(
            session(id = sessionId, startedAt = startedAt, finishedAt = finishedAt, deleted = deleted),
        )
        if (rpes.isEmpty()) return
        exercise(sessionId, "${sessionId}-se")
        rpes.forEachIndexed { index, rpe ->
            database.workoutDao().insertSet(set("$sessionId-set$index", "${sessionId}-se", rpe = rpe))
        }
    }

    private fun session(
        id: String,
        startedAt: Long,
        finishedAt: Long? = startedAt + 1,
        deleted: Boolean = false,
    ) = WorkoutSessionEntity(
        id = id,
        startedAt = startedAt,
        finishedAt = finishedAt,
        notes = null,
        restEndsAt = null,
        readinessNote = null,
        createdAt = startedAt,
        updatedAt = startedAt,
        deletedAt = if (deleted) startedAt else null,
    )

    private suspend fun exercise(
        sessionId: String,
        id: String,
        feel: Int? = null,
        pain: Int? = null,
    ) {
        database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "library-$id",
                    name = "Back Squat",
                    primaryMuscle = MuscleGroup.QUADS,
                    secondaryMuscles = emptyList(),
                    equipment = Equipment.BARBELL,
                    movementPattern = MovementPattern.SQUAT,
                    isCustom = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                    deletedAt = null,
                ),
            ),
        )
        database.workoutDao().insertSessionExercise(
            SessionExerciseEntity(
                id = id,
                sessionId = sessionId,
                exerciseId = "library-$id",
                position = 0,
                finishedAt = null,
                muscleFeel = feel,
                jointPain = pain,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null,
            ),
        )
    }

    /** A set for the RPE average; the exercise it hangs off is created by the caller. */
    private fun set(id: String, sessionExerciseId: String, rpe: Int?) = SetEntryEntity(
        id = id,
        sessionExerciseId = sessionExerciseId,
        setIndex = 0,
        reps = 5,
        weightGrams = 100_000L,
        setType = SetType.NORMAL,
        rpe = rpe,
        note = null,
        completedAt = 1_000L,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = null,
    )
}
