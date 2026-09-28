package com.example.androidapp.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (ROADMAP F2).
 *
 * Navigation Compose derives each destination's arguments from the properties
 * of these types, so a missing or misspelled argument is a compile error rather
 * than a crash at runtime. The generated route strings are an implementation
 * detail we never hand-write.
 */

/** The exercise library, and the app's start destination. */
@Serializable
data object ExerciseLibrary

/** Detail for a single exercise. */
@Serializable
data class ExerciseDetail(val exerciseId: String)
