package com.example.androidapp.domain

import java.util.Locale
import kotlin.math.abs

/**
 * Weight handling (ROADMAP P1.3, and the groundwork for P1.9).
 *
 * Everything is stored as **whole grams in a `Long`**. That is a deliberate
 * choice over kilograms-as-`Double`:
 *
 *  - A `Double` cannot represent 0.1 kg exactly, so repeated edits (62.5, 63.75,
 *    ...) accumulate error, and "did they lift 100 or 100.00000001?" is not a
 *    question a training log should have to answer.
 *  - Grams give exact 0.5 kg and 1.25 kg steps with no rounding, and stay exact
 *    through unit changes, which is what makes P1.9 (kg/lb display) a pure
 *    presentation concern rather than a data conversion.
 */
object Weight {

    const val GRAMS_PER_KILOGRAM = 1_000L

    /** Sub-divisions shown on the label: 62.5 kg is 62 whole + 5 tenths. */
    private const val TENTHS_PER_KILOGRAM = 10

    /** A 2.5 kg step: the smallest pair of plates most gyms have per side. */
    const val DEFAULT_STEP_GRAMS = 2_500L

    /**
     * The heaviest weight a lifter can log, in grams.
     *
     * A cap exists because there is otherwise no upper bound at all: a fat-fingered
     * entry becomes a permanent, absurd row in the training history. 1000 kg is far
     * above any human lift and far below anything that breaks a total.
     */
    const val MAX_GRAMS = 1_000L * GRAMS_PER_KILOGRAM

    /**
     * User grammar for a weight: digits, optionally a decimal part. No sign, no
     * exponent, no hex.
     *
     * Deliberately stricter than [Double.parseDouble], which accepts `"1e10"`, `"8d"`
     * and hex floats — all of which a numeric keypad cannot produce but a paste or a
     * test can. Anything the regex rejects is `null`, so a typo cannot become a set.
     */
    private val PLAIN_DECIMAL = Regex("""^\d{1,4}([.,]\d{1,3})?$""")

    /** Where a brand-new exercise starts. Light enough to be obviously editable. */
    const val DEFAULT_GRAMS = 20 * GRAMS_PER_KILOGRAM

    /**
     * Kilograms without trailing zeros: `60000 -> "60"`, `62500 -> "62.5"`.
     * `Locale.ROOT` keeps the decimal separator stable regardless of device
     * locale (a locale-aware swap belongs with P5.4, not here).
     */
    fun kilograms(grams: Long): String {
        val whole = grams / GRAMS_PER_KILOGRAM
        val remainder = abs(grams % GRAMS_PER_KILOGRAM)
        if (remainder == 0L) return whole.toString()

        // Tenths of a kilogram, trailing zeros trimmed: 62500 -> "62.5", 60500 -> "60.5".
        val tenths = remainder / (GRAMS_PER_KILOGRAM / TENTHS_PER_KILOGRAM)
        val fraction = if (remainder % (GRAMS_PER_KILOGRAM / TENTHS_PER_KILOGRAM) == 0L) {
            tenths.toString()
        } else {
            String.format(Locale.ROOT, "%03d", remainder).trimEnd('0')
        }
        return "$whole.$fraction"
    }

    /**
     * Parses user input in kilograms, or null when it is not a usable number.
     *
     * Returns null rather than throwing or defaulting, so a typo cannot silently
     * become a logged set of 0 kg.
     */
    fun parseKilograms(text: String): Long? {
        val normalized = text.trim().replace(',', '.')
        if (!PLAIN_DECIMAL.matches(normalized)) return null

        val grams = normalized
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0 }
            ?.let { Math.round(it * GRAMS_PER_KILOGRAM) }
        return grams?.takeIf { it <= MAX_GRAMS }
    }

    /** Never negative: a weight below zero is meaningless and would corrupt totals. */
    fun step(grams: Long, deltaGrams: Long): Long = (grams + deltaGrams).coerceAtLeast(0L)
}
