package com.example.androidapp

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {

    @Test
    fun text_formatsName() {
        assertEquals("Hello, Android!", Greeting.text("Android"))
    }

    @Test
    fun increment_advancesByOneByDefault() {
        assertEquals(1, Greeting.increment(0))
        assertEquals(42, Greeting.increment(41))
    }

    @Test
    fun increment_ignoresNegativeSteps() {
        assertEquals(5, Greeting.increment(5, -3))
    }
}
