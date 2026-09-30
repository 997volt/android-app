package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RPE in half steps (ROADMAP N6, extended for 9.5).
 *
 * The representation is the point: halves in an `Int`, so 9.5 is exact and an average
 * of a session's RPE cannot drift. These tests are where "exact" is defined.
 */
class RpeTest {

    @Test
    fun aWholeNumber_parsesToItsHalves() {
        assertEquals(16, Rpe.parse("8"))
        assertEquals(2, Rpe.parse("1"))
        assertEquals(20, Rpe.parse("10"))
    }

    @Test
    fun aHalfStep_parses() {
        // The reason this change exists.
        assertEquals(19, Rpe.parse("9.5"))
        assertEquals(3, Rpe.parse("1.5"))
        // A comma is what a European keyboard produces, as with weights.
        assertEquals(19, Rpe.parse("9,5"))
        assertEquals(19, Rpe.parse(" 9.5 "))
    }

    @Test
    fun anythingFinerThanAHalf_isRefused_ratherThanRounded() {
        // A silent 9.3 → 9.5 would be a claim about the set that was never made.
        assertNull(Rpe.parse("9.3"))
        assertNull(Rpe.parse("9.25"))
        assertNull(Rpe.parse("9.0.5"))
    }

    @Test
    fun anythingOffTheScale_isRefused() {
        assertNull(Rpe.parse("0.5"))
        assertNull(Rpe.parse("0"))
        assertNull(Rpe.parse("10.5"))
        assertNull(Rpe.parse("11"))
        assertNull(Rpe.parse(""))
        assertNull(Rpe.parse("hard"))
        assertNull(Rpe.parse("-8"))
    }

    @Test
    fun formatting_dropsATrailingZero() {
        // `9` and `9.5` have to read as the same kind of thing in a list.
        assertEquals("8", Rpe.format(16))
        assertEquals("9.5", Rpe.format(19))
        assertEquals("10", Rpe.format(20))
        assertEquals("1", Rpe.format(2))
    }

    @Test
    fun theScaleAcceptsNull_andNothingOutsideIt() {
        assertTrue("not recorded is valid", Rpe.isValid(null))
        assertTrue(Rpe.isValid(2))
        assertTrue(Rpe.isValid(19))
        assertTrue(Rpe.isValid(20))
        assertFalse(Rpe.isValid(1))
        assertFalse(Rpe.isValid(21))
    }

    @Test
    fun parseAndFormat_roundTrip() {
        // What the editor relies on: an existing value opens as it was typed.
        listOf("1", "1.5", "8", "9.5", "10").forEach { typed ->
            assertEquals(typed, Rpe.format(Rpe.parse(typed)!!))
        }
    }
}
