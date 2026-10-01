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

/** Home: recent workouts and the start action (ROADMAP N1). The start destination. */
@Serializable
data object WorkoutsHome

/** The exercise library: a reference you navigate to, not where the app opens. */
@Serializable
data object ExerciseLibrary

/** Detail for a single exercise. */
@Serializable
data class ExerciseDetail(val exerciseId: String)

/** One exercise's own trends (ROADMAP N17), reached from the library or a past lift. */
@Serializable
data class ExerciseTrends(val exerciseId: String)

/**
 * The app's settings (ROADMAP N21).
 *
 * `@Serializable` is what makes this navigable at all: every destination's route is
 * serialised by the type-safe navigation API, and a missing annotation is a crash the first
 * time the destination is reached — not a compile error, which is how it got as far as a
 * device.
 */
@Serializable
data object Settings

/**
 * The in-progress workout (ROADMAP P1.2).
 *
 * Carries no session id: the open session is already the single source of truth
 * in the database — which is precisely what makes recovery after process death
 * work (P1.8) — so copying it into the back stack would only create a second,
 * staleable answer to "which workout am I in".
 *
 * [templateId] is the exception, and it is not session state: it records *how this
 * workout was asked to begin* (ROADMAP N3). It is consumed once, when the session is
 * actually opened, and a resumed session ignores it — which is why it can live in
 * the back stack without becoming a second source of truth.
 */
@Serializable
data class ActiveWorkout(val templateId: String? = null)

/**
 * Exercise picker, shown over an active workout.
 *
 * [templateId] retargets the same picker at a template's exercise list (ROADMAP N3)
 * instead of at the open session: one screen, one search, two destinations, rather
 * than a second near-identical picker.
 */
@Serializable
data class ExercisePicker(val templateId: String? = null)

/** What the app collected, read back (ROADMAP N13). */
@Serializable
data object WorkoutTrends

/** The template list (ROADMAP N3). */
@Serializable
data object WorkoutTemplates

/** One template: its name and its ordered exercises. */
@Serializable
data class TemplateEditor(val templateId: String)

/** The history list (ROADMAP P1.6). */
@Serializable
data object WorkoutHistory

/** One past workout, read-only. */
@Serializable
data class WorkoutDetail(val sessionId: String)
