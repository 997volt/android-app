package com.example.androidapp.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The per-exercise series maths (ROADMAP N17).
 *
 * All of it lives here rather than in SQL, because "the heaviest working set" and "a
 * one-rep-max estimate" are decisions, not aggregations — and the two that matter most
 * are the ones a chart would get quietly wrong: a warm-up becoming the heaviest set, and
 * an assisted set being plotted as a load.
 */
class ExerciseTrendPointTest {

    @Test
    fun aWarmUp_isNotTheHeaviestSet() {
        // The reason N14's roles had to exist before this screen could: 60 kg for eight
        // as a warm-up must not read as the session's top set.
        val points = rows(
            row(weight = 60_000L, reps = 8, type = SetType.WARMUP),
            row(weight = 100_000L, reps = 5, type = SetType.NORMAL),
        ).toExerciseTrendPoints()

        assertEquals(100_000L, points.single().heaviestSetGrams)
        assertEquals("a warm-up is not volume either", 500_000L, points.single().volumeGrams)
        assertEquals(5, points.single().totalReps)
    }

    @Test
    fun aDropSet_countsAsAWorkingSet() {
        // Drop and failure sets are work: only the warm-up is excluded.
        val points = rows(
            row(weight = 100_000L, reps = 5),
            row(weight = 60_000L, reps = 12, type = SetType.DROP),
            row(weight = 80_000L, reps = 3, type = SetType.FAILURE),
        ).toExerciseTrendPoints()

        assertEquals(100_000L, points.single().heaviestSetGrams)
        assertEquals(20, points.single().totalReps)
        assertEquals(500_000L + 720_000L + 240_000L, points.single().volumeGrams)
    }

    @Test
    fun epley_estimatesFromTheHeaviestSet() {
        // 100 kg for 5: 100 × (1 + 5/30) = 116.67 kg, rounded to the nearest half.
        val points = rows(row(weight = 100_000L, reps = 5)).toExerciseTrendPoints()

        assertEquals(116_500L, points.single().estimatedOneRepMaxGrams)
    }

    @Test
    fun aSingleRep_isItsOwnMaximum() {
        assertEquals(140_000L, estimatedOneRepMax(140_000L, reps = 1))
    }

    @Test
    fun anEstimateFromTooManyReps_isNull_ratherThanConfident() {
        // Epley extrapolates a max from a set that was nowhere near one; past twelve
        // reps that is a guess, and the chart shows a gap instead of a number.
        assertNull(estimatedOneRepMax(100_000L, reps = 13))
        assertNull(estimatedOneRepMax(100_000L, reps = 0))
        assertNull(estimatedOneRepMax(0L, reps = 5))
    }

    @Test
    fun assistance_isTheLeastHelpOfTheSession_notTheMost() {
        // On an assisted machine the hardest set is the one with the least help, and
        // that is the number progress moves (N17's direction decision).
        val points = rows(
            row(weight = 0L, reps = 8, assistance = 20_000L),
            row(weight = 0L, reps = 6, assistance = 12_500L),
        ).toExerciseTrendPoints()

        assertEquals(12_500L, points.single().leastAssistanceGrams)
    }

    @Test
    fun anAssistedSet_addsNoLoadAndNoVolume() {
        // Assistance is not negative tonnage (N15), and a set the machine lifted is not
        // a set *you* loaded — so it does not become the heaviest set either.
        val points = rows(
            row(weight = 0L, reps = 8, assistance = 20_000L),
            row(weight = 0L, reps = 6, assistance = 12_500L),
        ).toExerciseTrendPoints()

        assertEquals(0L, points.single().volumeGrams)
        assertEquals(14, points.single().totalReps)
        assertNull(
            "0 kg is bodyweight or assistance, not a load to plot",
            points.single().heaviestSetGrams,
        )
        assertNull(points.single().estimatedOneRepMaxGrams)
    }

