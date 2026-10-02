package com.example.androidapp.ui.statistics

import androidx.annotation.StringRes
import com.example.androidapp.R
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.domain.model.TenPointScale
import com.example.androidapp.domain.model.TrendMetric
import com.example.androidapp.domain.model.asRating
import com.example.androidapp.domain.model.asTenthsOrWhole
import com.example.androidapp.ui.measurements.MeasurementFormat

/** The three groups the picker offers, in the order it offers them (ROADMAP N35). */
enum class MetricGroup { WORKOUT, EXERCISE, BODY }

/**
 * Which stored series an entry reads.
 *
 * The three enums stay as they are — each means something on its own, and `TrendMetric` is what a
 * workout's ratings are read through — and the registry references them rather than replacing them.
 */
sealed interface MetricKey {
    data class Workout(val metric: TrendMetric) : MetricKey
    data class Exercise(val metric: ExerciseTrendMetric) : MetricKey
    data class Body(val metric: BodyMetric) : MetricKey

    /** A tape site, which is a body series but not one of the three body metrics (ROADMAP N32). */
    data class Tape(val site: TapeSite) : MetricKey

    /**
     * A stable name for this series.
     *
     * Built from the enums' own names rather than hand-written, so a test tag, a stored preference and a
     * log line all say the same thing and cannot drift from the metric they name. The enums already store
     * by name for the same reason.
     */
    val id: String
        get() = when (this) {
            is Workout -> "WORKOUT:${metric.name}"
            is Exercise -> "EXERCISE:${metric.name}"
            is Body -> "BODY:${metric.name}"
            is Tape -> "TAPE:${site.name}"
        }
}

/** The body series that are not tape sites (ROADMAP N32). */
enum class BodyMetric { WEIGHT, BODY_FAT, MUSCLE }

/**
 * How a stored value reads (ROADMAP N35).
 *
 * The stored number is the schema's — grams, tenths, millimetres, half-points — and this is the one place
 * those become something a person reads, so it delegates to the formatters that already do it rather than
 * inventing a second set.
 */
enum class MetricUnit(
    /** What the numbers *are*, as a string resource: a rate without a unit is an ambiguous number. */
    @StringRes val labelRes: Int,
) {
    KILOGRAMS(R.string.unit_kilograms) {
        override fun format(value: Double): String = Weight.kilograms(value.toLong())

        override fun parse(text: String): Double? =
            measure(text) { it.times(GRAMS_PER_KILOGRAM) }
    },
    RATING(R.string.unit_rating) {
        override fun format(value: Double): String = value.asRating()

        // A rate arrives in *stored* units like every other value here — halves, for a rating — so it
        // converts the same way `format` does before deciding how many decimals a rate needs.
        override fun formatRate(value: Double): String = (value / HALVES_PER_POINT).asTenthsOrWhole()

        override fun parse(text: String): Double? =
            measure(text) { it.times(HALVES_PER_POINT) }
    },
    REPS(R.string.unit_reps) {
        // A count is whole, so a tip is not a measurement — but a *rate* of one can be fractional, and
        // `toInt()` would print "+0 reps per week" for a series that is visibly rising. Hence `formatRate`.
        override fun format(value: Double): String = value.toInt().toString()

        override fun formatRate(value: Double): String = value.asTenthsOrWhole()
    },
    PERCENT(R.string.unit_percent) {
        override fun format(value: Double): String = MeasurementFormat.percent(value.toInt())

        override fun formatRate(value: Double): String = value.asTenthsOrWhole()

        override fun parse(text: String): Double? =
            measure(text) { it.times(TENTHS_PER_PERCENT) }
    },
    CENTIMETRES(R.string.unit_centimetres) {
        override fun format(value: Double): String = MeasurementFormat.centimetres(value.toLong())

        override fun formatRate(value: Double): String = value.asTenthsOrWhole()

        override fun parse(text: String): Double? =
            measure(text) { it.times(MILLIMETRES_PER_CENTIMETRE) }
    },
    ;

    /** The number without its unit: the unit belongs to the label's language, as everywhere else. */
    abstract fun format(value: Double): String

    /**
     * The number without its unit, for a *rate* rather than a reading.
     *
     * Defaults to [format], which is right for the units that are already fractional in display — kilograms,
     * centimetres and a rating keep their precision. The whole-number units override it, because a rate is
     * not a count: 0.2 reps a week is a real trend, and truncating it to "0" contradicts the line the user
     * is looking at.
     */
    open fun formatRate(value: Double): String = format(value)

    /**
     * What a typed number means in the metric's stored units (ROADMAP N39).
     *
     * The inverse of [format], and needed because a target is *typed* as "80" kilograms and *stored* as 80000
     * grams, like every other weight in the app. Null for anything that is not a usable number, so a typo
     * cannot become a target.
     *
     * "Not a usable number" includes `NaN`, `Infinity` and an overflow to infinity: `toDoubleOrNull` accepts
     * all of them, and a target of `NaN` reaches the axis and makes every coordinate on the chart `NaN`, so
     * the chart draws nothing and says nothing. This is the same hazard [Weight.parseKilograms] guards
     * against, and the guard is the same one: finite and not negative.
     */
    open fun parse(text: String): Double? = finite(text.trim().toDoubleOrNull())
}

