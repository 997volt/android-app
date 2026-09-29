package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.Exercise
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Custom exercises, end to end through the repository (ROADMAP N2).
 *
 * The decisions this pins down are the ones a later session is most likely to
 * second-guess: creation asks for the *name only*, so the taxonomy is stored as
 * unspecified rather than invented; and editing fills that taxonomy in without
 * disturbing the row's identity.
 */
@RunWith(AndroidJUnit4::class)
class RoomExerciseRepositoryTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomExerciseRepository

    private var now: Instant = Instant.parse("2026-09-29T08:00:00Z")
    private val clock = TimeSource { now }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomExerciseRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createCustomExercise_storesTheNameAndLeavesTheTaxonomyUnspecified() = runTest {
        val created = created("  Sled Push  ")

        // A UUID, not a seeded slug: user-created rows must not collide with the
        // seed library's human-readable ids.
        UUID.fromString(created.id)
        assertEquals("Sled Push", created.name)
        assertTrue(created.isCustom)
        assertEquals(MuscleGroup.OTHER, created.primaryMuscle)
        assertEquals(Equipment.OTHER, created.equipment)
        assertEquals(MovementPattern.OTHER, created.movementPattern)
        // N5: rest and cue start unset, i.e. "use the app default" and "no cue".
        assertNull(created.restSeconds)
        assertNull(created.techniqueNote)

        // The reads are values now (ROADMAP B4), so a test unwraps them like one.
        val library = (repository.observeExercises().first() as DataResult.Success).data
        assertEquals(listOf("Sled Push"), library.map { it.name })
    }

    @Test
    fun createCustomExercise_refusesABlankName_withoutStoringAnything() = runTest {
        val failure = repository.createCustomExercise("   ") as DataResult.Failure

        assertTrue(failure.error is DataError.Invalid)
        val library = (repository.observeExercises().first() as DataResult.Success).data
        assertTrue("a refused create must store nothing", library.isEmpty())
    }

    @Test
    fun updateExercise_fillsInTheTaxonomy_keepingTheRowsIdentity() = runTest {
        val created = created("Sled Push")
        now = now.plusSeconds(60)

        val edited = created.copy(
            name = "Sled Push Heavy",
            primaryMuscle = MuscleGroup.QUADS,
            secondaryMuscles = listOf(MuscleGroup.GLUTES),
            equipment = Equipment.MACHINE,
            movementPattern = MovementPattern.SQUAT,
            restSeconds = 180,
            techniqueNote = "Drive the floor away",
        )
        assertTrue(repository.updateExercise(edited) is DataResult.Success)

        val stored = (repository.getExercise(created.id) as DataResult.Success).data!!
        assertEquals("Sled Push Heavy", stored.name)
        assertEquals(MuscleGroup.QUADS, stored.primaryMuscle)
        assertEquals(listOf(MuscleGroup.GLUTES), stored.secondaryMuscles)
        assertEquals(Equipment.MACHINE, stored.equipment)
        assertEquals(MovementPattern.SQUAT, stored.movementPattern)
        assertEquals(180, stored.restSeconds)
        assertEquals("Drive the floor away", stored.techniqueNote)
        // Identity and origin are untouched by an attribute edit.
        assertEquals(created.id, stored.id)
        assertTrue(stored.isCustom)

        val row = database.exerciseDao().findById(created.id)!!
        assertNotEquals("an edit must bump updatedAt", row.createdAt, row.updatedAt)
    }

    /** Reads are values now (ROADMAP B4); a test unwraps them in one place. */
    private suspend fun stored(id: String): Exercise =
        (repository.getExercise(id) as DataResult.Success).data
            ?: error("exercise $id should exist")

    @Test
    fun updateExercise_refusesANonPositiveRest() = runTest {
        val created = created("Sled Push")

        val failure = repository.updateExercise(created.copy(restSeconds = 0)) as DataResult.Failure

        assertTrue(failure.error is DataError.Invalid)
        // Nothing was written, so the exercise still reads as "use the default".
        assertNull(stored(created.id).restSeconds)
    }

    @Test
    fun updateExercise_storesAClearedCueAsNull_ratherThanAnEmptyString() = runTest {
        val created = created("Sled Push")
        repository.updateExercise(created.copy(techniqueNote = "Brace"))
        assertEquals("Brace", stored(created.id).techniqueNote)

        repository.updateExercise(created.copy(techniqueNote = "   "))

        // Two representations of "nothing" would render differently on screen.
        assertNull(stored(created.id).techniqueNote)
    }

    @Test
    fun updateExercise_missingRow_isNotFound() = runTest {
        val created = created("Sled Push")

        val failure = repository.updateExercise(created.copy(id = "does-not-exist")) as DataResult.Failure

        assertEquals(DataError.NotFound, failure.error)
    }

    @Test
    fun updateExercise_aSoftDeletedExercise_isNotFound_ratherThanResurrected() = runTest {
        val created = created("Sled Push")
        database.exerciseDao().softDelete(created.id, deletedAt = 1L)

        val failure = repository.updateExercise(created.copy(name = "Renamed")) as DataResult.Failure

        assertEquals(DataError.NotFound, failure.error)
        assertNull(database.exerciseDao().findById(created.id))
    }

    private suspend fun created(name: String): Exercise =
        (repository.createCustomExercise(name) as DataResult.Success<Exercise>).data
}
