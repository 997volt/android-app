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

    /** True when [query] appears in the name, muscle group or equipment. */
    private fun Exercise.matches(query: String): Boolean =
        name.contains(query, ignoreCase = true) ||
            primaryMuscle.label.contains(query, ignoreCase = true) ||
            equipment.label.contains(query, ignoreCase = true)
}