/**
 * A typed measurement in stored units, or null when it is not usable.
 *
 * Applied *after* the unit conversion as well as before it, because the conversion can overflow:
 * `"1e307" × 1000` is `Infinity` even though `1e307` is finite on its own.
 */
private inline fun measure(text: String, convert: (Double) -> Double): Double? =
    finite(text.trim().toDoubleOrNull())?.let { convert(it) }?.let(::finite)

/** Finite and not negative: what every stored measurement in this app is. */
private fun finite(value: Double?): Double? =
    value?.takeIf { it.isFinite() && it >= 0.0 }

/**
 * One series the Statistics screen can draw (ROADMAP N35).
 *
 * Twenty-one of them exist across three enums reached today by three screens with three query shapes; an
 * entry is what makes "everything" one picker instead of three.
 */
data class MetricEntry(
    val key: MetricKey,
    val group: MetricGroup,
    @StringRes val labelRes: Int,
    val unit: MetricUnit,
    /** Bars for count-like series, a line for continuous ones (ROADMAP N38). */
    val isBars: Boolean,
    /** False where less is better: assistance (ROADMAP N17) and joint pain. */
    val higherIsBetter: Boolean = true,
    /** Whether the axis is anchored at zero, which is a property of the metric (ROADMAP N38). */
    val fromZero: Boolean = false,
    /**
     * An axis this metric never narrows below.
     *
     * A rating is the case that needs it: the scale is definitionally 1–10, so fitting the axis to the data
     * turns a 0.2 wobble into a full-height cliff — the reading looks catastrophic and is not. A quantity
     * needs the opposite treatment and gets it from [fromZero]. The bounds live here, beside the metric they
     * describe, rather than in the chart, which deliberately does not choose its own axis.
     *
     * The axis still *widens* to include a target outside the range: a target of 12 is a claim the user made
     * and it must be visible, even where the scale says it should not exist.
     */
    val fixedRange: ClosedRange<Double>? = null,
    /** True when the series means nothing until a lift is chosen. */
    val needsExercise: Boolean = false,
)

/**
 * Every series, in one place (ROADMAP N35).
 *
 * A registry rather than a fourth enum: these entries describe *how to show* a series, which is a
 * different question from what a metric means, and the answering of it is what the chart, the picker, the
 * axis and the readings list all need.
 */
object MetricRegistry {

    val entries: List<MetricEntry> = buildList {
        TrendMetric.entries.forEach { add(workoutEntry(it)) }
        ExerciseTrendMetric.entries.forEach { add(exerciseEntry(it)) }
        add(
            MetricEntry(
                key = MetricKey.Body(BodyMetric.WEIGHT),
                group = MetricGroup.BODY,
                labelRes = R.string.measurements_weight,
                unit = MetricUnit.KILOGRAMS,
                isBars = false,
            ),
        )
        add(
            MetricEntry(
                key = MetricKey.Body(BodyMetric.BODY_FAT),
                group = MetricGroup.BODY,
                labelRes = R.string.measurements_body_fat,
                unit = MetricUnit.PERCENT,
                isBars = false,
            ),
        )
        add(
            MetricEntry(
                key = MetricKey.Body(BodyMetric.MUSCLE),
                group = MetricGroup.BODY,
                labelRes = R.string.measurements_muscle,
                unit = MetricUnit.PERCENT,
                isBars = false,
            ),
        )
        TapeSite.entries.forEach { add(tapeEntry(it)) }
    }

    /** The entry for a key. Every key has one, which a test asserts in both directions. */
    fun entryFor(key: MetricKey): MetricEntry = entries.first { it.key == key }

    /**
     * The entry with this [MetricKey.id], or null.
     *
     * Null rather than a default, because the caller is usually reading a stored preference: an id from a
     * build that had a series this one does not should leave the screen on its default rather than silently
     * showing a different metric than the one that was chosen.
     */
    fun byId(id: String?): MetricEntry? = entries.firstOrNull { it.key.id == id }
}

