package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Template writes, end to end through the repository (ROADMAP N3).
 *
 * The order assertions are the point of the feature — a template that comes back in
 * the wrong order produces the wrong workout — and the edge cases are asserted as
 * *successes*, because a Move up button at the top of the list should do nothing,
 * not report a failure the user cannot act on.
 */
@RunWith(AndroidJUnit4::class)
class TemplateRepositoryTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomTemplateRepository

    private val clock = TimeSource { Instant.parse("2026-09-29T08:00:00Z") }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomTemplateRepository(database, clock)
        runTest {
            database.exerciseDao().insertAll(listOf(exercise("back-squat"), exercise("bench-press")))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aCreatedTemplate_comesBackTrimmed_andWithNoExercises() = runTest {
        val created = repository.createTemplate("  Push day  ") as DataResult.Success

        val template = repository.observeTemplate(created.data).first()
        assertEquals("Push day", template?.name)
        assertEquals(0, template?.exerciseCount)
    }

    @Test
    fun aBlankName_isRefused_andStoresNothing() = runTest {
        val failure = repository.createTemplate("   ") as DataResult.Failure

        assertTrue(failure.error is DataError.Invalid)
        assertTrue("a refused create must not leave a row", repository.observeTemplates().first().isEmpty())
    }

    @Test
    fun renaming_isTrimmed_andRefusesABlankName() = runTest {
        val id = create("Push day")

        assertTrue(repository.renameTemplate(id, "  Legs  ") is DataResult.Success)
        assertEquals("Legs", repository.observeTemplate(id).first()?.name)

        assertTrue(repository.renameTemplate(id, "  ") is DataResult.Failure)
        assertEquals("a refused rename must leave the name alone", "Legs", repository.observeTemplate(id).first()?.name)
    }

    @Test
    fun renaming_aGoneTemplate_isNotFound() = runTest {
        val failure = repository.renameTemplate("no-such-template", "Legs") as DataResult.Failure

        assertEquals(DataError.NotFound, failure.error)
    }

    @Test
    fun deleting_hidesTheTemplate_andASecondDeleteIsNotFound() = runTest {
        val id = create("Push day")

        assertTrue(repository.deleteTemplate(id) is DataResult.Success)
        assertNull(repository.observeTemplate(id).first())
        assertTrue(repository.observeTemplates().first().isEmpty())

        assertEquals(
            DataError.NotFound,
            (repository.deleteTemplate(id) as DataResult.Failure).error,
        )
    }

    @Test
    fun exercises_areAppendedInTheOrderTheyAreAdded() = runTest {
        val id = create("Push day")

        repository.addExercise(id, "back-squat")
        repository.addExercise(id, "bench-press")

        assertEquals(
            listOf("back-squat", "bench-press"),
            repository.observeExercises(id).first().map { it.exerciseId },
        )
        assertEquals(listOf(0, 1), repository.observeExercises(id).first().map { it.position })
        assertEquals(2, repository.observeTemplate(id).first()?.exerciseCount)
    }

    @Test
    fun addingToAGoneTemplate_isNotFound() = runTest {
        val failure = repository.addExercise("no-such-template", "back-squat") as DataResult.Failure

        assertEquals(DataError.NotFound, failure.error)
    }

    @Test
    fun removingAnExercise_takesItOutOfTheOrder() = runTest {
        val id = create("Push day")
        repository.addExercise(id, "back-squat")
        repository.addExercise(id, "bench-press")
        val first = repository.observeExercises(id).first().first()

        assertTrue(repository.removeExercise(first.id) is DataResult.Success)

        assertEquals(
            listOf("bench-press"),
            repository.observeExercises(id).first().map { it.exerciseId },
        )
    }

    @Test
    fun removingAnUnknownExercise_isNotFound() = runTest {
        val failure = repository.removeExercise("no-such-row") as DataResult.Failure

        assertEquals(DataError.NotFound, failure.error)
    }

    @Test
    fun movingDown_swapsWithTheNextExercise() = runTest {
        val id = create("Push day")
        repository.addExercise(id, "back-squat")
        repository.addExercise(id, "bench-press")
        val first = repository.observeExercises(id).first().first()

        assertTrue(repository.moveExercise(first.id, delta = 1) is DataResult.Success)

        assertEquals(
            listOf("bench-press", "back-squat"),
            repository.observeExercises(id).first().map { it.exerciseId },
        )
    }

    @Test
    fun movingUp_swapsWithThePreviousExercise() = runTest {
        val id = create("Push day")
        repository.addExercise(id, "back-squat")
        repository.addExercise(id, "bench-press")
        val last = repository.observeExercises(id).first().last()

        assertTrue(repository.moveExercise(last.id, delta = -1) is DataResult.Success)

        assertEquals(
            listOf("bench-press", "back-squat"),
            repository.observeExercises(id).first().map { it.exerciseId },
        )
    }

    @Test
    fun movingPastEitherEnd_isANoOp_success() = runTest {
        val id = create("Push day")
        repository.addExercise(id, "back-squat")
        repository.addExercise(id, "bench-press")
        val rows = repository.observeExercises(id).first()

        assertTrue(repository.moveExercise(rows.first().id, delta = -1) is DataResult.Success)
        assertTrue(repository.moveExercise(rows.last().id, delta = 1) is DataResult.Success)

        assertEquals(
            "the order must be untouched at the edges",
            listOf("back-squat", "bench-press"),
            repository.observeExercises(id).first().map { it.exerciseId },
        )
    }

    @Test
    fun movingAnUnknownExercise_isNotFound() = runTest {
        val failure = repository.moveExercise("no-such-row", delta = 1) as DataResult.Failure

        assertEquals(DataError.NotFound, failure.error)
    }

    @Test
    fun templates_areListedByName() = runTest {
        create("Push day")
        create("Legs")
        create("Arms")

        assertEquals(
            listOf("Arms", "Legs", "Push day"),
            repository.observeTemplates().first().map { it.name },
        )
    }

    private suspend fun create(name: String): String =
        (repository.createTemplate(name) as DataResult.Success).data

    private fun exercise(id: String) = ExerciseEntity(
        id = id,
        name = id,
        primaryMuscle = MuscleGroup.QUADS,
        secondaryMuscles = emptyList(),
        equipment = Equipment.BARBELL,
        movementPattern = MovementPattern.SQUAT,
        isCustom = false,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )
}
