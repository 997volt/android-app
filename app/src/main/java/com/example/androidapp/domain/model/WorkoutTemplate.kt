package com.example.androidapp.domain.model

import java.time.DayOfWeek

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
    /**
     * The weekday this plan belongs to, or null when it is not scheduled (ROADMAP N16).
     *
     * A *living* template rather than a dated instance: editing this plan changes every
     * future occurrence of its day, and what was performed is the record.
     */
    val weekday: DayOfWeek? = null,
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
    /** A rest this exercise prescribes, or null to use the library's (N14). */
    val restSeconds: Int? = null,
    /** A cue this exercise prescribes, or null to use the library's (N14). */
    val techniqueNote: String? = null,
    /** The planned sets, in order (ROADMAP N14). Empty for a template with none. */
    val sets: List<TemplateSet> = emptyList(),
)

/**
 * One planned set on a template exercise (ROADMAP N14).
 *
 * A target, not a record: the set the user logs is a separate row and is expected to
 * differ, and nothing verifies the plan. Every target is nullable, because "work up
 * to a heavy single" has no weight to write down and a zero would be a claim the app
 * cannot check.
 */
data class TemplateSet(
    val id: String,
    val templateExerciseId: String,
    val setIndex: Int,
    val role: SetType = SetType.NORMAL,
    val targetWeightGrams: Long? = null,
    /** The assistance the plan prescribes, or null (ROADMAP N15). */
    val targetAssistanceGrams: Long? = null,
    /** The target reps: both ends nullable, since a plan may write only an upper bound. */
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpeHalves: Int? = null,
    val note: String? = null,
)
