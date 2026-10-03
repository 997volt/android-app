package com.example.androidapp.domain.model

import java.time.Instant
import java.util.Locale

/**
 * One finished workout's worth of the signals the app collects (ROADMAP N13).
 *
 * Every value is an average over that workout's rated sets or rated exercises, so a
 * workout with one rated set and a workout with nine carry the same weight — a trend
 * is about sessions, not about how many sets happened to be tagged.
 *
 * Null means "not recorded", which is the normal case: all three are skippable, and a
 * workout can have an RPE but no muscle-feel rating at all.
 */
data class TrendPoint(
    val startedAt: Instant,
    val averageRpe: Double? = null,
    val averageMuscleFeel: Double? = null,
    val averageJointPain: Double? = null,
)

/** The three series the trends screen draws (ROADMAP N13). */
enum class TrendMetric {
    RPE,
    MUSCLE_FEEL,
    JOINT_PAIN,
    ;

    /** The value this metric takes from a point, or null when it was not recorded. */
    fun valueOf(point: TrendPoint): Double? = when (this) {
        RPE -> point.averageRpe
        MUSCLE_FEEL -> point.averageMuscleFeel
        JOINT_PAIN -> point.averageJointPain
    }
}

/**
 * One metric's values across the window, oldest first, null where it was not recorded.
 *
 * Order matters to the caller and is not implied by the list type, so the KDoc says
 * it; the chart draws left to right and the caption reads the last entry.
 */
fun List<TrendPoint>.valuesOf(metric: TrendMetric): List<Double?> = map { metric.valueOf(it) }

/**
 * The most recent recorded value, or null when the metric was never recorded.
 *
 * Deliberately not `last()`: the newest workout may simply not carry this metric, and
 * reporting null there would look like the signal had been lost.
 */
fun List<Double?>.latestValue(): Double? = lastOrNull { it != null }

/** The mean of what was recorded, or null when nothing was. */
fun List<Double?>.averageValue(): Double? {
    val recorded = filterNotNull()
    return if (recorded.isEmpty()) null else recorded.average()
}

/**
 * A 1–10 average as the screen shows it: one decimal, always a dot.
 *
 * `Locale.ROOT` on purpose. These are ratings rather than measurements in a user's
 * unit system, and a chart axis that reads "7,2" in one locale and "7.2" in another is
 * harder to test for no gain the user can see.
 */
fun Double.asRating(): String = String.format(Locale.ROOT, "%.1f", this)

/**
 * A number that is whole when it can be, and a tenth otherwise.
 *
 * For a rate, where the unit is a count but the *change* is not: two reps a week reads "2", and a fifth of
 * one reads "0.2" instead of being truncated to a zero that contradicts the trend line. The same
 * `Locale.ROOT` reasoning as [asRating].
 */
fun Double.asTenthsOrWhole(): String =
    if (this == toLong().toDouble()) toLong().toString() else String.format(Locale.ROOT, "%.1f", this)
