package com.example.androidapp.domain.model

import java.time.Instant
import kotlin.math.roundToLong

/**
 * One finished workout's worth of *one exercise* (ROADMAP N17).
 *
 * N13 reads the app's signals across everything; this is the narrower question a lifter
 * actually asks — how is my bench press going — so every value here comes from the sets
 * logged for a single exercise in a single session.
 *
 * **Warm-up sets are excluded from every load series**, which is only expressible
 * because N14's roles exist: a warm-up must not become the "heaviest set" on a chart.
 * Drop and failure sets are working sets and count.
 */
data class ExerciseTrendPoint(
    val startedAt: Instant,
    /** The heaviest working set, or null when the session logged none. */
    val heaviestSetGrams: Long? = null,
    /** Epley's estimate from [heaviestSetGrams], or null when it cannot be estimated. */
    val estimatedOneRepMaxGrams: Long? = null,
    /** `reps × weight` over the working sets. An assisted set contributes nothing (N15). */
    val volumeGrams: Long = 0L,
    val totalReps: Int = 0,
    /**
     * The *least* assistance among the working sets, or null when none was assisted.
     *
     * The least, not the most: on an assisted machine the hardest set is the one with
     * the least help, and that is the number progress moves.
     */
    val leastAssistanceGrams: Long? = null,
    val averageRpe: Double? = null,
    val averageMuscleFeel: Double? = null,
    val averageJointPain: Double? = null,
)

/** The series a per-exercise screen can draw (ROADMAP N17). */
enum class ExerciseTrendMetric {
    HEAVIEST_SET,
    ESTIMATED_1RM,
    VOLUME,
    TOTAL_REPS,
    ASSISTANCE,
    RPE,
    MUSCLE_FEEL,
    JOINT_PAIN,
    ;

    /** The value this metric takes from a point, or null when it was not recorded. */
    fun valueOf(point: ExerciseTrendPoint): Double? = when (this) {
        HEAVIEST_SET -> point.heaviestSetGrams?.toDouble()
        ESTIMATED_1RM -> point.estimatedOneRepMaxGrams?.toDouble()
        VOLUME -> point.volumeGrams.toDouble()
        TOTAL_REPS -> point.totalReps.toDouble()
        ASSISTANCE -> point.leastAssistanceGrams?.toDouble()
        RPE -> point.averageRpe
        MUSCLE_FEEL -> point.averageMuscleFeel
        JOINT_PAIN -> point.averageJointPain
    }

    /**
     * Whether a bigger number is better (N17's assisted direction).
     *
     * On an assisted machine more help is not progress, so the screen says which way is
     * forward rather than drawing a climb that reads as improvement. The number itself
     * is plotted as recorded; only the label changes.
     */
    val higherIsBetter: Boolean get() = this != ASSISTANCE

    /** Whether this metric is a load, and so drawn from zero rather than on 1–10. */
    val isLoad: Boolean
        get() = this == HEAVIEST_SET || this == ESTIMATED_1RM || this == VOLUME ||
            this == TOTAL_REPS || this == ASSISTANCE
}

/** One metric's values across the window, oldest first, null where it was not recorded. */
fun List<ExerciseTrendPoint>.valuesOf(metric: ExerciseTrendMetric): List<Double?> =
    map { metric.valueOf(it) }

/** The most recent recorded value, not `last()`: the newest session may lack this metric. */
fun List<ExerciseTrendPoint>.latestValue(metric: ExerciseTrendMetric): Double? =
    mapNotNull { metric.valueOf(it) }.lastOrNull()

/** The mean of the recorded values, or null when none were. */
fun List<ExerciseTrendPoint>.averageValue(metric: ExerciseTrendMetric): Double? {
    val recorded = mapNotNull { metric.valueOf(it) }
    return if (recorded.isEmpty()) null else recorded.average()
}

/** How many sessions in the window recorded this metric at all. */
fun List<ExerciseTrendPoint>.recordedCount(metric: ExerciseTrendMetric): Int =
    count { metric.valueOf(it) != null }

