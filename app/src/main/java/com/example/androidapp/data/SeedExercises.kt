package com.example.androidapp.data

import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup

/**
 * The exercise library shipped with the app (ROADMAP P1.1).
 *
 * Ids are permanent slugs: a logged set references one of them, so renaming an
 * entry is safe but changing its id would orphan history. Add new entries at
 * the end and never renumber.
 *
 * This list becomes a prepackaged Room database in F5 so first launch stays
 * instant and works offline; until then it is the in-memory source of truth.
 */
internal object SeedExercises {

    val all: List<Exercise> = listOf(
        // ---- Chest ----
        barbell("barbell-bench-press", "Barbell Bench Press", MuscleGroup.CHEST, Equipment.BARBELL, MovementPattern.HORIZONTAL_PUSH, MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
        barbell("incline-dumbbell-press", "Incline Dumbbell Press", MuscleGroup.CHEST, Equipment.DUMBBELL, MovementPattern.HORIZONTAL_PUSH, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
        barbell("cable-fly", "Cable Fly", MuscleGroup.CHEST, Equipment.CABLE, MovementPattern.ISOLATION, MuscleGroup.SHOULDERS),
        barbell("push-up", "Push-Up", MuscleGroup.CHEST, Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, MuscleGroup.TRICEPS, MuscleGroup.CORE),

        // ---- Back ----
        barbell("deadlift", "Deadlift", MuscleGroup.BACK, Equipment.BARBELL, MovementPattern.HINGE, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.FOREARMS),
        barbell("pull-up", "Pull-Up", MuscleGroup.BACK, Equipment.BODYWEIGHT, MovementPattern.VERTICAL_PULL, MuscleGroup.BICEPS, MuscleGroup.FOREARMS),
        barbell("barbell-row", "Barbell Row", MuscleGroup.BACK, Equipment.BARBELL, MovementPattern.HORIZONTAL_PULL, MuscleGroup.BICEPS, MuscleGroup.FOREARMS),
        barbell("lat-pulldown", "Lat Pulldown", MuscleGroup.BACK, Equipment.CABLE, MovementPattern.VERTICAL_PULL, MuscleGroup.BICEPS),
        barbell("seated-cable-row", "Seated Cable Row", MuscleGroup.BACK, Equipment.CABLE, MovementPattern.HORIZONTAL_PULL, MuscleGroup.BICEPS),

        // ---- Shoulders ----
        barbell("overhead-press", "Overhead Press", MuscleGroup.SHOULDERS, Equipment.BARBELL, MovementPattern.VERTICAL_PUSH, MuscleGroup.TRICEPS, MuscleGroup.CORE),
        barbell("lateral-raise", "Lateral Raise", MuscleGroup.SHOULDERS, Equipment.DUMBBELL, MovementPattern.ISOLATION),
        barbell("face-pull", "Face Pull", MuscleGroup.SHOULDERS, Equipment.CABLE, MovementPattern.ISOLATION, MuscleGroup.BACK),

        // ---- Arms ----
        barbell("barbell-curl", "Barbell Curl", MuscleGroup.BICEPS, Equipment.BARBELL, MovementPattern.ISOLATION, MuscleGroup.FOREARMS),
        barbell("hammer-curl", "Hammer Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL, MovementPattern.ISOLATION, MuscleGroup.FOREARMS),
        barbell("triceps-pushdown", "Triceps Pushdown", MuscleGroup.TRICEPS, Equipment.CABLE, MovementPattern.ISOLATION),
        barbell("overhead-triceps-extension", "Overhead Triceps Extension", MuscleGroup.TRICEPS, Equipment.DUMBBELL, MovementPattern.ISOLATION),
        barbell("dip", "Dip", MuscleGroup.TRICEPS, Equipment.BODYWEIGHT, MovementPattern.VERTICAL_PUSH, MuscleGroup.CHEST, MuscleGroup.SHOULDERS),

        // ---- Legs ----
        barbell("back-squat", "Back Squat", MuscleGroup.QUADS, Equipment.BARBELL, MovementPattern.SQUAT, MuscleGroup.GLUTES, MuscleGroup.CORE),
        barbell("leg-press", "Leg Press", MuscleGroup.QUADS, Equipment.MACHINE, MovementPattern.SQUAT, MuscleGroup.GLUTES),
        barbell("bulgarian-split-squat", "Bulgarian Split Squat", MuscleGroup.QUADS, Equipment.DUMBBELL, MovementPattern.LUNGE, MuscleGroup.GLUTES),
        barbell("leg-extension", "Leg Extension", MuscleGroup.QUADS, Equipment.MACHINE, MovementPattern.ISOLATION),
        barbell("romanian-deadlift", "Romanian Deadlift", MuscleGroup.HAMSTRINGS, Equipment.BARBELL, MovementPattern.HINGE, MuscleGroup.GLUTES, MuscleGroup.BACK),
        barbell("lying-leg-curl", "Lying Leg Curl", MuscleGroup.HAMSTRINGS, Equipment.MACHINE, MovementPattern.ISOLATION),
        barbell("hip-thrust", "Hip Thrust", MuscleGroup.GLUTES, Equipment.BARBELL, MovementPattern.HINGE, MuscleGroup.HAMSTRINGS),
        barbell("walking-lunge", "Walking Lunge", MuscleGroup.GLUTES, Equipment.DUMBBELL, MovementPattern.LUNGE, MuscleGroup.QUADS),
        barbell("standing-calf-raise", "Standing Calf Raise", MuscleGroup.CALVES, Equipment.MACHINE, MovementPattern.ISOLATION),
        barbell("seated-calf-raise", "Seated Calf Raise", MuscleGroup.CALVES, Equipment.MACHINE, MovementPattern.ISOLATION),

        // ---- Core ----
        barbell("plank", "Plank", MuscleGroup.CORE, Equipment.BODYWEIGHT, MovementPattern.CORE),
        barbell("hanging-leg-raise", "Hanging Leg Raise", MuscleGroup.CORE, Equipment.BODYWEIGHT, MovementPattern.CORE, MuscleGroup.FOREARMS),
        barbell("cable-crunch", "Cable Crunch", MuscleGroup.CORE, Equipment.CABLE, MovementPattern.CORE),
        // ---- Competition and paused variants, plus accessory work ----
        barbell("competition-bench-press", "Competition Bench Press", MuscleGroup.CHEST, Equipment.BARBELL, MovementPattern.HORIZONTAL_PUSH, MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
        barbell("bench-press-speed-day", "Bench Press — Speed Day", MuscleGroup.CHEST, Equipment.BARBELL, MovementPattern.HORIZONTAL_PUSH, MuscleGroup.TRICEPS),
        barbell("paused-bench-press-3s", "3-Second Paused Bench Press", MuscleGroup.CHEST, Equipment.BARBELL, MovementPattern.HORIZONTAL_PUSH, MuscleGroup.TRICEPS),
        barbell("conventional-deadlift", "Conventional Deadlift", MuscleGroup.BACK, Equipment.BARBELL, MovementPattern.HINGE, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.FOREARMS),
        barbell("paused-back-squat", "Paused Back Squat", MuscleGroup.QUADS, Equipment.BARBELL, MovementPattern.SQUAT, MuscleGroup.GLUTES, MuscleGroup.CORE),
        barbell("push-press", "Push Press", MuscleGroup.SHOULDERS, Equipment.BARBELL, MovementPattern.VERTICAL_PUSH, MuscleGroup.TRICEPS, MuscleGroup.QUADS),
        barbell("machine-row", "Machine Row", MuscleGroup.BACK, Equipment.MACHINE, MovementPattern.HORIZONTAL_PULL, MuscleGroup.BICEPS),
        barbell("assisted-pull-up", "Assisted Pull-Up", MuscleGroup.BACK, Equipment.MACHINE, MovementPattern.VERTICAL_PULL, MuscleGroup.BICEPS),
        barbell("dumbbell-fly", "Dumbbell Fly", MuscleGroup.CHEST, Equipment.DUMBBELL, MovementPattern.ISOLATION),
        barbell("incline-dumbbell-arm-curl", "Incline Dumbbell Arm Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL, MovementPattern.ISOLATION, MuscleGroup.FOREARMS),
        barbell("dumbbell-skullcrusher", "Dumbbell Skullcrusher", MuscleGroup.TRICEPS, Equipment.DUMBBELL, MovementPattern.ISOLATION),
        // Rotator work: the two the user asked for by description rather than by name.
        barbell("dumbbell-rotator-raise", "Dumbbell Rotator Raise", MuscleGroup.SHOULDERS, Equipment.DUMBBELL, MovementPattern.ISOLATION),
        barbell("rotator-cable-to-side", "Rotator Cable to Side", MuscleGroup.SHOULDERS, Equipment.CABLE, MovementPattern.ISOLATION),
        barbell("two-arm-cable-pushdown", "Two-Arm Cable Pushdown", MuscleGroup.TRICEPS, Equipment.CABLE, MovementPattern.ISOLATION),
        barbell("cable-arm-curl", "Cable Arm Curl", MuscleGroup.BICEPS, Equipment.CABLE, MovementPattern.ISOLATION, MuscleGroup.FOREARMS),
    )

    /** Terse constructor: the seed list is long and every row repeats the same shape. */
    private fun barbell(
        id: String,
        name: String,
        primary: MuscleGroup,
        equipment: Equipment,
        pattern: MovementPattern,
        vararg secondary: MuscleGroup,
    ) = Exercise(
        id = id,
        name = name,
        primaryMuscle = primary,
        secondaryMuscles = secondary.toList(),
        equipment = equipment,
        movementPattern = pattern,
    )
}
