package com.example.androidapp.ui.components

import com.example.androidapp.domain.model.TapeSite

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
    /** The role a performed set was (ROADMAP N14). */
    const val SET_ROLE = "set_role"

    fun setRole(role: String) = "set_role_$role"

    /** The control that arms the next log, or — with a role — one of its options (N19). */
    fun exercisePendingRole(id: String, role: String? = null) =
        if (role == null) "exercise_pending_role_$id" else "exercise_pending_role_${id}_$role"

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

    /** The workout's Finish action, and the comment prompt behind it (ROADMAP N11). */
    const val ACTIVE_WORKOUT_FINISH = "active_workout_finish"

    /** Removing an exercise, and the confirmation it now asks for (ROADMAP B2). */
    const val EXERCISE_REMOVE = "exercise_remove"
    const val EXERCISE_REMOVE_CONFIRM = "exercise_remove_confirm"
    const val EXERCISE_REMOVE_CANCEL = "exercise_remove_cancel"

    /**
     * How an exercise felt (ROADMAP N8): the dialog's two fields and the workout
     * detail's row that reaches it.
     */
    const val RATING_MUSCLE_FIELD = "rating_muscle_field"
    const val RATING_JOINT_FIELD = "rating_joint_field"
    const val RATING_JOINT_NOTE_FIELD = "rating_joint_note_field"
    const val RATING_SAVE = "rating_save"
    const val RATING_DISMISS = "rating_dismiss"
    const val EXERCISE_RATING_ROW = "exercise_rating_row"

    const val HOME_TITLE = "home_title"
    const val HOME_START = "home_start"
    const val HOME_RESUME = "home_resume"

    /**
     * Export and import (ROADMAP B1). Tagged generically because the point of the
     * fix is *which screen* offers them, so a test asserts presence at home and
     * absence in the library using the same two tags.
     */
    const val DATA_EXPORT = "data_export"
    const val DATA_IMPORT = "data_import"

    /** The other half of the start action (ROADMAP N3): begin from a template. */
    const val HOME_START_FROM_TEMPLATE = "home_start_from_template"
    /** The way into programs, one tap from the plan they schedule (ROADMAP P3.3). */
    const val HOME_PROGRAMS = "home_programs"
    const val HOME_REPEAT_LAST = "home_repeat_last"
    const val DETAIL_SAVE_AS_PLAN = "detail_save_as_plan"
    const val DETAIL_PLAN_NAME = "detail_plan_name"
    const val DETAIL_PLAN_CONFIRM = "detail_plan_confirm"
    const val DETAIL_OPEN_NEW_PLAN = "detail_open_new_plan"
    const val HOME_MENU = "home_menu"
    const val HOME_RECENT_ROW = "home_recent_row"
    const val HOME_SEE_ALL = "home_see_all"
    const val HOME_FIRST_RUN = "home_first_run"
    const val HOME_NO_RECENT = "home_no_recent"

    const val LIBRARY_TITLE = "library_title"
    const val LIBRARY_SEARCH_FIELD = "library_search_field"

    /**
     * Two empty states, two tags — deliberately. The distinction is the feature
     * (P1.1a): an empty library wants different words from a search with no hits.
     */
    const val LIBRARY_EMPTY_LIBRARY = "library_empty_library"
    const val LIBRARY_NO_MATCH = "library_no_match"


    /** A read that failed, shown where the data would have been (ROADMAP B4). */
    const val LIBRARY_READ_ERROR = "library_read_error"
    const val EXERCISE_READ_ERROR = "exercise_read_error"

    /** The library's overflow and the one entry left in it after B1. */
    const val LIBRARY_MENU = "library_menu"
    const val LIBRARY_HISTORY = "library_history"

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

    /** The workout comment asked for on Finish (ROADMAP N11), and its row in history. */
    const val WORKOUT_NOTE = "workout_note"
    const val WORKOUT_NOTE_SAVE = "workout_note_save"
    const val WORKOUT_NOTE_SKIP = "workout_note_skip"

    /**
     * Workout templates (ROADMAP N3): the list, and one template's editor.
     *
     * Row-level tags are parameterised by id, so a test addresses the third
     * exercise by identity rather than by its position on screen.
     */
    const val TEMPLATES_TITLE = "templates_title"
    const val TEMPLATES_NEW = "templates_new"
    const val TEMPLATES_EMPTY = "templates_empty"
    const val TEMPLATE_EDIT_TITLE = "template_edit_title"
    const val TEMPLATE_NAME_FIELD = "template_name_field"
    const val TEMPLATE_NAME_SAVE = "template_name_save"
    const val TEMPLATE_ADD_EXERCISE = "template_add_exercise"
    const val TEMPLATE_NO_EXERCISES = "template_no_exercises"
    const val TEMPLATE_DELETE = "template_delete"
    const val TEMPLATE_DELETE_CONFIRM = "template_delete_confirm"

    /** A template in the list; tapping it edits, its button starts a workout. */
    fun templateRow(id: String) = "template_row_$id"

    fun templateStart(id: String) = "template_start_$id"

    fun templateExerciseRow(id: String) = "template_exercise_$id"

    /** Reordering a template's exercises: one helper, because up and down are one idea. */
    fun templateMove(id: String, up: Boolean) = "template_move_${if (up) "up" else "down"}_$id"

    fun templateRemove(id: String) = "template_remove_$id"

    /** Today's plan on home (ROADMAP N16). */
    fun homeStartPlan(id: String) = "home_start_plan_$id"

    /** A plan's sets (ROADMAP N14): the list, one target, and its rest and cue. */
    const val TEMPLATE_PLAN_ROW = "template_plan_row"
    const val TEMPLATE_PLAN_EMPTY = "template_plan_empty"
    const val TEMPLATE_PLAN_ADD = "template_plan_add"
    const val TEMPLATE_ADD_WARMUPS = "template_add_warmups"
    const val TEMPLATE_PLAN_DUPLICATE = "template_plan_duplicate"
    const val TEMPLATE_PLAN_CLOSE = "template_plan_close"
    const val TEMPLATE_SET_ROLE = "template_set_role"
    const val TEMPLATE_SET_WEIGHT = "template_set_weight"
    const val TEMPLATE_SET_REPS_MIN = "template_set_reps_min"
    const val TEMPLATE_SET_REPS_MAX = "template_set_reps_max"
    const val TEMPLATE_SET_RPE = "template_set_rpe"
    const val TEMPLATE_SET_NOTE = "template_set_note"
    const val TEMPLATE_SET_SAVE = "template_set_save"
    const val TEMPLATE_SET_CANCEL = "template_set_cancel"
    const val TEMPLATE_REST_FIELD = "template_rest_field"
    const val TEMPLATE_CUE_FIELD = "template_cue_field"
    const val TEMPLATE_REST_CUE_SAVE = "template_rest_cue_save"

    fun templatePlanSet(id: String) = "template_plan_set_$id"

    fun templatePlanRemove(id: String) = "template_plan_remove_$id"

    fun templateSetRole(role: String) = "template_set_role_$role"

    /** The editor's scrolling exercise list, so a test can scroll to a row. */
    const val TEMPLATE_EXERCISE_LIST = "template_exercise_list"

    /** The record just set (ROADMAP N23). */
    const val PERSONAL_RECORD = "personal_record"

    /** Why the next set is what it is (ROADMAP N22). */
    const val SUGGESTION_REASON = "suggestion_reason"

    /** Taking the app's proposal, which is the only way it becomes the prefill (ROADMAP N33). */
    const val SUGGESTION_ACCEPT = "suggestion_accept"

    /** The bottom bar's five roots (ROADMAP N34). */
    const val TAB_BAR = "tab_bar"
    const val TAB_WORKOUTS = "tab_workouts"
    const val TAB_HISTORY = "tab_history"
    const val TAB_STATISTICS = "tab_statistics"
    const val TAB_LIBRARY = "tab_library"
    const val TAB_SETTINGS = "tab_settings"


    /** The settings screen (ROADMAP N21): the screen, the current value, and each choice. */
    const val SETTINGS_SCREEN = "settings_screen"
    const val SETTINGS_REST_CURRENT = "settings_rest_current"
    const val SETTINGS_REST_CUE = "settings_rest_cue"
    const val SETTINGS_KEEP_SCREEN_ON = "settings_keep_screen_on"

    fun settingRest(seconds: Int) = "setting_rest_$seconds"

    /** Joining or leaving the superset above (ROADMAP N24). */
    fun supersetToggle(id: String) = "superset_toggle_$id"

    /** The review a finished workout gets (ROADMAP N20). */
    const val SUMMARY_DIALOG = "summary_dialog"
    const val SUMMARY_DONE = "summary_done"

    /** The one-tap log itself (ROADMAP N19 gave it a companion, and it needed a name). */
    const val SET_LOG = "set_log"

    /** The role armed for the next one-tap log (ROADMAP N19). */
    /** Starting over (ROADMAP N18): the menu entry, the field and the confirm button. */
    const val HOME_CLEAR_DATA = "home_clear_data"
    const val CLEAR_CONFIRM_FIELD = "clear_confirm_field"
    const val CLEAR_CONFIRM_ACTION = "clear_confirm_action"
    const val CLEAR_EXPORT_FIRST = "clear_export_first"
    const val CLEAR_CANCEL = "clear_cancel"

    /** One exercise's own trends (ROADMAP N17). */
    const val EXERCISE_TRENDS = "exercise_trends"

    fun historyExerciseTrends(id: String) = "history_exercise_trends_$id"

    /** Pinning a plan to a weekday (ROADMAP N16). */
    fun templateWeekday(day: String) = "template_weekday_$day"

    /**
     * Programs (ROADMAP P3.3), grouped so this object stays under its ceiling.
     *
     * The same shape as templates: a flat list with rows addressed by id, so a test names
     * the slot it means rather than counting positions.
     */
    object Programs {
        const val NEW = "programs_new"
        const val EMPTY = "programs_empty"
        const val NAME_FIELD = "program_name_field"
        const val NAME_SAVE = "program_name_save"
        const val ACTIVE = "program_active"
        const val ADD_SLOT = "program_add_slot"
        const val NO_SLOTS = "program_no_slots"
        const val DELETE = "program_delete"
        const val DELETE_CONFIRM = "program_delete_confirm"
        const val PICKER = "program_template_picker"
        const val PICKER_EMPTY = "program_template_picker_empty"

        /** The point-of-start question (P3.3): the missed day, and the two answers. */
        const val SKIP_PROMPT = "program_skip_prompt"
        const val SKIP_DO_NOW = "program_skip_do_now"
        const val SKIP_CONTINUE = "program_skip_continue"

        /** A program in the list; tapping it edits, its button makes it the active one. */
        fun row(id: String) = "program_row_$id"

        fun use(id: String) = "program_use_$id"

        fun slot(id: String) = "program_slot_$id"

        fun move(id: String, up: Boolean) = "program_slot_move_${if (up) "up" else "down"}_$id"

        fun slotWeekday(id: String, day: String) = "program_slot_weekday_${day}_$id"

        fun removeSlot(id: String) = "program_slot_remove_$id"

        fun pickTemplate(id: String) = "program_pick_template_$id"
    }

    /** The measurements screen's own tags (ROADMAP N32), grouped so this object stays under its ceiling. */
    object Measurements {
        const val ADD = "measurements_add"
        const val WEIGHT = "measurements_weight"
        const val BODY_FAT = "measurements_body_fat"
        const val MUSCLE = "measurements_muscle"
        const val CONFIRM = "measurements_confirm"
    
        /** One per tape site, so a test names the site rather than a position. */
        fun tape(site: TapeSite) = "measurements_tape_${site.name.lowercase()}"
    
        fun delete(id: String) = "measurements_delete_$id"
    }

    /** The Statistics screen's own tags (ROADMAP N35), grouped so this object stays under its ceiling. */
    object Statistics {
        /** The Statistics screen (ROADMAP N35). */
        const val OVERVIEW_WORKOUTS = "statistics_overview_workouts"
        const val OVERVIEW_VOLUME = "statistics_overview_volume"
        const val OVERVIEW_RECORDS = "statistics_overview_records"
        const val METRIC = "statistics_metric"
        const val CHART = "statistics_chart"
        const val CHART_FIRST_DATE = "statistics_chart_first_date"
        const val CHART_LAST_DATE = "statistics_chart_last_date"
        const val CHART_MIN = "statistics_chart_min"
        const val CHART_MAX = "statistics_chart_max"
        const val CHOOSE_LIFT = "statistics_choose_lift"
        const val LIFT = "statistics_lift"
        const val MEASUREMENTS = "statistics_measurements"
        const val READINGS_TOGGLE = "statistics_readings_toggle"
        const val READINGS_AVERAGE = "statistics_readings_average"
        const val READINGS_TREND = "statistics_readings_trend"
        const val MOVING_AVERAGE = "statistics_moving_average"
        const val GOAL = "statistics_goal"
        const val GOAL_SET = "statistics_goal_set"
        const val GOAL_FIELD = "statistics_goal_field"
        const val GOAL_CONFIRM = "statistics_goal_confirm"
        const val GOAL_CLEAR = "statistics_goal_clear"

        /** One period the moving average can be taken over. */
        fun movingAveragePeriod(period: Int) = "statistics_moving_average_$period"

        /** One reading, addressed by its place in the list — newest first, so 0 is the latest. */
        fun reading(index: Int) = "statistics_reading_$index"

        /** One row of the lift picker, named by the exercise it selects. */
        fun lift(id: String) = "statistics_lift_$id"
        const val CUSTOM_FROM = "statistics_custom_from"
        const val CUSTOM_TO = "statistics_custom_to"
        const val CUSTOM_APPLY = "statistics_custom_apply"
    
        /** One range chip, and one picker row, named by what they select. */
        fun range(kind: String) = "statistics_range_$kind"
    
        fun metric(id: String) = "statistics_metric_$id"
    }
}
