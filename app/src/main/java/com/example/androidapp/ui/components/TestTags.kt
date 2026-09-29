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
    const val SET_SAVE = "set_save"
    const val SET_CANCEL = "set_cancel"
    const val SET_INCREASE_WEIGHT = "set_increase_weight"
    const val SET_DECREASE_WEIGHT = "set_decrease_weight"
    const val SET_INCREASE_REPS = "set_increase_reps"
    const val SET_DECREASE_REPS = "set_decrease_reps"

    const val LIBRARY_TITLE = "library_title"
    const val LIBRARY_SEARCH_FIELD = "library_search_field"

    /**
     * The library's primary button, tagged by *state* rather than by its caption.
     * "Start workout" and "Resume workout" are user-visible text a translation would
     * change; which of the two is showing is the behaviour under test.
     */
    const val LIBRARY_START = "library_start"
    const val LIBRARY_RESUME = "library_resume"

    /**
     * Two empty states, two tags — deliberately. The distinction is the feature
     * (P1.1a): an empty library wants different words from a search with no hits.
     */
    const val LIBRARY_EMPTY_LIBRARY = "library_empty_library"
    const val LIBRARY_NO_MATCH = "library_no_match"

    /** A library row, addressed by exercise id so tests need no display name. */
    fun exerciseRow(id: String) = "library_row_$id"
}
