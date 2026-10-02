package com.example.androidapp.ui.statistics

/**
 * A trailing simple moving average (ROADMAP N40).
 *
 * **The period counts readings, not days**, and that is the decision worth stating. For a daily weigh-in the
 * two are the same thing, which is the case the roadmap has in mind — "daily weight is noise, and the
 * seven-day mean is the signal" — but for a lift trained twice a week a seven-*day* window would often contain
 * one reading and average nothing, while a seven-*reading* window smooths four weeks of training. A window
 * that is sometimes empty is worse than one whose unit is stated.
 *
 * **It emits from the first reading**, averaging over what exists so far, rather than waiting for a full
 * period. A chart that began a week in would hide the first week of the signal it was added to show, and the
 * mean of one reading is that reading.
 *
 * A moment that recorded nothing contributes nothing: the window holds the readings that exist, so a gap
 * neither counts towards the period nor drags the mean down.
 */
fun MetricSeries.movingAverage(period: Int): List<MetricReading> {
    if (period < 1) return emptyList()

    val recorded = readings.filter { it.value != null }
    return recorded.mapIndexed { index, reading ->
        val window = recorded.subList(maxOf(0, index - period + 1), index + 1)
        MetricReading(at = reading.at, value = window.mapNotNull { it.value }.average())
    }
}

/** The periods the screen offers, with [DAYS] the one a daily weigh-in wants. */
val MOVING_AVERAGE_PERIODS = listOf(DAYS, FORTNIGHT, MONTH)

/** Seven readings: the default the roadmap names, and a week for anything recorded daily. */
const val DAYS = 7

/** Two weeks, and a month: the two longer windows worth offering without making the mean meaningless. */
const val FORTNIGHT = 14
const val MONTH = 30
