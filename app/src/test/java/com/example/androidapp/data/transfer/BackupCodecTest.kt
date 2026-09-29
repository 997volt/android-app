package com.example.androidapp.data.transfer

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
                createdAt = 10L,
                updatedAt = 10L,
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
                completedAt = 11L,
                createdAt = 11L,
                updatedAt = 11L,
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
    fun aFileWrittenBeforeTheRestCueAndReadinessFieldsExisted_stillDecodes() {
        // N4 and N5 added fields to two DTOs and deliberately did *not* bump the
        // schema version (the version moves when a field changes meaning or is
        // removed). That is only safe because every added field is defaulted, so a
        // file written before they existed must still decode — reading them unset.
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
        val olderFile = JsonObject(
            tree + mapOf(
                "exercises" to JsonArray(olderExercises),
                "sessions" to JsonArray(olderSessions),
            ),
        )

        val restored = BackupCodec.decode(olderFile.toString())

        assertEquals(null, restored.exercises.first().restSeconds)
        assertEquals(null, restored.exercises.first().techniqueNote)
        assertEquals(null, restored.sessions.first().readinessNote)
    }
}
