package com.example.androidapp.domain

import java.time.Duration
import java.time.Instant

/**
 * Rest-timer arithmetic (ROADMAP P1.4).
 *
 * The timer is stored as an absolute end instant, so "how long is left" is a
 * pure function of the clock rather than a counter someone has to keep ticking
 * correctly. That is what makes it survive a process death and stay accurate
 * while the screen is off.
 */
object RestTimer {

    private const val SECONDS_PER_MINUTE = 60

    /**
     * The default rest between sets. A user-configurable value is part of P1.4's
     * "configurable default"; until there is a settings screen, one constant.
     */
    const val DEFAULT_SECONDS = 90

    /** How much the +15s / -15s controls move the timer. */
    const val ADJUST_STEP_SECONDS = 15

    /** Whole seconds left, never negative. 0 means the rest is over or not running. */
    fun remainingSeconds(restEndsAt: Instant?, now: Instant): Int {
        val endsAt = restEndsAt ?: return 0
        return Duration.between(now, endsAt).seconds.coerceAtLeast(0L).toInt()
    }

    fun isRunning(restEndsAt: Instant?, now: Instant): Boolean =
        remainingSeconds(restEndsAt, now) > 0

    /** `m:ss`, matching how the workout's elapsed time is shown. */
    fun format(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return "${safe / SECONDS_PER_MINUTE}:${(safe % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
    }
}
