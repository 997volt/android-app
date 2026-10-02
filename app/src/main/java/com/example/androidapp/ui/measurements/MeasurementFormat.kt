package com.example.androidapp.ui.measurements

import java.util.Locale

/**
 * How the stored measurement units read on screen (ROADMAP N32).
 *
 * The schema stores what a body is measured in — tenths of a percent, millimetres — and this is the
 * only place those become something a person says. **No unit is included**: the unit belongs to the
 * string resource, so a translation can put it where its language wants it, which is the rule
 * `Weight.display` follows for kilograms.
 *
 * The locale is a parameter rather than implied, so the decimal separator is testable: a German reader
 * sees `18,3` and an English one `18.3`, and that is a property of the number, not of the machine the
 * test happens to run on.
 */
object MeasurementFormat {

    /** Body fat or muscle, from tenths of a percent: `183` reads as `18.3`. */
    fun percent(tenths: Int, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", tenths / TENTHS_PER_PERCENT)

    /**
     * A tape site, from millimetres: `864` reads as `86.4` and `860` as `86`.
     *
     * The trailing zero is dropped rather than shown, because a centimetre tape does not measure to a
     * tenth and `86.0` claims a precision the reading does not have.
     */
    fun centimetres(millimetres: Long, locale: Locale = Locale.getDefault()): String {
        val centimetres = millimetres / MILLIMETRES_PER_CENTIMETRE
        return if (centimetres % 1.0 == 0.0) {
            String.format(locale, "%.0f", centimetres)
        } else {
            String.format(locale, "%.1f", centimetres)
        }
    }
}

private const val TENTHS_PER_PERCENT = 10.0
private const val MILLIMETRES_PER_CENTIMETRE = 10.0
