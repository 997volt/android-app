package com.example.androidapp

/**
 * Pure presentation logic kept out of the composables so it can be covered by
 * fast JVM unit tests (see `src/test`).
 */
object Greeting {

    /** Builds the headline shown on screen. */
    fun text(name: String): String = "Hello, $name!"

    /** Counter value after [times] increments, never below the [start] value. */
    fun increment(start: Int, times: Int = 1): Int = start + times.coerceAtLeast(0)
}
