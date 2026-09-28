package com.example.androidapp.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for weight storage and parsing (ROADMAP P1.3).
 *
 * The reason grams are used at all is that these conversions have to be exact —
 * a training log that drifts by 0.01 kg per edit is worse than useless.
 */
class WeightTest {

    @Test
    fun kilograms_dropsTrailingZeros() {
        assertEquals("60", Weight.kilograms(60_000))
        assertEquals("0", Weight.kilograms(0))
    }

    @Test
    fun kilograms_keepsMeaningfulTenths() {
        assertEquals("62.5", Weight.kilograms(62_500))
        assertEquals("60.5", Weight.kilograms(60_500))
        assertEquals("1.25", Weight.kilograms(1_250))
    }

    @Test
    fun parse_acceptsDecimalsAndCommas() {
        assertEquals(62_500L, Weight.parseKilograms("62.5"))
        // A comma decimal separator is what most of Europe types.
        assertEquals(62_500L, Weight.parseKilograms("62,5"))
        assertEquals(60_000L, Weight.parseKilograms("  60 "))
    }

    @Test
    fun parse_rejectsNonsenseInsteadOfGuessing() {
        // Returning null (rather than 0) is what keeps a typo from being logged
        // as a real set.
        assertNull(Weight.parseKilograms(""))
        assertNull(Weight.parseKilograms("abc"))
        assertNull(Weight.parseKilograms("-5"))
    }

    @Test
    fun parseAndFormat_roundTripExactly() {
        val samples = listOf(0L, 1_250L, 60_000L, 62_500L, 100_000L, 227_500L)
        samples.forEach { grams ->
            assertEquals(grams, Weight.parseKilograms(Weight.kilograms(grams)))
        }
    }

    @Test
    fun parse_rejectsDoubleGrammarThatIsNotUserGrammar() {
        // `toDoubleOrNull()` accepts all of these; a numeric keypad cannot produce
        // any of them, so accepting them only ever means a paste or a bug.
        assertNull(Weight.parseKilograms("1e10"))
        assertNull(Weight.parseKilograms("1E3"))
        assertNull(Weight.parseKilograms("0x1p3"))
        assertNull(Weight.parseKilograms("8d"))
        assertNull(Weight.parseKilograms("Infinity"))
        assertNull(Weight.parseKilograms("NaN"))
    }

    @Test
    fun parse_rejectsWeightsAboveTheCap() {
        // Without a ceiling a fat-fingered entry becomes permanent history.
        assertNull(Weight.parseKilograms("1001"))
        assertEquals(1_000_000L, Weight.parseKilograms("1000"))
    }

    @Test
    fun parse_rejectsMorePrecisionThanGrams() {
        assertNull(Weight.parseKilograms("60.0001"))
        assertEquals(60_000L, Weight.parseKilograms("60.0"))
    }

    @Test
    fun step_neverGoesNegative() {
        assertEquals(0L, Weight.step(1_000L, -5_000L))
        assertEquals(62_500L, Weight.step(60_000L, Weight.DEFAULT_STEP_GRAMS))
    }
}

/**
 * JVM tests for rest-timer arithmetic (ROADMAP P1.4).
 *
 * Because the timer is an absolute end instant, all of this is pure and needs no
 * clock mocking beyond passing an instant in.
 */
class RestTimerTest {

    private val now: Instant = Instant.parse("2026-09-28T08:00:00Z")

    @Test
    fun noRest_hasNoTimeRemaining() {
        assertEquals(0, RestTimer.remainingSeconds(null, now))
        assertFalse(RestTimer.isRunning(null, now))
    }

    @Test
    fun remaining_countsDownToTheEndInstant() {
        assertEquals(90, RestTimer.remainingSeconds(now.plusSeconds(90), now))
        assertTrue(RestTimer.isRunning(now.plusSeconds(90), now))
    }

    @Test
    fun aFinishedRest_reportsZero_notNegative() {
        // The elapsed-time formatter must never be handed a negative count.
        assertEquals(0, RestTimer.remainingSeconds(now.minus(Duration.ofSeconds(30)), now))
        assertFalse(RestTimer.isRunning(now.minusSeconds(1), now))
    }

    @Test
    fun remaining_truncatesPartialSeconds() {
        assertEquals(0, RestTimer.remainingSeconds(now.plusMillis(999), now))
    }

    @Test
    fun format_padsSeconds() {
        assertEquals("1:30", RestTimer.format(90))
        assertEquals("0:05", RestTimer.format(5))
        assertEquals("0:00", RestTimer.format(0))
        assertEquals("0:00", RestTimer.format(-10))
    }
}
