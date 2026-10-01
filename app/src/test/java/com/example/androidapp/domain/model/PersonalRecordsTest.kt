package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rep-max records (ROADMAP N23).
 *
 * A record is per rep count, strictly heavier, and never a warm-up — each of those is a way
 * the previous "approximately correct" answer was wrong.
 */
class PersonalRecordsTest {

    private fun did(weightGrams: Long, reps: Int, role: SetType = SetType.NORMAL, assistanceGrams: Long = 0L) =
        PerformedSetSpec(role = role, weightGrams = weightGrams, assistanceGrams = assistanceGrams, reps = reps)

    @Test
    fun records_arePerRepCount_notOneBestEver() {
        val records = PersonalRecords.from(
            listOf(did(100_000L, 5), did(90_000L, 8), did(110_000L, 3)),
        )

        assertEquals(100_000L, records.bestAt(5))
        assertEquals(90_000L, records.bestAt(8))
        assertEquals(110_000L, records.bestAt(3))
        assertNull("nothing was done at ten", records.bestAt(10))
    }

    @Test
    fun moreRepsAtLessWeight_isNotARecordAtFive() {
        // The whole reason records are per rep count: twelve reps at 90 kg does not beat
        // five at 100 kg, and a single best-ever line would say it did.
        val records = PersonalRecords.from(listOf(did(100_000L, 5), did(90_000L, 12)))

        assertFalse(records.isRecord(reps = 5, weightGrams = 95_000L))
        assertFalse(records.isRecord(reps = 5, weightGrams = 100_000L))
        assertTrue(records.isRecord(reps = 5, weightGrams = 102_500L))
    }

    @Test
    fun matchingTheBest_isNotBeatingIt() {
        // Celebrating a repeat devalues the word, and the app would be lying by omission.
        val records = PersonalRecords.from(listOf(did(100_000L, 5)))

        assertFalse(records.isRecord(reps = 5, weightGrams = 100_000L))
    }

    @Test
    fun theFirstSetAtARepCount_isARecord() {
        val records = PersonalRecords.from(listOf(did(100_000L, 5)))

        assertTrue("nothing had been done at three before", records.isRecord(reps = 3, weightGrams = 60_000L))
        assertNull(records.bestAt(3))
    }

    @Test
    fun aWarmUp_isNeverARecord_norSetsOne() {
        // Why records are finally correct rather than approximately: before N14's roles, a
        // heavy warm-up was indistinguishable from a working set.
        val records = PersonalRecords.from(
            listOf(did(120_000L, 3, role = SetType.WARMUP), did(100_000L, 3)),
        )

        assertEquals(100_000L, records.bestAt(3))
        assertTrue("the warm-up did not set the bar", records.isRecord(reps = 3, weightGrams = 105_000L))
    }

    @Test
    fun bodyweightAndAssistedSets_haveNoRecordToClaim() {
        // There is no weight to beat: an assisted set's number is the machine's help, not the
        // lifter's work, and a bodyweight set is zero by this definition (N15).
        val records = PersonalRecords.from(
            listOf(did(0L, 10), did(0L, 8, assistanceGrams = 20_000L)),
        )

        assertTrue(records.isEmpty)
        assertFalse(records.isRecord(reps = 10, weightGrams = 0L))
        assertFalse("reps of zero is not a performance", records.isRecord(reps = 0, weightGrams = 100_000L))
    }

    @Test
    fun theHeaviestAtARepCount_wins() {
        val records = PersonalRecords.from(listOf(did(95_000L, 5), did(100_000L, 5), did(97_500L, 5)))

        assertEquals(100_000L, records.bestAt(5))
    }
}
