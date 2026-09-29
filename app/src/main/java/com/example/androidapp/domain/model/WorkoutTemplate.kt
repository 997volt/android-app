package com.example.androidapp.domain.model

/**
 * A named, reusable workout (ROADMAP N3): a name and an ordered list of exercises,
 * started in one tap.
 *
 * [exerciseCount] is carried on the list rather than derived from [TemplateExercise]s
 * so the list screen can show "5 exercises" without loading the join table.
 */
data class WorkoutTemplate(
    val id: String,
    val name: String,
    val exerciseCount: Int = 0,
)

/**
 * One exercise in a template, at an explicit [position].
 *
 * Carries the library attributes because that is exactly what the editor displays,
 * and fetching them in the same query avoids an N+1 walk over the library.
 */
data class TemplateExercise(
    val id: String,
    val templateId: String,
    val exerciseId: String,
    val position: Int,
    val exerciseName: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
)