private fun workoutEntry(metric: TrendMetric) = MetricEntry(
    key = MetricKey.Workout(metric),
    group = MetricGroup.WORKOUT,
    labelRes = when (metric) {
        TrendMetric.RPE -> R.string.trends_metric_rpe
        TrendMetric.MUSCLE_FEEL -> R.string.trends_metric_muscle
        TrendMetric.JOINT_PAIN -> R.string.trends_metric_joint
    },
    unit = MetricUnit.RATING,
    isBars = false,
    // A rating is a position on a known scale, so the axis keeps the scale's ends (ROADMAP N39).
    fixedRange = RATING_SCALE,
    // Pain is the one workout rating where more is worse.
    higherIsBetter = metric != TrendMetric.JOINT_PAIN,
)

private fun exerciseEntry(metric: ExerciseTrendMetric): MetricEntry {
    val unit = when (metric) {
        ExerciseTrendMetric.TOTAL_REPS -> MetricUnit.REPS
        ExerciseTrendMetric.RPE,
        ExerciseTrendMetric.MUSCLE_FEEL,
        ExerciseTrendMetric.JOINT_PAIN,
        -> MetricUnit.RATING

        else -> MetricUnit.KILOGRAMS
    }
    return MetricEntry(
        key = MetricKey.Exercise(metric),
        group = MetricGroup.EXERCISE,
        labelRes = metric.labelRes(),
        unit = unit,
        // Counting is what bars are for: a volume or a rep count is a quantity, not a position on a scale.
        isBars = metric == ExerciseTrendMetric.VOLUME || metric == ExerciseTrendMetric.TOTAL_REPS,
        // A rating keeps the scale's ends; a load does not have ends to keep (ROADMAP N39).
        fixedRange = if (unit == MetricUnit.RATING) RATING_SCALE else null,
        // The direction the app already knows (ROADMAP N17), plus pain, which works the same way.
        higherIsBetter = metric.higherIsBetter && metric != ExerciseTrendMetric.JOINT_PAIN,
        // A load starts at zero; a rating does not (ROADMAP N17, N38).
        fromZero = metric.isLoad,
        needsExercise = true,
    )
}

private fun tapeEntry(site: TapeSite) = MetricEntry(
    key = MetricKey.Tape(site),
    group = MetricGroup.BODY,
    labelRes = tapeLabel(site),
    unit = MetricUnit.CENTIMETRES,
    isBars = false,
)

private fun tapeLabel(site: TapeSite): Int = when (site) {
    TapeSite.NECK -> R.string.measurements_neck
    TapeSite.CHEST -> R.string.measurements_chest
    TapeSite.WAIST -> R.string.measurements_waist
    TapeSite.HIPS -> R.string.measurements_hips
    TapeSite.UPPER_ARM -> R.string.measurements_upper_arm
    TapeSite.THIGH -> R.string.measurements_thigh
    TapeSite.CALF -> R.string.measurements_calf
}

private fun ExerciseTrendMetric.labelRes(): Int = when (this) {
    ExerciseTrendMetric.HEAVIEST_SET -> R.string.exercise_trend_heaviest
    ExerciseTrendMetric.ESTIMATED_1RM -> R.string.exercise_trend_one_rep_max
    ExerciseTrendMetric.VOLUME -> R.string.exercise_trend_volume
    ExerciseTrendMetric.TOTAL_REPS -> R.string.exercise_trend_reps
    ExerciseTrendMetric.ASSISTANCE -> R.string.exercise_trend_assistance
    ExerciseTrendMetric.RPE -> R.string.trends_metric_rpe
    ExerciseTrendMetric.MUSCLE_FEEL -> R.string.trends_metric_muscle
    ExerciseTrendMetric.JOINT_PAIN -> R.string.trends_metric_joint
}

/** Stored units per displayed unit: grams, millimetres, tenths of a percent, half-points of RPE. */
private const val GRAMS_PER_KILOGRAM = 1000.0
private const val MILLIMETRES_PER_CENTIMETRE = 10.0
private const val TENTHS_PER_PERCENT = 10.0
private const val HALVES_PER_POINT = 2.0

/**
 * Every rating in the app is 1–10.
 *
 * The same scale [com.example.androidapp.domain.model.TenPointScale] validates for muscle feel and joint
 * pain, and the one RPE works in at half steps. Named once here so the axis a rating is drawn on cannot
 * drift from the scale the editor enforces.
 */
private val RATING_SCALE = TenPointScale.MIN.toDouble()..TenPointScale.MAX.toDouble()
