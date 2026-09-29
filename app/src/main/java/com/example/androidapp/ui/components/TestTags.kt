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

    /** RPE and the set comment (ROADMAP N6). Both may be left empty. */
    const val SET_RPE_FIELD = "set_rpe_field"
    const val SET_NOTE_FIELD = "set_note_field"

    /** A logged set, tappable to edit it. */
    const val SET_ROW = "set_row"
    const val SET_SAVE = "set_save"
    const val SET_CANCEL = "set_cancel"
    const val SET_INCREASE_WEIGHT = "set_increase_weight"
    const val SET_DECREASE_WEIGHT = "set_decrease_weight"
    const val SET_INCREASE_REPS = "set_increase_reps"
    const val SET_DECREASE_REPS = "set_decrease_reps"

    /**
     * Ending an exercise (ROADMAP N7). The two actions are mutually exclusive, so
     * a test can assert which state a row is in without matching on English.
     */
    const val EXERCISE_DONE = "exercise_done"
    const val EXERCISE_REOPEN = "exercise_reopen"
    const val EXERCISE_FINISHED_LABEL = "exercise_finished_label"

    const val HOME_TITLE = "home_title"
    const val HOME_START = "home_start"
    const val HOME_RESUME = "home_resume"
    const val HOME_RECENT_ROW = "home_recent_row"
    const val HOME_SEE_ALL = "home_see_all"
    const val HOME_FIRST_RUN = "home_first_run"
    const val HOME_NO_RECENT = "home_no_recent"

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

    /**
     * The picker's "new exercise" action (ROADMAP N2) and the dialog it opens.
     * The library destination never renders these, so their presence is itself
     * the behaviour under test.
     */
    const val LIBRARY_NEW_EXERCISE = "library_new_exercise"
    const val NEW_EXERCISE_NAME = "new_exercise_name"
    const val NEW_EXERCISE_SAVE = "new_exercise_save"
    const val NEW_EXERCISE_CANCEL = "new_exercise_cancel"

    /** The exercise detail screen's edit mode (ROADMAP N2, N5). */
    const val EXERCISE_EDIT = "exercise_edit"
    const val EXERCISE_EDIT_NAME = "exercise_edit_name"
    const val EXERCISE_EDIT_MUSCLE = "exercise_edit_muscle"
    const val EXERCISE_EDIT_EQUIPMENT = "exercise_edit_equipment"
    const val EXERCISE_EDIT_PATTERN = "exercise_edit_pattern"
    const val EXERCISE_EDIT_REST = "exercise_edit_rest"
    const val EXERCISE_EDIT_CUE = "exercise_edit_cue"
    const val EXERCISE_EDIT_SAVE = "exercise_edit_save"
    const val EXERCISE_EDIT_CANCEL = "exercise_edit_cancel"

    /**
     * The readiness note (ROADMAP N4): the prompt/edit dialog and the header row
     * that reaches it after the prompt has been answered or skipped.
     */
    const val READINESS_ROW = "readiness_row"
    const val READINESS_NOTE = "readiness_note"
    const val READINESS_SAVE = "readiness_save"
    const val READINESS_DISMISS = "readiness_dismiss"
}
