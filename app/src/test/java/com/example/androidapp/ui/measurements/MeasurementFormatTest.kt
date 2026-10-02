package com.example.androidapp.ui.measurements

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The measurement units, as they read (ROADMAP N32).
 *
 * Explicit locales rather than the machine's, which is the same reason the formatter takes one: a test
 * that passed in one country and failed in another would be testing the runner.
 */
class MeasurementFormatTest {

    @Test
    fun aPercentage_readsAsATenth() {
        assertEquals("18.3", MeasurementFormat.percent(183, Locale.US))
    }

    @Test
    fun theDecimalSeparator_followsTheLanguage() {
        // The same number, which is the point of passing the locale rather than assuming one.
        assertEquals("18,3", MeasurementFormat.percent(183, Locale.GERMANY))
    }

    @Test
    fun aWholePercentage_keepsItsTenth() {
        assertEquals("18.0", MeasurementFormat.percent(180, Locale.US))
    }

    @Test
    fun aTapeReading_showsATenthOnlyWhenThereIsOne() {
        assertEquals("86.4", MeasurementFormat.centimetres(864L, Locale.US))
        // 86.0 would claim a precision a centimetre tape does not have.
        assertEquals("86", MeasurementFormat.centimetres(860L, Locale.US))
    }
}
