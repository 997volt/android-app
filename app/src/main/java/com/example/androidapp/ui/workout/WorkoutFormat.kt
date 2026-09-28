package com.example.androidapp.ui.workout

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

    private const val SECONDS_PER_HOUR = 3600
    private const val SECONDS_PER_MINUTE = 60

    /** `m:ss`, or `h:mm:ss` once past an hour. Negative durations clamp to zero. */
    fun elapsed(duration: Duration): String {
        val totalSeconds = duration.seconds.coerceAtLeast(0)
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE

        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** Wall-clock time of day, e.g. `07:42`. */
    fun clockTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(zone).format(instant)
}
