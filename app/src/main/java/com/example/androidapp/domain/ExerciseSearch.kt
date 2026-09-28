package com.example.androidapp.domain

import com.example.androidapp.domain.model.Exercise

/**
 * Search over the exercise library.
 *
 * Deliberately pure and free of Android types so it is covered by fast JVM
 * tests rather than instrumented ones — the same split as the original
 * `Greeting`/`GreetingTest` scaffold pair.
 */
object ExerciseSearch {

    /** Exercises matching [query], in their original order. A blank query matches everything. */
    fun filter(exercises: List<Exercise>, query: String): List<Exercise> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return exercises
        return exercises.filter { it.matches(trimmed) }
    }

    /**
     * True when [query] appears anywhere we describe an exercise: its name, either
     * muscle group, its equipment or its movement pattern.
     *
     * Two deliberate choices (ROADMAP P1.1a):
     *
     *  - **Secondary muscles are searched too.** They were not, so typing
     *    "forearms" missed every row where forearms were *secondary* — a dead end,
     *    because nothing on screen explained why a row the user could see was not
     *    found.
     *  - **The enum name is matched as well as the label.** `label` is user-facing
     *    text that localization translates, so a search keyed only on it quietly
     *    stops working in another language. `name` is locale-stable, so
     *    `FOREARMS` still matches "forearms" whatever the display language says.
     */
    private fun Exercise.matches(query: String): Boolean =
        matches(query, name, name) ||
            (listOf(primaryMuscle) + secondaryMuscles).any { matches(query, it.label, it.name) } ||
            matches(query, equipment.label, equipment.name) ||
            matches(query, movementPattern.label, movementPattern.name)

    /** Matches against the display text *and* the locale-stable key. */
    private fun matches(query: String, label: String, stableKey: String): Boolean =
        label.contains(query, ignoreCase = true) || stableKey.contains(query, ignoreCase = true)
}
