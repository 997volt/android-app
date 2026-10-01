package com.example.androidapp.data.transfer

import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.TemplateExerciseEntity
import com.example.androidapp.data.local.TemplateSetEntity
import com.example.androidapp.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every column survives a trip through the backup codec.
 *
 * This exists because the codec is **hand-written**, listing each field by name, and it has
 * silently dropped an unnamed column three times — a set's location (N9), the assistance on a
 * set (N15), a template's weekday (N16). Each was found by hand, on a device, after shipping.
 * A dropped field is invisible in the other direction: an export still looks complete, and the
 * data is simply absent from the file.
 *
 * The guard is a round trip with **every field set to something distinctive**. A field the
 * codec forgets comes back as its default instead, and the assertion names it. The next column
 * this app adds — N24's superset group is the one waiting — fails here instead of turning up
 * missing in someone's backup.
 *
 * It is a JVM test on purpose: the mappers are pure, so this runs in the ordinary suite rather
 * than needing a device, and it cannot be skipped for being inconvenient.
 */
class BackupCodecRoundTripTest {

    @Test
    fun aSessionExercise_survivesTheCodec() {
        val entity = SessionExerciseEntity(
            id = "se1",
            sessionId = "s1",
            exerciseId = "back-squat",
            position = 3,
            restSeconds = 120,
            techniqueNote = "brace hard",
            finishedAt = 1_700_000_000_000L,
            muscleFeel = 8,
            jointPain = 2,
            jointPainNote = "left knee",
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_000_001L,
            deletedAt = null,
        )

        assertEquals(entity, entity.toDto().toEntity())
    }

    @Test
    fun aTemplateExercise_survivesTheCodec() {
        val entity = TemplateExerciseEntity(
            id = "te1",
            templateId = "t1",
            exerciseId = "back-squat",
            position = 2,
            restSeconds = 90,
            techniqueNote = "pause at the bottom",
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_000_001L,
            deletedAt = null,
        )

        assertEquals(entity, entity.toDto().toEntity())
    }

    @Test
    fun aSet_survivesTheCodec() {
        // The assistance column is why this test exists: N15 added it and the codec lost it.
        val entity = SetEntryEntity(
            id = "set1",
            sessionExerciseId = "se1",
            setIndex = 1,
            reps = 8,
            weightGrams = 100_000L,
            rpeHalves = 17,
            note = "slow eccentric",
            setType = SetType.TOP_SET,
            assistanceGrams = 20_000L,
            completedAt = 1_600_000_000_000L,
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_000_001L,
            deletedAt = null,
        )

        assertEquals(entity, entity.toDto().toEntity())
    }

    @Test
    fun aPlannedSet_survivesTheCodec() {
        val entity = TemplateSetEntity(
            id = "ts1",
            templateExerciseId = "te1",
            setIndex = 2,
            role = SetType.DROP,
            targetWeightGrams = 92_500L,
            targetAssistanceGrams = 15_000L,
            targetRepsMin = 2,
            targetRepsMax = 4,
            targetRpeHalves = 19,
            note = "leave one in the tank",
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_000_001L,
            deletedAt = null,
        )

        assertEquals(entity, entity.toDto().toEntity())
    }

    @Test
    fun aSession_survivesTheCodec() {
        val entity = WorkoutSessionEntity(
            id = "s1",
            startedAt = 1_600_000_000_000L,
            finishedAt = 1_600_000_001_000L,
            restEndsAt = 1_600_000_000_500L,
            readinessNote = "slept badly",
            notes = "good session",
            deletedAt = null,
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_000_001L,
        )

        val restored = entity.toDto().toEntity()

        // The one field the codec drops on purpose: a rest countdown is device-and-moment
        // state rather than training history, so a restored session must not come back
        // mid-rest. Everything else is asserted field for field.
        assertEquals(entity.copy(restEndsAt = null), restored)
        assertNull("and it is dropped deliberately", restored.restEndsAt)
    }

    @Test
    fun anExercise_survivesTheCodec() {
        // A custom exercise's taxonomy and its own rest, which is what N9 and N14 added.
        val entity = ExerciseEntity(
            id = "front-squat",
            name = "Front Squat",
            primaryMuscle = MuscleGroup.QUADS,
            secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.CORE),
            equipment = Equipment.BARBELL,
            movementPattern = MovementPattern.SQUAT,
            isCustom = true,
            restSeconds = 150,
            techniqueNote = "elbows up",
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_000_001L,
            deletedAt = null,
        )

        assertEquals(entity, entity.toDto().toEntity())
    }
}
