package com.example.androidapp.ui.workout

import com.example.androidapp.domain.DurationFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Presentation formatting for a workout.
 *
 * Pure functions with an explicit [Locale]/[ZoneId], so they are covered by fast
 * JVM tests and cannot accidentally depend on the machine's default locale —
 * which is also what lint's DefaultLocale check would flag.
 */
object WorkoutFormat {

    /**
     * `m:ss`, or `h:mm:ss` once past an hour. Negative durations clamp to zero.
     *
     * Delegates so there is one implementation; see [DurationFormat].
     */
    fun elapsed(duration: Duration): String = DurationFormat.of(duration)

    /** Wall-clock time of day, e.g. `07:42`. */
    /** [zone] is required for the reason in `HistoryFormat.date` (ROADMAP B38). */
    fun clockTime(instant: Instant, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone).format(instant)
}
