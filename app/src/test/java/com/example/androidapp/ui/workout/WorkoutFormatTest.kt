package com.example.androidapp.ui.workout

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for the workout display formatting.
 *
 * These pin down the boundary cases that are easy to get wrong by hand and
 * invisible until a workout is long enough to matter.
 */
class WorkoutFormatTest {

    @Test
    fun elapsed_underAMinute_padsSeconds() {
        assertEquals("0:00", WorkoutFormat.elapsed(Duration.ZERO))
        assertEquals("0:07", WorkoutFormat.elapsed(Duration.ofSeconds(7)))
    }

    @Test
    fun elapsed_underAnHour_showsMinutesAndSeconds() {
        assertEquals("1:05", WorkoutFormat.elapsed(Duration.ofSeconds(65)))
        assertEquals("59:59", WorkoutFormat.elapsed(Duration.ofSeconds(3_599)))
    }

    @Test
    fun elapsed_pastAnHour_addsTheHourField() {
        // The boundary a naive "%02d:%02d" implementation gets wrong.
        assertEquals("1:00:00", WorkoutFormat.elapsed(Duration.ofSeconds(3_600)))
        assertEquals("2:03:04", WorkoutFormat.elapsed(Duration.ofSeconds(7_384)))
    }

    @Test
    fun elapsed_negativeClampsToZero() {
        // A clock that moves backwards must not render "-1:-30".
        assertEquals("0:00", WorkoutFormat.elapsed(Duration.ofSeconds(-90)))
    }

    @Test
    fun clockTime_usesTheGivenZone() {
        val instant = Instant.parse("2026-09-28T07:42:00Z")

        assertEquals("07:42", WorkoutFormat.clockTime(instant, ZoneId.of("UTC")))
        assertEquals("09:42", WorkoutFormat.clockTime(instant, ZoneId.of("Europe/Berlin")))
    }
}
