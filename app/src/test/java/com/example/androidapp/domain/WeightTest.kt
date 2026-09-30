package com.example.androidapp.domain

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Test

/**
 * JVM tests for weight storage and parsing (ROADMAP P1.3).
 *
 * The reason grams are used at all is that these conversions have to be exact —
 * a training log that drifts by 0.01 kg per edit is worse than useless.
 *
 * Asserted with Truth rather than JUnit's `assertEquals(expected, actual)`: those two
 * arguments are easy to swap, and a failure prints two bare values with no indication
 * of which was expected. This is the first file migrated; the rest follow as they are
 * touched (see DECISIONS.md).
 */
class WeightTest {

    @Test
    fun kilograms_dropsTrailingZeros() {
        assertThat(Weight.kilograms(60_000)).isEqualTo("60")
        assertThat(Weight.kilograms(0)).isEqualTo("0")
    }

    @Test
    fun kilograms_keepsMeaningfulTenths() {
        assertThat(Weight.kilograms(62_500)).isEqualTo("62.5")
        assertThat(Weight.kilograms(60_500)).isEqualTo("60.5")
        assertThat(Weight.kilograms(1_250)).isEqualTo("1.25")
    }

    @Test
    fun parse_acceptsDecimalsAndCommas() {
        assertThat(Weight.parseKilograms("62.5")).isEqualTo(62_500L)
        // A comma decimal separator is what most of Europe types.
        assertThat(Weight.parseKilograms("62,5")).isEqualTo(62_500L)
        assertThat(Weight.parseKilograms("  60 ")).isEqualTo(60_000L)
    }

    @Test
    fun parse_rejectsNonsenseInsteadOfGuessing() {
        // Returning null (rather than 0) is what keeps a typo from being logged
        // as a real set.
        assertThat(Weight.parseKilograms("")).isNull()
        assertThat(Weight.parseKilograms("abc")).isNull()
        assertThat(Weight.parseKilograms("-5")).isNull()
    }

    @Test
    fun parseAndFormat_roundTripExactly() {
        val samples = listOf(0L, 1_250L, 60_000L, 62_500L, 100_000L, 227_500L)
        samples.forEach { grams ->
            assertThat(Weight.parseKilograms(Weight.kilograms(grams))).isEqualTo(grams)
        }
    }

    @Test
    fun parse_rejectsDoubleGrammarThatIsNotUserGrammar() {
        // `toDoubleOrNull()` accepts all of these; a numeric keypad cannot produce
        // any of them, so accepting them only ever means a paste or a bug.
        assertThat(Weight.parseKilograms("1e10")).isNull()
        assertThat(Weight.parseKilograms("1E3")).isNull()
        assertThat(Weight.parseKilograms("0x1p3")).isNull()
        assertThat(Weight.parseKilograms("8d")).isNull()
        assertThat(Weight.parseKilograms("Infinity")).isNull()
        assertThat(Weight.parseKilograms("NaN")).isNull()
    }

    @Test
    fun parse_rejectsWeightsAboveTheCap() {
        // Without a ceiling a fat-fingered entry becomes permanent history.
        assertThat(Weight.parseKilograms("1001")).isNull()
        assertThat(Weight.parseKilograms("1000")).isEqualTo(1_000_000L)
    }

    @Test
    fun parse_rejectsMorePrecisionThanGrams() {
        assertThat(Weight.parseKilograms("60.0001")).isNull()
        assertThat(Weight.parseKilograms("60.0")).isEqualTo(60_000L)
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
        assertThat(RestTimer.remainingSeconds(null, now)).isEqualTo(0)
        assertThat(RestTimer.isRunning(null, now)).isFalse()
    }

    @Test
    fun remaining_countsDownToTheEndInstant() {
        assertThat(RestTimer.remainingSeconds(now.plusSeconds(90), now)).isEqualTo(90)
        assertThat(RestTimer.isRunning(now.plusSeconds(90), now)).isTrue()
    }

    @Test
    fun aFinishedRest_reportsZero_notNegative() {
        // The elapsed-time formatter must never be handed a negative count.
        assertThat(RestTimer.remainingSeconds(now.minus(Duration.ofSeconds(30)), now)).isEqualTo(0)
        assertThat(RestTimer.isRunning(now.minusSeconds(1), now)).isFalse()
    }

    @Test
    fun remaining_truncatesPartialSeconds() {
        assertThat(RestTimer.remainingSeconds(now.plusMillis(999), now)).isEqualTo(0)
    }

    @Test
    fun format_padsSeconds() {
        assertThat(RestTimer.format(90)).isEqualTo("1:30")
        assertThat(RestTimer.format(5)).isEqualTo("0:05")
        assertThat(RestTimer.format(0)).isEqualTo("0:00")
        assertThat(RestTimer.format(-10)).isEqualTo("0:00")
    }

    @Test
    fun aLoadIsAWeightOrAssistance_dependingOnTheSign() {
        // One field, two columns: the sign is how assistance is said (ROADMAP N15).
        assertThat(Weight.parseLoad("100"))
            .isEqualTo(Load(weightGrams = 100_000L, assistanceGrams = 0L))
        assertThat(Weight.parseLoad("-20"))
            .isEqualTo(Load(weightGrams = 0L, assistanceGrams = 20_000L))
        assertThat(Weight.parseLoad("  -20  ")).isEqualTo(Load(0L, 20_000L))
        assertThat(Weight.parseLoad("-0,5")).isEqualTo(Load(0L, 500L))
        assertThat(Weight.parseLoad("60.0")).isEqualTo(Load(60_000L, 0L))
    }

    @Test
    fun aLoadThatIsNotANumber_isNull_ratherThanZero() {
        assertThat(Weight.parseLoad("")).isNull()
        assertThat(Weight.parseLoad("heavy")).isNull()
        assertThat(Weight.parseLoad("--20")).isNull()
        assertThat(Weight.parseLoad("-")).isNull()
        assertThat(Weight.parseLoad("20-")).isNull()
    }

    @Test
    fun anAssistedLoad_showsTheMinusBack() {
        assertThat(Weight.display(weightGrams = 0L, assistanceGrams = 20_000L)).isEqualTo("-20")
        assertThat(Weight.display(weightGrams = 20_000L, assistanceGrams = 0L)).isEqualTo("20")
        assertThat(Weight.display(0L, 0L)).isEqualTo("0")
        // Assistance wins when both are set, which the editor cannot produce: it is
        // the number that changes how the set reads.
        assertThat(Weight.display(20_000L, 20_000L)).isEqualTo("-20")
    }

}
