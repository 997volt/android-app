package com.example.androidapp.ui.history

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Presentation formatting for history (ROADMAP P1.6).
 *
 * Pure functions with explicit [Locale]/[ZoneId], so they are covered by fast JVM
 * tests and cannot silently depend on the machine's defaults.
 *
 * The zone matters more than it looks: history groups by *local* month, so a user
 * who travels sees a past workout in a different group. That is the right
 * behaviour, and it is why these take the zone rather than assuming UTC — see the
 * note in the roadmap about storing the offset that was true at the time.
 */
object HistoryFormat {

    fun date(
        instant: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(zone)
        .format(instant)

    /** e.g. `September 2026`, in the user's language. */
    fun month(month: YearMonth, locale: Locale = Locale.getDefault()): String {
        val name = month.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
        return "$name ${month.year}"
    }
}
