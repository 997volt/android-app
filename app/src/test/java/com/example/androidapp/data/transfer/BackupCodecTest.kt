package com.example.androidapp.data.transfer

import org.junit.Assert.assertNull
import com.example.androidapp.domain.InvalidInputException
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.SetType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the backup file format (ROADMAP P1.12).
 *
 * The format's whole job is a faithful round trip, so that is what is asserted —
 * including the fields that are easy to forget, like soft-delete timestamps and
 * the secondary-muscle list.
 */
class BackupCodecTest {

    private val sample = BackupFile(
        schemaVersion = BackupCodec.CURRENT_SCHEMA_VERSION,
        exportedAt = 1_790_000_000_000L,
        exercises = listOf(
            ExerciseDto(
                id = "back-squat",
                name = "Back Squat",
                primaryMuscle = MuscleGroup.QUADS,
                secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.CORE),
                equipment = Equipment.BARBELL,
                movementPattern = MovementPattern.SQUAT,
                isCustom = false,
                createdAt = 1L,
                updatedAt = 2L,
                deletedAt = null,
                restSeconds = 180,
                techniqueNote = "Brace, sit back",
            ),
            // A soft-deleted row: the backup must carry it, not quietly drop it.
            ExerciseDto(
                id = "gone",
                name = "Removed Lift",
                primaryMuscle = MuscleGroup.BACK,
                secondaryMuscles = emptyList(),
                equipment = Equipment.CABLE,
                movementPattern = MovementPattern.HORIZONTAL_PULL,
                isCustom = true,
                createdAt = 3L,
                updatedAt = 4L,
                deletedAt = 5L,
            ),
        ),
        sessions = listOf(
            SessionDto(
                id = "s1",
                startedAt = 10L,
                finishedAt = 20L,
                notes = "felt heavy",
                restEndsAt = null,
                readinessNote = "Slept badly, legs heavy",
                createdAt = 10L,
                updatedAt = 20L,
                deletedAt = null,
            ),
        ),
        sessionExercises = listOf(
            SessionExerciseDto(
                id = "se1",
                sessionId = "s1",
                exerciseId = "back-squat",
                position = 0,
                finishedAt = 12L,
                muscleFeel = 8,
                jointPain = 2,
                jointPainNote = "left shoulder",
                createdAt = 10L,
                updatedAt = 12L,
                deletedAt = null,
            ),
        ),
        sets = listOf(
            SetDto(
                id = "set1",
                sessionExerciseId = "se1",
                setIndex = 0,
                reps = 5,
                weightGrams = 100_000,
                setType = SetType.WARMUP,
                rpe = 8,
                note = "Felt heavy",
                completedAt = 11L,
                createdAt = 11L,
                updatedAt = 11L,
                deletedAt = null,
            ),
        ),
        // A template is the user's plan, so the escape hatch has to carry it (N3),
        // soft-deleted rows included.
        templates = listOf(
            TemplateDto(
                id = "t1",
                name = "Push day",
                createdAt = 30L,
                updatedAt = 31L,
                deletedAt = null,
            ),
            TemplateDto(
                id = "t2",
                name = "Deleted plan",
                createdAt = 32L,
                updatedAt = 33L,
                deletedAt = 34L,
            ),
        ),
        templateExercises = listOf(
            TemplateExerciseDto(
                id = "te1",
                templateId = "t1",
                exerciseId = "back-squat",
                position = 0,
                restSeconds = 180,
                techniqueNote = "Slow descent, no sinking",
                createdAt = 30L,
                updatedAt = 30L,
                deletedAt = null,
            ),
        ),
        // A plan's sets are the plan (ROADMAP N14). This sample is what proves the
        // codec carries them: a field the DTO does not name is dropped in silence.
        templateSets = listOf(
            TemplateSetDto(
                id = "ts1",
                templateExerciseId = "te1",
                setIndex = 0,
                role = SetType.TOP_SET,
                targetWeightGrams = 140_000L,
                targetRepsMin = 1,
                targetRepsMax = 2,
                targetRpe = 9,
                note = "grind",
                createdAt = 30L,
                updatedAt = 30L,
                deletedAt = null,
            ),
        ),
    )

    @Test
    fun encodeThenDecode_returnsExactlyTheSameData() {
        val restored = BackupCodec.decode(BackupCodec.encode(sample))

        assertEquals(sample, restored)
    }

    @Test
    fun enumsAreWrittenByName_notByOrdinal() {
        // An ordinal would be re-interpreted silently if the enum were ever
        // reordered, and the file may outlive several app versions.
        val text = BackupCodec.encode(sample)

        assertTrue("expected the enum name in the file", text.contains("\"QUADS\""))
        assertTrue(text.contains("\"WARMUP\""))
    }

    @Test
    fun aSoftDeletedRowSurvivesTheRoundTrip() {
        // Dropping it would restore into a database that differs from the original.
        val restored = BackupCodec.decode(BackupCodec.encode(sample))

        assertEquals(1, restored.exercises.count { it.deletedAt != null })
    }

    @Test
    fun garbageIsRejectedWithAMessageWorthShowing() {
        val thrown = assertThrows(InvalidInputException::class.java) {
            BackupCodec.decode("this is not json")
        }

        assertTrue(
            "the message is user-facing, not a stack trace",
            thrown.message.orEmpty().contains("backup file"),
        )
    }

    @Test
    fun aFileFromANewerAppIsRefused_ratherThanPartiallyRead() {
        val fromTheFuture = BackupCodec.encode(
            sample.copy(schemaVersion = BackupCodec.CURRENT_SCHEMA_VERSION + 1),
        )

        val thrown = assertThrows(InvalidInputException::class.java) {
            BackupCodec.decode(fromTheFuture)
        }

        assertTrue(
            "the message should say why, not just fail",
            thrown.message.orEmpty().contains("newer version"),
        )
    }

    @Test
    fun unknownFieldsAreTolerated() {
        // Within the same schema version a newer build may add fields; refusing
        // them would make the format brittle for no gain.
        val text = BackupCodec.encode(sample).replaceFirst("{", "{\n  \"somethingNew\": 42,")

        assertEquals(sample, BackupCodec.decode(text))
    }

    @Test
    fun aFileWrittenBeforeTemplatesExisted_stillDecodes_seeingNone() {
        // N3 added whole collections rather than fields. They are defaulted to
        // empty, which is what lets a file from before templates restore cleanly
        // instead of failing to decode.
        val json = Json { prettyPrint = false }
        val tree = json.parseToJsonElement(BackupCodec.encode(sample)).jsonObject
        val olderFile = JsonObject(tree - "templates" - "templateExercises" - "templateSets")

        val restored = BackupCodec.decode(olderFile.toString())

        assertEquals(emptyList<TemplateDto>(), restored.templates)
        assertEquals(emptyList<TemplateExerciseDto>(), restored.templateExercises)
        assertEquals(emptyList<TemplateSetDto>(), restored.templateSets)
        assertEquals("everything else still decodes", "Push day", sample.templates.first().name)
    }

    @Test
    fun aFileWrittenBeforeTheN4ToN7FieldsExisted_stillDecodes() {
        // N4 through N7 each added fields and deliberately did *not* bump the schema
        // version (the version moves when a field changes meaning or is removed).
        // That is only safe because every added field is defaulted, so a file
        // written before they existed must still decode — reading them unset.
        val json = Json { prettyPrint = false }
        val tree = json.parseToJsonElement(BackupCodec.encode(sample)).jsonObject
        val olderExercises = tree.getValue("exercises").jsonArray.map { element ->
            JsonObject(
                element.jsonObject.filterKeys { it != "restSeconds" && it != "techniqueNote" },
            )
        }
        val olderSessions = tree.getValue("sessions").jsonArray.map { element ->
            JsonObject(element.jsonObject.filterKeys { it != "readinessNote" })
        }
        val olderSessionExercises = tree.getValue("sessionExercises").jsonArray.map { element ->
            JsonObject(
                element.jsonObject.filterKeys {
                    it != "finishedAt" && it != "muscleFeel" &&
                        it != "jointPain" && it != "jointPainNote"
                },
            )
        }
        val olderSets = tree.getValue("sets").jsonArray.map { element ->
            JsonObject(element.jsonObject.filterKeys { it != "rpe" && it != "note" })
        }
        val olderFile = JsonObject(
            tree + mapOf(
                "exercises" to JsonArray(olderExercises),
                "sessions" to JsonArray(olderSessions),
                "sessionExercises" to JsonArray(olderSessionExercises),
                "sets" to JsonArray(olderSets),
            ),
        )

        val restored = BackupCodec.decode(olderFile.toString())

        assertEquals(null, restored.exercises.first().restSeconds)
        assertEquals(null, restored.exercises.first().techniqueNote)
        assertEquals(null, restored.sessions.first().readinessNote)
        assertEquals(null, restored.sessionExercises.first().finishedAt)
        assertEquals(null, restored.sessionExercises.first().muscleFeel)
        assertEquals(null, restored.sessionExercises.first().jointPain)
        assertEquals(null, restored.sessionExercises.first().jointPainNote)
        assertEquals(null, restored.sets.first().rpe)
        assertEquals(null, restored.sets.first().note)
    }

    @Test
    fun aFileWrittenBeforePlansExisted_stillDecodes_withThePlanFieldsUnset() {
        // N14 added a collection *and* two fields on template exercises. A file from
        // before it must decode, with the plan absent and the rest and cue unset —
        // which is "use the library's", not zero (ROADMAP N14, N5).
        val json = Json { prettyPrint = false }
        val tree = json.parseToJsonElement(BackupCodec.encode(sample)).jsonObject
        val exercises = tree["templateExercises"]!!.jsonArray.map { element ->
            JsonObject(
                element.jsonObject -
                    "restSeconds" -
                    "techniqueNote",
            )
        }
        val olderFile = JsonObject(
            tree - "templateSets" + ("templateExercises" to JsonArray(exercises)),
        )

        val restored = BackupCodec.decode(olderFile.toString())

        assertEquals(emptyList<TemplateSetDto>(), restored.templateSets)
        val exercise = restored.templateExercises.single()
        assertNull(exercise.restSeconds)
        assertNull(exercise.techniqueNote)
        assertEquals("the rest of the exercise still decodes", "back-squat", exercise.exerciseId)
    }

    @Test
    fun aPlanWithNoTargets_survivesTheRoundTrip_asNulls() {
        // Nullable targets are the point: an absent weight is not a zero (N14).
        val bare = sample.copy(
            templateSets = listOf(
                TemplateSetDto(
                    id = "ts1",
                    templateExerciseId = "te1",
                    setIndex = 0,
                    role = SetType.WARMUP,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )

        val restored = BackupCodec.decode(BackupCodec.encode(bare))

        val set = restored.templateSets.single()
        assertEquals(SetType.WARMUP, set.role)
        assertNull(set.targetWeightGrams)
        assertNull(set.targetRepsMin)
        assertNull(set.targetRepsMax)
        assertNull(set.targetRpe)
    }
}
