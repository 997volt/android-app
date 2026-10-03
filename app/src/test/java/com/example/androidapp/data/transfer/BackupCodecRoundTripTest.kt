package com.example.androidapp.data.transfer

import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.MeasurementEntity
import com.example.androidapp.data.local.ProgramEntity
import com.example.androidapp.data.local.ProgramSkipEntity
import com.example.androidapp.data.local.ProgramSlotEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.SetEntryEntity
import com.example.androidapp.data.local.TemplateExerciseEntity
import com.example.androidapp.data.local.TemplateSetEntity
import com.example.androidapp.data.local.WorkoutSessionEntity
import java.time.DayOfWeek
import com.google.common.truth.Truth.assertThat
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
    fun aMeasurement_survivesTheCodec() {
        // ROADMAP N32, and the reason this file exists: the codec lists every field by hand, so a
        // column it forgets comes back as its default — an export that looks complete and is not.
        // Every field is set to something distinctive, so nothing can pass by coincidence.
        val entity = MeasurementEntity(
            id = "m1",
            measuredAt = 1_700_000_000_000L,
            weightGrams = 82_450L,
            bodyFatTenths = 183,
            muscleTenths = 421,
            neckMm = 381L,
            chestMm = 1_042L,
            waistMm = 864L,
            hipsMm = 977L,
            upperArmMm = 363L,
            thighMm = 574L,
            calfMm = 389L,
            createdAt = 1_700_000_000_001L,
            updatedAt = 1_700_000_000_002L,
            deletedAt = 1_700_000_000_003L,
        )

        assertThat(entity.toDto().toEntity()).isEqualTo(entity)
    }

    @Test
    fun aSessionExercise_survivesTheCodec() {
        val entity = SessionExerciseEntity(
            id = "se1",
            sessionId = "s1",
            exerciseId = "back-squat",
            position = 3,
            // The column N24 added: if the codec ever forgets it, this test says so.
            supersetGroup = 7,
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

        assertThat(entity.toDto().toEntity()).isEqualTo(entity)
    }

    @Test
    fun aTemplateExercise_survivesTheCodec() {
        val entity = TemplateExerciseEntity(
            id = "te1",
            templateId = "t1",
            exerciseId = "back-squat",
            position = 2,
            // The column B16 added: a plan's grouping must survive a restore like any other.
            supersetGroup = 4,
            restSeconds = 90,
            techniqueNote = "pause at the bottom",
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_000_001L,
            deletedAt = null,
        )

        assertThat(entity.toDto().toEntity()).isEqualTo(entity)
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

        assertThat(entity.toDto().toEntity()).isEqualTo(entity)
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

        assertThat(entity.toDto().toEntity()).isEqualTo(entity)
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
            // The column P3.3 added: a session's provenance must survive a restore, or a
            // restored done day would read as missed.
            templateId = "t1",
        )

        val restored = entity.toDto().toEntity()

        // The one field the codec drops on purpose: a rest countdown is device-and-moment
        // state rather than training history, so a restored session must not come back
        // mid-rest. Everything else is asserted field for field.
        assertThat(restored).isEqualTo(entity.copy(restEndsAt = null))
        assertThat(restored.restEndsAt).isNull()
    }

    @Test
    fun aProgram_survivesTheCodec() {
        val entity = ProgramEntity(
            id = "p1",
            name = "Upper/Lower",
            isActive = true,
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_000_001L,
            deletedAt = null,
        )

        assertThat(entity.toDto().toEntity()).isEqualTo(entity)
    }

    @Test
    fun aProgramSlot_survivesTheCodec() {
        val entity = ProgramSlotEntity(
            id = "slot1",
            programId = "p1",
            templateId = "t1",
            position = 2,
            // A weekday is the column a hand-written codec loses first (N16's own history),
            // so it is set to something distinctive here.
            weekday = DayOfWeek.THURSDAY,
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_000_001L,
            deletedAt = null,
        )

        assertThat(entity.toDto().toEntity()).isEqualTo(entity)
    }

    @Test
    fun aProgramSkip_survivesTheCodec() {
        val entity = ProgramSkipEntity(
            id = "skip1",
            slotId = "slot1",
            weekStart = 20_345L,
            createdAt = 1_600_000_000_000L,
            updatedAt = 1_600_000_000_001L,
            deletedAt = null,
        )

        assertThat(entity.toDto().toEntity()).isEqualTo(entity)
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

        assertThat(entity.toDto().toEntity()).isEqualTo(entity)
    }
}
