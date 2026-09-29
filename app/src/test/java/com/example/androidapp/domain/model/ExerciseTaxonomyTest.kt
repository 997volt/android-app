package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `Quads · Barbell` line under an exercise (ROADMAP N2).
 *
 * Pure logic, so it is a JVM test: the point is which parts are shown, not how
 * they are laid out.
 */
class ExerciseTaxonomyTest {

    @Test
    fun bothPartsKnown_areJoinedWithTheMiddleDot() {
        assertEquals(
            "Quads · Barbell",
            taxonomySubtitle(primaryMuscle = MuscleGroup.QUADS, equipment = Equipment.BARBELL),
        )
    }

    @Test
    fun anUnspecifiedMuscle_isLeftOut_ratherThanReadingOther() {
        assertEquals(
            "Dumbbell",
            taxonomySubtitle(primaryMuscle = MuscleGroup.OTHER, equipment = Equipment.DUMBBELL),
        )
    }

    @Test
    fun unspecifiedEquipment_isLeftOut_ratherThanReadingOther() {
        assertEquals(
            "Chest",
            taxonomySubtitle(primaryMuscle = MuscleGroup.CHEST, equipment = Equipment.OTHER),
        )
    }

    @Test
    fun aFullyUnspecifiedExercise_hasNoSubtitle() {
        // The state a custom exercise is created in: name only (N2).
        assertNull(taxonomySubtitle(primaryMuscle = MuscleGroup.OTHER, equipment = Equipment.OTHER))
    }
}