/**
 * Epley's one-rep-max estimate, or null when it would be a guess dressed as a number.
 *
 * `weight × (1 + reps / 30)`. A single rep *is* the max, so it returns the weight. Beyond
 * [MAX_ESTIMATED_REPS] the formula stops meaning anything — it extrapolates a max from
 * a set that was nowhere near one — so this returns null rather than a confident figure
 * the app cannot stand behind.
 */
fun estimatedOneRepMax(weightGrams: Long, reps: Int): Long? = when {
    weightGrams <= 0L -> null
    reps == 1 -> weightGrams
    // Rounded to the nearest half-kilo: the estimate is not precise to the gram, and
    // printing 25.333 kg would dress a formula's output as a measurement.
    reps in 2..MAX_ESTIMATED_REPS ->
        (weightGrams * (1.0 + reps / EPLEY_REPS_DIVISOR) / ESTIMATE_STEP_GRAMS)
            .roundToLong() * ESTIMATE_STEP_GRAMS

    else -> null
}

/** The estimate is rounded to this, because it is an estimate. */
private const val ESTIMATE_STEP_GRAMS = 500L

/** Epley's own divisor: `1 + reps / 30`. */
private const val EPLEY_REPS_DIVISOR = 30.0

/** Above this, Epley's estimate is extrapolation rather than arithmetic. */
const val MAX_ESTIMATED_REPS = 12

/**
 * The flat rows a session contributes, as the DAO returns them (N17).
 *
 * One row per logged set, with the session's ratings repeated, and a single row with a
 * null weight when the session recorded the exercise but logged no set at all. Keeping
 * the shape flat is what lets [toExerciseTrendPoints] hold all the arithmetic — the
 * grouping, the warm-up exclusion and the averages — in one pure, tested place.
 */
data class ExerciseTrendRow(
    val sessionId: String,
    val startedAt: Instant,
    val muscleFeel: Int?,
    val jointPain: Int?,
    val weightGrams: Long?,
    val reps: Int?,
    val rpeHalves: Int?,
    val setType: SetType?,
    val assistanceGrams: Long?,
)

/**
 * Groups the rows by session, in the order they arrived (oldest first), and computes
 * each session's series.
 */
fun List<ExerciseTrendRow>.toExerciseTrendPoints(): List<ExerciseTrendPoint> =
    groupBy { it.sessionId }
        .map { (_, rows) -> rows.first().let { it.startedAt to rows } }
        .sortedBy { it.first }
        .map { (startedAt, rows) -> rows.toPoint(startedAt) }

private fun List<ExerciseTrendRow>.toPoint(startedAt: Instant): ExerciseTrendPoint {
    // A warm-up is not a working set: it must not become the heaviest set on the chart.
    val working = filter { it.weightGrams != null && it.setType != SetType.WARMUP }
    // And a set with no added weight is not a load: bodyweight and assisted work carry
    // reps and volume (both zero here), which is exactly what N15 decided they carry.
    val heaviest = working.filter { it.weightGrams!! > 0L }.maxByOrNull { it.weightGrams!! }

    return ExerciseTrendPoint(
        startedAt = startedAt,
        heaviestSetGrams = heaviest?.weightGrams,
        estimatedOneRepMaxGrams = heaviest?.let {
            estimatedOneRepMax(it.weightGrams!!, it.reps ?: 0)
        },
        volumeGrams = working.sumOf { (it.weightGrams ?: 0L) * (it.reps ?: 0) },
        totalReps = working.sumOf { it.reps ?: 0 },
        leastAssistanceGrams = working
            .mapNotNull { it.assistanceGrams?.takeIf { help -> help > 0L } }
            .minOrNull(),
        averageRpe = averageRpeOf(this),
        averageMuscleFeel = first().muscleFeel?.toDouble(),
        averageJointPain = first().jointPain?.toDouble(),
    )
}

/** The session's RPE average, in points, over the sets that recorded one (N6's halves). */
private fun averageRpeOf(rows: List<ExerciseTrendRow>): Double? {
    val halves = rows.mapNotNull { it.rpeHalves }
    return if (halves.isEmpty()) null else halves.average() / HALVES_PER_POINT
}

/** Half steps per RPE point (ROADMAP N6). */
private const val HALVES_PER_POINT = 2.0