    @Test
    fun theRatingAverages_arePerSession_andRpeComesBackInPoints() {
        // 19 and 16 halves are 9.5 and 8.0, so the session's average is 8.75 — not 8.75
        // halves, and not 17.5.
        val points = rows(
            row(weight = 100_000L, reps = 5, rpeHalves = 19),
            row(weight = 100_000L, reps = 5, rpeHalves = 16),
            muscleFeel = 8,
            jointPain = 4,
        ).toExerciseTrendPoints()

        assertEquals(8.75, points.single().averageRpe!!, 0.0001)
        assertEquals(8.0, points.single().averageMuscleFeel!!, 0.0001)
        assertEquals(4.0, points.single().averageJointPain!!, 0.0001)
    }

    @Test
    fun sessions_comeBackOldestFirst() {
        // The chart draws left to right, so the order is part of the contract.
        val points = rows(
            row(session = "new", startedAt = 2_000L, weight = 100_000L, reps = 5),
            row(session = "old", startedAt = 1_000L, weight = 90_000L, reps = 5),
        ).toExerciseTrendPoints()

        assertEquals(
            listOf(Instant.ofEpochMilli(1_000L), Instant.ofEpochMilli(2_000L)),
            points.map { it.startedAt },
        )
    }

    @Test
    fun aSessionThatLoggedNoSet_isAPointWithNothingToDraw_forTheLoadSeries() {
        // The exercise was recorded but no set was logged: it still contributes its
        // ratings, and the load series has a gap rather than a zero.
        val points = listOf(
            ExerciseTrendRow(
                sessionId = "s1",
                startedAt = Instant.ofEpochMilli(1_000L),
                muscleFeel = 7,
                jointPain = null,
                weightGrams = null,
                reps = null,
                rpeHalves = null,
                setType = null,
                assistanceGrams = null,
            ),
        ).toExerciseTrendPoints()

        val point = points.single()
        assertNull(point.heaviestSetGrams)
        assertEquals(0L, point.volumeGrams)
        assertEquals(7.0, point.averageMuscleFeel!!, 0.0001)
    }

    @Test
    fun theLoadAxis_startsAtZero_whileTheRatingAxisDoesNot() {
        // A kilogram is a quantity, so its chart runs from zero; a 1–10 rating is a
        // scale, and drawing it from zero would flatten every real difference.
        assertEquals(false, ExerciseTrendMetric.RPE.isLoad)
        assertEquals(true, ExerciseTrendMetric.HEAVIEST_SET.isLoad)
        assertEquals(true, ExerciseTrendMetric.ASSISTANCE.isLoad)
    }

    @Test
    fun onlyAssistance_saysLowerIsBetter() {
        assertEquals(false, ExerciseTrendMetric.ASSISTANCE.higherIsBetter)
        assertEquals(true, ExerciseTrendMetric.HEAVIEST_SET.higherIsBetter)
        assertEquals(true, ExerciseTrendMetric.VOLUME.higherIsBetter)
    }

    private fun rows(
        vararg sets: SetRow,
        muscleFeel: Int? = null,
        jointPain: Int? = null,
    ): List<ExerciseTrendRow> = sets.map {
            ExerciseTrendRow(
                sessionId = it.session,
                startedAt = Instant.ofEpochMilli(it.startedAt),
                muscleFeel = muscleFeel,
                jointPain = jointPain,
                weightGrams = it.weight,
                reps = it.reps,
                rpeHalves = it.rpeHalves,
                setType = it.type,
                assistanceGrams = it.assistance,
            )
        }

    private fun row(
        session: String = "s1",
        startedAt: Long = 1_000L,
        weight: Long? = 100_000L,
        reps: Int? = 5,
        rpeHalves: Int? = null,
        type: SetType = SetType.NORMAL,
        assistance: Long? = null,
    ) = SetRow(session, startedAt, weight, reps, rpeHalves, type, assistance)

    private data class SetRow(
        val session: String,
        val startedAt: Long,
        val weight: Long?,
        val reps: Int?,
        val rpeHalves: Int?,
        val type: SetType,
        val assistance: Long?,
    )
}
