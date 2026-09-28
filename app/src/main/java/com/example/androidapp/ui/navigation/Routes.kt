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

/**
 * The in-progress workout (ROADMAP P1.2).
 *
 * Carries no session id: the open session is already the single source of truth
 * in the database — which is precisely what makes recovery after process death
 * work (P1.8) — so copying it into the back stack would only create a second,
 * staleable answer to "which workout am I in".
 */
@Serializable
data object ActiveWorkout

/** Exercise picker, shown over an active workout. */
@Serializable
data object ExercisePicker

/** The history list (ROADMAP P1.6). */
@Serializable
data object WorkoutHistory

/** One past workout, read-only. */
@Serializable
data class WorkoutDetail(val sessionId: String)
