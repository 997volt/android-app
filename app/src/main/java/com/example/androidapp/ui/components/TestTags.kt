package com.example.androidapp.ui.components

/**
 * Test tags for shared controls (ROADMAP P1.17).
 *
 * Tests target these instead of English literals, so a translated string cannot
 * break a test — and so a test failure means behaviour changed, not wording.
 */
object TestTags {
    const val SET_WEIGHT_FIELD = "set_weight_field"
    const val SET_REPS_FIELD = "set_reps_field"

    /** A logged set, tappable to edit it. */
    const val SET_ROW = "set_row"
}
