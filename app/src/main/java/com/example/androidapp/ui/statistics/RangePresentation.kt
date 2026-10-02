package com.example.androidapp.ui.statistics

import androidx.annotation.StringRes
import com.example.androidapp.R
import com.example.androidapp.domain.model.RangeKind
import com.example.androidapp.domain.model.StatisticsRange
import com.example.androidapp.domain.model.window
import java.time.LocalDate
import java.time.ZoneId

/**
 * The parts of a range that only the screen needs (ROADMAP N35).
 *
 * The window itself is domain: what a range *covers* is a fact about time. Its label names a string
 * resource and its filter speaks in readings, which are the screen's, so they live here.
 */
@get:StringRes
val RangeKind.labelRes: Int
    get() = when (this) {
        RangeKind.LAST_7_DAYS -> R.string.range_7_days
        RangeKind.LAST_MONTH -> R.string.range_month
        RangeKind.LAST_3_MONTHS -> R.string.range_3_months
        RangeKind.LAST_6_MONTHS -> R.string.range_6_months
        RangeKind.LAST_YEAR -> R.string.range_year
        RangeKind.ALL -> R.string.range_all
        RangeKind.CUSTOM -> R.string.range_custom
    }

/** The readings inside the window, gaps and all: a moment with no value is still a moment. */
fun StatisticsRange.inWindow(
    readings: List<MetricReading>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<MetricReading> {
    val bounds = window(today, zone) ?: return readings
    return readings.filter { it.at in bounds }
}
