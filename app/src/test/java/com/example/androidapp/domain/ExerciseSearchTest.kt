package com.example.androidapp.domain

import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for library search — no device, no Android framework types.
 * This is the pattern the whole domain layer should follow.
 */
class ExerciseSearchTest {

    private val squat = exercise("back-squat", "Back Squat", MuscleGroup.QUADS, Equipment.BARBELL)
    private val bench = exercise("barbell-bench-press", "Barbell Bench Press", MuscleGroup.CHEST, Equipment.BARBELL)
    private val curl = exercise("hammer-curl", "Hammer Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL)
    private val library = listOf(squat, bench, curl)

    @Test
    fun blankQuery_returnsWholeLibrary() {
        assertEquals(library, ExerciseSearch.filter(library, ""))
        assertEquals(library, ExerciseSearch.filter(library, "   "))
    }

    @Test
    fun matchesNameIgnoringCase() {
        assertEquals(listOf(squat), ExerciseSearch.filter(library, "back squat"))
        assertEquals(listOf(bench), ExerciseSearch.filter(library, "BENCH"))
    }

    @Test
    fun matchesMuscleGroup() {
        assertEquals(listOf(bench), ExerciseSearch.filter(library, "chest"))
    }

    @Test
    fun matchesEquipment() {
        assertEquals(listOf(curl), ExerciseSearch.filter(library, "dumbbell"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(listOf(squat), ExerciseSearch.filter(library, "  squat  "))
    }

    @Test
    fun noMatch_returnsEmptyRatherThanEverything() {
        assertTrue(ExerciseSearch.filter(library, "zzz").isEmpty())
    }

    @Test
    fun preservesLibraryOrder() {
        assertEquals(listOf(squat, bench), ExerciseSearch.filter(library, "barbell"))
    }

    private fun exercise(
        id: String,
        name: String,
        muscle: MuscleGroup,
        equipment: Equipment,
    ) = Exercise(
        id = id,
        name = name,
        primaryMuscle = muscle,
        equipment = equipment,
        movementPattern = MovementPattern.ISOLATION,
    )
}
