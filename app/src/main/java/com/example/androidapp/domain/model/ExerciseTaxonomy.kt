package com.example.androidapp.domain.model

/**
 * How an exercise is classified. Kept as enums rather than free text so
 * filtering, balance warnings (ROADMAP P2.8) and auto-progression (P3.4) can
 * reason about them instead of parsing strings.
 *
 * The [label] values are user-facing. They live here for now to keep the seed
 * library compact; extracting them into `strings.xml` is part of the
 * localization pass (ROADMAP P5.4).
 */

/** Primary muscle worked. Drives grouping and the volume-per-group charts (P2.3). */
enum class MuscleGroup(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    CORE("Core"),
}

/** Equipment required, used to filter a library down to what the user has access to. */
enum class Equipment(val label: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    MACHINE("Machine"),
    CABLE("Cable"),
    BODYWEIGHT("Bodyweight"),
    KETTLEBELL("Kettlebell"),
    BAND("Band"),
    OTHER("Other"),
}

/** Movement pattern, the basis for the push/pull balance check (P2.8). */
enum class MovementPattern(val label: String) {
    HORIZONTAL_PUSH("Horizontal push"),
    VERTICAL_PUSH("Vertical push"),
    HORIZONTAL_PULL("Horizontal pull"),
    VERTICAL_PULL("Vertical pull"),
    SQUAT("Squat"),
    HINGE("Hinge"),
    LUNGE("Lunge"),
    CARRY("Carry"),
    ISOLATION("Isolation"),
    CORE("Core"),
}
