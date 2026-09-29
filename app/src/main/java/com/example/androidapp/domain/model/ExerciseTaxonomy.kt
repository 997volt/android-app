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

/**
 * Primary muscle worked. Drives grouping and the volume-per-group charts (P2.3).
 *
 * [OTHER] is the "not specified yet" value a custom exercise is created with
 * (ROADMAP N2), next to [Equipment.OTHER] which already existed. It is appended
 * rather than inserted so the enum reads the same as before; order is cosmetic
 * anyway, because enums are stored by name.
 */
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
    OTHER("Other"),
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
    OTHER("Other"),
}

/**
 * The `Quads · Barbell` line shown under an exercise's name, with any part that
 * has not been filled in yet left out rather than rendered as `Other · Other`
 * (ROADMAP N2).
 *
 * Returns null when nothing is known: a custom exercise is created with a name
 * only, and an empty line is more honest than one that says "Other · Other".
 * Shared by the library/picker row and the active workout's exercise header so
 * the two cannot drift apart.
 */
fun taxonomySubtitle(primaryMuscle: MuscleGroup, equipment: Equipment): String? {
    val parts = buildList {
        if (primaryMuscle != MuscleGroup.OTHER) add(primaryMuscle.label)
        if (equipment != Equipment.OTHER) add(equipment.label)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
