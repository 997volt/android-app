package com.example.androidapp.domain

import java.time.Instant

/**
 * Port for telling the user when a rest is over while the app is in the
 * background (ROADMAP P1.4).
 *
 * An interface rather than a direct `AlarmManager` call so the ViewModel stays
 * testable on the JVM — the workout screen's behaviour on a rest starting,
 * changing or ending is worth asserting without a device.
 *
 * Note this is **not** the foreground service of P4.2: scheduling one alarm at
 * the known end time needs no continuously running process, and no service type.
 * The trade is accuracy — see the implementation.
 */
interface RestNotifier {

    /** Schedules the "rest over" alert for [endsAt], replacing any previous one. */
    fun schedule(endsAt: Instant)

    /** Cancels a pending alert (rest skipped, adjusted, or the set deleted). */
    fun cancel()
}
