package com.example.androidapp.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The windows the Statistics picker offers (ROADMAP N35).
 *
 * `CUSTOM` is the one with its own dates; the rest are stored as what they *mean*, so that a range ending
 * today still ends today tomorrow.
 */
enum class RangeKind {
    LAST_7_DAYS,
    LAST_MONTH,
    LAST_3_MONTHS,
    LAST_6_MONTHS,
    LAST_YEAR,
    ALL,
    CUSTOM,
    ;

}

/**
 * How much of the series to show (ROADMAP N35).
 *
 * Stored as an intent rather than as two frozen dates, and that is the whole point of this type: a saved
 * "last 7 days" pinned to the dates it was chosen on would silently become a stale week the moment the
 * calendar moved. The dates are kept only for [RangeKind.CUSTOM], where the user picked them deliberately
 * and they mean exactly what they say.
 */
data class StatisticsRange(
    val kind: RangeKind = RangeKind.LAST_7_DAYS,
    /** Only meaningful for [RangeKind.CUSTOM]. */
    val from: LocalDate? = null,
    val to: LocalDate? = null,
)

/** A half-open window: from the start of the first day to the start of the day after the last. */
data class TimeWindow(val from: Instant, val toExclusive: Instant) {
    operator fun contains(instant: Instant): Boolean = instant >= from && instant < toExclusive
}

/**
 * The instants this range covers, or null when it covers everything.
 *
 * Resolved against [today] rather than against the clock inside, so the answer is a function of its inputs
 * and a test can ask what next week looks like.
 *
 * Days, not durations: "the last 7 days" means seven *dates* including today, so a reading at nine this
 * morning and one at nine tonight are in the same window whatever the length of the day. The last instant
 * of the window is the start of tomorrow, exclusive — the same half-open shape the measurements screen uses
 * to decide which day an entry belongs to.
 */
fun StatisticsRange.window(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): TimeWindow? {
    fun startOf(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    return when (kind) {
        RangeKind.ALL -> null
        RangeKind.CUSTOM -> {
            // Nothing chosen yet means nothing excluded, which is a truthful reading of an empty form and
            // better than an empty chart that looks like a failure.
            if (from == null || to == null) return null
            // A picker makes it easy to choose the ends the wrong way round; the intent is unambiguous.
            val first = minOf(from, to)
            val last = maxOf(from, to)
            TimeWindow(from = startOf(first), toExclusive = startOf(last.plusDays(ONE_DAY)))
        }

        RangeKind.LAST_7_DAYS -> daysBack(today, DAYS_BEFORE_TODAY_IN_A_WEEK, ::startOf)
        RangeKind.LAST_MONTH -> fromDate(today.minusMonths(ONE_MONTH), today, ::startOf)
        RangeKind.LAST_3_MONTHS -> fromDate(today.minusMonths(THREE_MONTHS), today, ::startOf)
        RangeKind.LAST_6_MONTHS -> fromDate(today.minusMonths(SIX_MONTHS), today, ::startOf)
        RangeKind.LAST_YEAR -> fromDate(today.minusYears(ONE_YEAR), today, ::startOf)
    }
}

private fun daysBack(today: LocalDate, days: Long, startOf: (LocalDate) -> Instant) =
    TimeWindow(from = startOf(today.minusDays(days)), toExclusive = startOf(today.plusDays(ONE_DAY)))

private fun fromDate(from: LocalDate, to: LocalDate, startOf: (LocalDate) -> Instant) =
    TimeWindow(from = startOf(from), toExclusive = startOf(to.plusDays(ONE_DAY)))

/**
 * The window's end is exclusive, so every range ends at the start of the day after its last.
 *
 * The six rather than seven is the same idea: seven days *including* today is today and the six before it.
 */
private const val ONE_DAY = 1L
private const val DAYS_BEFORE_TODAY_IN_A_WEEK = 6L
private const val ONE_MONTH = 1L
private const val THREE_MONTHS = 3L
private const val SIX_MONTHS = 6L
private const val ONE_YEAR = 1L
