package com.example.androidapp.domain

import java.time.Instant

/**
 * The clock, injected rather than read inline.
 *
 * Sessions, durations and (in P1.4) the rest timer all need to be asserted at
 * chosen instants. `Instant.now()` called directly inside a repository would make
 * that untestable without sleeping, so the clock is a dependency.
 */
fun interface TimeSource {
    fun now(): Instant
}

/** Epoch milliseconds — the representation the entities store. */
fun TimeSource.nowEpochMillis(): Long = now().toEpochMilli()
