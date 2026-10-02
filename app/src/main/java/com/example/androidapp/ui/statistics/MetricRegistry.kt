package com.example.androidapp.ui.statistics

import androidx.annotation.StringRes
import com.example.androidapp.R
import com.example.androidapp.domain.Weight
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.domain.model.TrendMetric
import com.example.androidapp.domain.model.asRating
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

        override fun parse(text: String): Double? = text.trim().toDoubleOrNull()?.times(GRAMS_PER_KILOGRAM)
    },
    RATING(R.string.unit_rating) {
        override fun format(value: Double): String = value.asRating()

        override fun parse(text: String): Double? = text.trim().toDoubleOrNull()?.times(HALVES_PER_POINT)
    },
    REPS(R.string.unit_reps) {
        override fun format(value: Double): String = value.toInt().toString()
    },
    PERCENT(R.string.unit_percent) {
        override fun format(value: Double): String = MeasurementFormat.percent(value.toInt())

        override fun parse(text: String): Double? = text.trim().toDoubleOrNull()?.times(TENTHS_PER_PERCENT)
    },
    CENTIMETRES(R.string.unit_centimetres) {
        override fun format(value: Double): String = MeasurementFormat.centimetres(value.toLong())

        override fun parse(text: String): Double? =
            text.trim().toDoubleOrNull()?.times(MILLIMETRES_PER_CENTIMETRE)
    },
    ;

    /** The number without its unit: the unit belongs to the label's language, as everywhere else. */
    abstract fun format(value: Double): String

    /**
     * What a typed number means in the metric's stored units (ROADMAP N39).
     *
     * The inverse of [format], and needed because a target is *typed* as "80" kilograms and *stored* as 80000
     * grams, like every other weight in the app. Null for anything that is not a number, so a typo cannot
     * become a target.
     */
    open fun parse(text: String): Double? = text.trim().toDoubleOrNull()
}

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
    // Pain is the one workout rating where more is worse.
    higherIsBetter = metric != TrendMetric.JOINT_PAIN,
)

private fun exerciseEntry(metric: ExerciseTrendMetric) = MetricEntry(
    key = MetricKey.Exercise(metric),
    group = MetricGroup.EXERCISE,
    labelRes = metric.labelRes(),
    unit = when (metric) {
        ExerciseTrendMetric.TOTAL_REPS -> MetricUnit.REPS
        ExerciseTrendMetric.RPE,
        ExerciseTrendMetric.MUSCLE_FEEL,
        ExerciseTrendMetric.JOINT_PAIN,
        -> MetricUnit.RATING

        else -> MetricUnit.KILOGRAMS
    },
    // Counting is what bars are for: a volume or a rep count is a quantity, not a position on a scale.
    isBars = metric == ExerciseTrendMetric.VOLUME || metric == ExerciseTrendMetric.TOTAL_REPS,
    // The direction the app already knows (ROADMAP N17), plus pain, which works the same way.
    higherIsBetter = metric.higherIsBetter && metric != ExerciseTrendMetric.JOINT_PAIN,
    // A load starts at zero; a rating does not (ROADMAP N17, N38).
    fromZero = metric.isLoad,
    needsExercise = true,
)

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
