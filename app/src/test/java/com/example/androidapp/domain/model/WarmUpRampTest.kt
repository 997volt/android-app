package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The warm-up generator (ROADMAP N28).
 *
 * A generator is a rule with an opinion, so the cases state the opinion: a ramp that climbs, weights
 * a person can actually load, nothing above the working set, and no ramp at all for a bodyweight
 * exercise.
 */
class WarmUpRampTest {

    @Test
    fun theRamp_climbsToTheWorkingWeight_andNoFurther() {
        val ramp = warmUpRamp(workingWeightGrams = 100_000L)

        assertEquals(listOf(40_000L, 60_000L, 75_000L, 85_000L), ramp.map { it.weightGrams })
        assertTrue("nothing warms up above the work", ramp.all { it.weightGrams < 100_000L })
    }

    @Test
    fun theReps_fallAsTheWeightRises() {
        // A ramp is also a rep scheme: five at the first weight, one at the last.
        val ramp = warmUpRamp(workingWeightGrams = 100_000L)

        assertEquals(listOf(5, 3, 2, 1), ramp.map { it.reps })
    }

    @Test
    fun everyWeight_isOneAPersonCanLoad() {
        // 2.5 kg steps, the same the progression rule uses: a warm-up of 61.7 kg is not loadable.
        val ramp = warmUpRamp(workingWeightGrams = 82_500L)

        assertTrue(
            "each weight is a multiple of the step",
            ramp.all { it.weightGrams % 2_500L == 0L },
        )
    }

    @Test
    fun aLightWorkingWeight_dropsTheWarmUpsThatCollapseTogether() {
        // At 30 kg the first two fractions round to the same bar, and one warm-up is not two.
        val ramp = warmUpRamp(workingWeightGrams = 30_000L)

        assertEquals(
            "no weight appears twice",
            ramp.size,
            ramp.map { it.weightGrams }.distinct().size,
        )
        assertTrue(ramp.all { it.weightGrams < 30_000L })
    }

    @Test
    fun aBodyweightExercise_getsNoRamp() {
        // A fraction of nothing is nothing, and a list of zeroes would be a plan full of empty bars
        // (N15: a bodyweight set carries no weight).
        assertTrue(warmUpRamp(workingWeightGrams = 0L).isEmpty())
    }

    @Test
    fun assistanceWork_getsNoRampEither() {
        // An assisted set's number is the machine's help, not a weight to take 40% of.
        assertTrue(warmUpRamp(workingWeightGrams = 0L).isEmpty())
    }

    @Test
    fun aVeryLightWorkingWeight_stillProducesSomethingLoadable() {
        // 5 kg working weight: the fractions all round below one step, so the ramp is empty rather
        // than a row of 2.5 kg warm-ups for a 5 kg set — which would be a ramp that warms nothing.
        val ramp = warmUpRamp(workingWeightGrams = 5_000L)

        assertTrue("nothing loadable below the work", ramp.all { it.weightGrams < 5_000L })
    }
}
