package com.example.androidapp.domain

import java.time.Duration
import java.util.Locale

/**
 * The app's one elapsed-time format: `m:ss`, or `h:mm:ss` past an hour.
 *
 * `RestTimer.format` and `WorkoutFormat.elapsed` had each grown their own copy of
 * this. Two copies of one rule is how they drift, and they already had: only the
 * workout's version handled hours, so a long rest would have rendered `90:00` while
 * a workout of the same length rendered `1:30:00`. It lives in `domain` because the
 * rest timer is domain and the UI is allowed to depend on domain, not the reverse.
 */
object DurationFormat {

    /** Negative input clamps to zero rather than rendering a negative time. */
    fun ofSeconds(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / SECONDS_PER_HOUR
        val minutes = (safe % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = safe % SECONDS_PER_MINUTE

        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    fun of(duration: Duration): String = ofSeconds(duration.seconds)

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
}
