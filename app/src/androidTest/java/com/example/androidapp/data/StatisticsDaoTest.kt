package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.WorkoutSessionEntity
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Counting the records inside a window (ROADMAP N35).
 *
 * The query has to be right about four things a fake would agree with whatever it was told: that matching a
 * best is not beating it, that a warm-up is never a record, that "earlier" means the order they were
 * performed in, and that the window is what bounds the count. Only SQLite can be asked.
 */
class StatisticsDaoTest {

    private lateinit var database: WorkoutDatabase
    private val dao get() = database.statisticsDao()

    private val day = 86_400_000L

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        database.exerciseDao().insertAll(
            listOf(exercise("back-squat"), exercise("bench-press")),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theFirstSetAtARepCountIsARecord_andEverythingIsJudgedAgainstIt() = runTest {
        // Day 1: 100 x 5 — the first, so a record.
        session("s1", at = 0L, "back-squat" to listOf(set("s1-0", reps = 5, weight = 100_000L)))
        // Day 2: 100 x 5 again — matching a best does not beat it.
        session("s2", at = day, "back-squat" to listOf(set("s2-0", reps = 5, weight = 100_000L)))
        // Day 3: 105 x 5 — heavier, so a record.
        session("s3", at = 2 * day, "back-squat" to listOf(set("s3-0", reps = 5, weight = 105_000L)))
        // Day 4: 102.5 x 5 — heavier than the first, lighter than the best: not a record.
        session("s4", at = 3 * day, "back-squat" to listOf(set("s4-0", reps = 5, weight = 102_500L)))

        assertEquals(2, dao.countRecordsIn(from = 0L, to = 10 * day))
    }

    @Test
    fun aWarmUpIsNeverARecord_howeverHeavy() = runTest {
        // B17: a 120 kg warm-up is not a personal best, and it must not raise the bar for the working sets
        // either — so it is excluded on both sides of the comparison.
        session(
            "s1",
            at = 0L,
            "back-squat" to listOf(set("s1-0", reps = 5, weight = 120_000L, type = SetType.WARMUP)),
        )
        session("s2", at = day, "back-squat" to listOf(set("s2-0", reps = 5, weight = 100_000L)))

        assertEquals("the working set is the first real one, so it is a record", 1, dao.countRecordsIn(0L, 10 * day))
    }

    @Test
    fun withinOneSession_theSetBeforeCounts_asEarlier() = runTest {
        // 100 x 5 then 100 x 5: the second matches the first, so one record, not two.
        session(
            "s1",
            at = 0L,
            "back-squat" to listOf(
                set("s1-0", index = 0, reps = 5, weight = 100_000L),
                set("s1-1", index = 1, reps = 5, weight = 100_000L),
            ),
        )
        // And a rising session is two records: 100 then 105.
        session(
            "s2",
            at = day,
            "back-squat" to listOf(
                set("s2-0", index = 0, reps = 5, weight = 100_000L),
                set("s2-1", index = 1, reps = 5, weight = 105_000L),
            ),
        )

        assertEquals("s1-0 is not a record by then, s2-0 and s2-1 are", 2, dao.countRecordsIn(0L, 10 * day))
    }

    @Test
    fun aSetWithNoWeight_isNotCountedEitherWay() = runTest {
        // A bodyweight set has nothing to compare, which the domain rule answers with "no claim" rather
        // than with a record (PersonalRecords.isRecord).
        session("s1", at = 0L, "back-squat" to listOf(set("s1-0", reps = 8, weight = 0L)))

        assertEquals(0, dao.countRecordsIn(0L, 10 * day))
    }

    @Test
    fun theWindowBoundsTheCount_evenWhenTheSetWasARecord() = runTest {
        session("s1", at = 0L, "back-squat" to listOf(set("s1-0", reps = 5, weight = 100_000L)))
        session("s2", at = 5 * day, "back-squat" to listOf(set("s2-0", reps = 5, weight = 105_000L)))

        assertEquals("the first is outside a window that starts at day five", 1, dao.countRecordsIn(4 * day, 10 * day))
        assertEquals("and both are inside a window that covers everything", 2, dao.countRecordsIn(0L, 10 * day))
        assertEquals(
            "the end is exclusive",
            0,
            dao.countRecordsIn(4 * day, 5 * day),
        )
    }

    @Test
    fun twoExercisesAreJudgedSeparately() = runTest {
        // A bench press record says nothing about the squat: the comparison is per exercise.
        session("s1", at = 0L, "back-squat" to listOf(set("s1-0", reps = 5, weight = 100_000L)))
        session("s2", at = day, "bench-press" to listOf(set("s2-0", reps = 5, weight = 60_000L)))

        assertEquals(2, dao.countRecordsIn(0L, 10 * day))
    }

    /** A finished session at [at] (epoch millis), holding each exercise's sets in order. */
    private suspend fun session(
        id: String,
        at: Long,
        vararg exercises: Pair<String, List<SetEntryEntity>>,
    ) {
        val dao = database.workoutDao()
        dao.insertSession(
            WorkoutSessionEntity(
                id = id,
                startedAt = at,
                finishedAt = at + 3_600_000L,
                notes = null,
                restEndsAt = null,
                readinessNote = null,
                createdAt = at,
                updatedAt = at,
                deletedAt = null,
            ),
        )
        exercises.forEachIndexed { position, (exerciseId, rows) ->
            val rowId = "$id-row-$position"
            dao.insertSessionExercise(
                SessionExerciseEntity(
                    id = rowId,
                    sessionId = id,
                    exerciseId = exerciseId,
                    position = position,
                    restSeconds = 120,
                    techniqueNote = null,
                    finishedAt = null,
                    supersetGroup = null,
                    createdAt = at,
                    updatedAt = at,
                    deletedAt = null,
                ),
            )
            rows.forEach { dao.insertSet(it.copy(sessionExerciseId = rowId)) }
        }
    }

    private fun set(
        id: String,
        index: Int = 0,
        reps: Int,
        weight: Long,
        type: SetType = SetType.NORMAL,
    ) = SetEntryEntity(
        // Replaced with the session's row id when the session is seeded.
        sessionExerciseId = "",
        id = id,
        setIndex = index,
        reps = reps,
        weightGrams = weight,
        assistanceGrams = 0L,
        setType = type,
        rpeHalves = null,
        note = null,
        completedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )

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
}
