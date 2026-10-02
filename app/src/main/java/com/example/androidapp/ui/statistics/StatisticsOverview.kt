package com.example.androidapp.ui.statistics

import com.example.androidapp.domain.model.WorkoutSummary
import java.time.LocalDate
import java.time.ZoneId

/**
 * The three numbers at the top of the Statistics screen (ROADMAP N35).
 *
 * Three, deliberately, and no more for now: an overview that grows into a dashboard stops being one.
 */
data class StatisticsOverview(
    val workouts: Int = 0,
    /** Gram-reps, the unit the summary query already totals in. */
    val volumeGrams: Long = 0L,
    /**
     * Records set inside the range.
     *
     * Null rather than zero when the count was not supplied, because zero is a claim — it says "you set no
     * records" — and null says the question was not asked. `StatisticsDao.countRecordsIn` is the answer
     * when it has been: a record is judged per rep count against everything before it, and nothing stores
     * that a set *was* one.
     */
    val personalRecords: Int? = null,
)

/**
 * Totals for a range (ROADMAP N35).
 *
 * Pure, and over [WorkoutSummary] rather than a database, because the interesting part is which workouts
 * fall inside a window and what their pre-computed totals add up to — both of which are worth asserting
 * without a device. The volume is summed in [Long] grams, the unit the query already uses; converting to
 * tonnes or a Double would lose exactness the schema deliberately kept.
 */
fun statisticsOverview(
    range: StatisticsRange,
    today: LocalDate,
    sessions: List<WorkoutSummary>,
    zone: ZoneId = ZoneId.systemDefault(),
    personalRecords: Int? = null,
): StatisticsOverview {
    val window = range.window(today, zone)
    val inRange = if (window == null) sessions else sessions.filter { it.startedAt in window }

    return StatisticsOverview(
        workouts = inRange.size,
        volumeGrams = inRange.sumOf { it.volumeGrams },
        personalRecords = personalRecords,
    )
}
