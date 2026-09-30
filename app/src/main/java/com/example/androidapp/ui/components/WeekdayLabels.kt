package com.example.androidapp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.androidapp.R
import java.time.DayOfWeek

/**
 * A weekday as a person reads it — long for a heading, short for a row of seven chips
 * (ROADMAP N16).
 *
 * One home for both, because there were two: the home screen's heading and the plan
 * editor's chips each mapped `DayOfWeek` to strings themselves (F8), so a missing day
 * could be added to one and forgotten in the other.
 */
@Composable
fun DayOfWeek.longLabel(): String = stringResource(
    when (this) {
        DayOfWeek.MONDAY -> R.string.weekday_monday
        DayOfWeek.TUESDAY -> R.string.weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.weekday_thursday
        DayOfWeek.FRIDAY -> R.string.weekday_friday
        DayOfWeek.SATURDAY -> R.string.weekday_saturday
        DayOfWeek.SUNDAY -> R.string.weekday_sunday
    },
)

/** `Mon` — seven of these have to fit a phone's width. */
@Composable
fun DayOfWeek.shortLabel(): String = stringResource(
    when (this) {
        DayOfWeek.MONDAY -> R.string.weekday_mon
        DayOfWeek.TUESDAY -> R.string.weekday_tue
        DayOfWeek.WEDNESDAY -> R.string.weekday_wed
        DayOfWeek.THURSDAY -> R.string.weekday_thu
        DayOfWeek.FRIDAY -> R.string.weekday_fri
        DayOfWeek.SATURDAY -> R.string.weekday_sat
        DayOfWeek.SUNDAY -> R.string.weekday_sun
    },
)
