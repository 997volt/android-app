package com.example.androidapp.domain.model

/**
 * An entry in the exercise library (ROADMAP P1.1).
 *
 * [id] is a stable, human-readable slug for seeded exercises (for example
 * `barbell-bench-press`) rather than a database row id. That keeps a logged
 * set pointing at the same movement across export/import (P1.12) and a future
 * sync (P4.9), and stops ids shifting when the seed list is reordered.
 * User-created exercises (P3.1) will use a UUID instead.
 */
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: Equipment,
    val movementPattern: MovementPattern,
    val isCustom: Boolean = false,
)
