package com.example.androidapp.ui.history

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What the date formatter does with the zone it is given (ROADMAP B39).
 *
 * N25's grouping was covered thoroughly and its *rendering* was not — which is the half that turned
 * out to be wrong, on Home. These are deliberately locale-independent: they compare two renderings of
 * the same instant rather than asserting a string, so they hold in any language and on any machine.
 */
class HistoryFormatTest {

    /** 20:00 UTC on 30 September is still September in London and already 1 October in Tokyo. */
    private val nearMidnight = Instant.parse("2026-09-30T20:00:00Z")

    @Test
    fun nearADayBoundary_theZoneChangesTheDayItReadsAs() {
        assertNotEquals(
            "the same instant is a different day in Tokyo",
            HistoryFormat.date(nearMidnight, ZoneOffset.UTC),
            HistoryFormat.date(nearMidnight, ZoneOffset.ofHours(9)),
        )
    }

    @Test
    fun awayFromABoundary_theZoneDoesNotChangeIt() {
        // The control: without this, the test above would pass if the formatter returned something
        // different for any two zones, including by ignoring the zone and varying by something else.
        val midday = Instant.parse("2026-09-30T12:00:00Z")

        assertEquals(
            HistoryFormat.date(midday, ZoneOffset.UTC),
            HistoryFormat.date(midday, ZoneOffset.ofHours(9)),
        )
    }
}
