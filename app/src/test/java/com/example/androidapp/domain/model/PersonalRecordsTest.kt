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

        assertFalse(records.isRecord(reps = 5, weightGrams = 95_000L, role = SetType.NORMAL))
        assertFalse(records.isRecord(reps = 5, weightGrams = 100_000L, role = SetType.NORMAL))
        assertTrue(records.isRecord(reps = 5, weightGrams = 102_500L, role = SetType.NORMAL))
    }

    @Test
    fun matchingTheBest_isNotBeatingIt() {
        // Celebrating a repeat devalues the word, and the app would be lying by omission.
        val records = PersonalRecords.from(listOf(did(100_000L, 5)))

        assertFalse(records.isRecord(reps = 5, weightGrams = 100_000L, role = SetType.NORMAL))
    }

    @Test
    fun theFirstSetAtARepCount_isARecord() {
        val records = PersonalRecords.from(listOf(did(100_000L, 5)))

        assertTrue(
            "nothing had been done at three before",
            records.isRecord(reps = 3, weightGrams = 60_000L, role = SetType.NORMAL),
        )
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
        assertTrue(
            "the warm-up did not set the bar",
            records.isRecord(reps = 3, weightGrams = 105_000L, role = SetType.NORMAL),
        )
    }

    @Test
    fun bodyweightAndAssistedSets_haveNoRecordToClaim() {
        // There is no weight to beat: an assisted set's number is the machine's help, not the
        // lifter's work, and a bodyweight set is zero by this definition (N15).
        val records = PersonalRecords.from(
            listOf(did(0L, 10), did(0L, 8, assistanceGrams = 20_000L)),
        )

        assertTrue(records.isEmpty)
        assertFalse(records.isRecord(reps = 10, weightGrams = 0L, role = SetType.NORMAL))
        assertFalse(
            "reps of zero is not a performance",
            records.isRecord(reps = 0, weightGrams = 100_000L, role = SetType.NORMAL),
        )
    }

    @Test
    fun theHeaviestAtARepCount_wins() {
        val records = PersonalRecords.from(listOf(did(95_000L, 5), did(100_000L, 5), did(97_500L, 5)))

        assertEquals(100_000L, records.bestAt(5))
    }

    @Test
    fun aWarmUp_isNeverARecord_evenAboveTheBest() {
        // ROADMAP B17: the role was never passed to this rule, so the one set that mattered —
        // the set being logged — went unchecked, and a heavy warm-up raised a personal best the
        // file's own doc said could no longer happen.
        val records = PersonalRecords.from(listOf(did(100_000L, 5)))

        assertTrue(
            "a working set above the best is a record",
            records.isRecord(reps = 5, weightGrams = 102_500L, role = SetType.NORMAL),
        )
        assertFalse(
            "and the same weight as a warm-up is not",
            records.isRecord(reps = 5, weightGrams = 102_500L, role = SetType.WARMUP),
        )
    }

    @Test
    fun theMergedBest_isWhatABannerMustReport() {
        // ROADMAP B18: the report read history alone, so a bar set earlier in the same session
        // was invisible to it — the banner then said "the first time at this rep count" while
        // claiming a record over that very set. The fix is to read the merged view, which is
        // what this asserts: history says 8 reps at 17.5, the session already has 20, and what
        // the next 22.5 beats is the 20.
        val history = PersonalRecords.from(listOf(did(17_500L, 8)))
        val thisSession = PersonalRecords.from(listOf(did(20_000L, 8)))

        val against = history.mergedWith(thisSession)

        assertEquals("history alone would say 17.5", 17_500L, history.bestAt(8))
        assertEquals("but the bar is the session's 20", 20_000L, against.bestAt(8))
        assertTrue(against.isRecord(reps = 8, weightGrams = 22_500L, role = SetType.NORMAL))
    }
}
