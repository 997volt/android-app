package com.example.androidapp.data.local

import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JVM tests for the enum storage format. These run in milliseconds and pin down
 * the two things that would silently corrupt persisted data.
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun muscleGroup_roundTripsByName() {
        MuscleGroup.entries.forEach { muscle ->
            assertEquals(muscle, converters.toMuscleGroup(converters.fromMuscleGroup(muscle)))
        }
    }

    @Test
    fun equipmentAndPattern_roundTripByName() {
        assertEquals(Equipment.CABLE, converters.toEquipment(converters.fromEquipment(Equipment.CABLE)))
        assertEquals(
            MovementPattern.HINGE,
            converters.toMovementPattern(converters.fromMovementPattern(MovementPattern.HINGE)),
        )
    }

    @Test
    fun storedFormIsTheEnumName_notAnOrdinal() {
        // The whole point: a stored value must survive someone reordering the
        // enum. If this ever becomes a number, every existing row is at risk.
        assertEquals("QUADS", converters.fromMuscleGroup(MuscleGroup.QUADS))
        assertEquals("BARBELL", converters.fromEquipment(Equipment.BARBELL))
    }

    @Test
    fun emptySecondaryMuscles_roundTripToEmptyList() {
        assertEquals(emptyList<MuscleGroup>(), converters.toMuscleGroups(converters.fromMuscleGroups(emptyList())))
    }

    @Test
    fun multipleSecondaryMuscles_preserveOrder() {
        val muscles = listOf(MuscleGroup.GLUTES, MuscleGroup.CORE, MuscleGroup.BACK)
        assertEquals(muscles, converters.toMuscleGroups(converters.fromMuscleGroups(muscles)))
    }

    @Test
    fun unknownStoredName_failsLoudlyRatherThanDefaulting() {
        // A silent fallback here would quietly mislabel every affected row.
        assertThrows(IllegalArgumentException::class.java) { converters.toMuscleGroup("NOT_A_MUSCLE") }
    }
}
