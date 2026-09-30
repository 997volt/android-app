package com.example.androidapp.data

import com.example.androidapp.domain.repository.TemplateSetEdit
import com.example.androidapp.domain.model.SetType
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

    @Test
    fun aPlannedSet_isAppended_andComesBackWithItsTargets() = runTest {
        val template = create("Legs")
        val exercise = plannedExercise(template)

        repository.addSet(
            exercise,
            TemplateSetEdit(
                role = SetType.TOP_SET,
                targetWeightGrams = 140_000L,
                targetRepsMin = 1,
                targetRepsMax = 2,
                targetRpeHalves = 18,
                note = "grind",
            ),
        )

        val stored = repository.observeExercises(template).first().single().sets.single()
        assertEquals(0, stored.setIndex)
        assertEquals(SetType.TOP_SET, stored.role)
        assertEquals(140_000L, stored.targetWeightGrams)
        assertEquals(1, stored.targetRepsMin)
        assertEquals(2, stored.targetRepsMax)
        assertEquals(18, stored.targetRpeHalves)
        assertEquals("grind", stored.note)
    }

    @Test
    fun aPlanWithNoTargets_isValid_ratherThanZeroed() = runTest {
        // "Work up to a heavy single" has no weight to write down (ROADMAP N14).
        val template = create("Legs")
        val exercise = plannedExercise(template)

        val result = repository.addSet(exercise, TemplateSetEdit(role = SetType.WARMUP))

        assertTrue(result is DataResult.Success)
        val stored = repository.observeExercises(template).first().single().sets.single()
        assertNull(stored.targetWeightGrams)
        assertNull(stored.targetRepsMin)
        assertNull(stored.targetRpeHalves)
    }

    @Test
    fun aPlanThatDoesNotMakeSense_isRefused() = runTest {
        val template = create("Legs")
        val exercise = plannedExercise(template)

        val negative = repository.addSet(exercise, TemplateSetEdit(targetWeightGrams = -1))
        val zeroReps = repository.addSet(exercise, TemplateSetEdit(targetRepsMin = 0))
        val backwards = repository.addSet(exercise, TemplateSetEdit(targetRepsMin = 8, targetRepsMax = 3))
        val offScaleRpe = repository.addSet(exercise, TemplateSetEdit(targetRpeHalves = 21))

        assertTrue("a negative weight", negative is DataResult.Failure)
        assertTrue("a zero-rep target", zeroReps is DataResult.Failure)
        assertTrue("a range that runs backwards", backwards is DataResult.Failure)
        assertTrue("an RPE off the scale", offScaleRpe is DataResult.Failure)
        assertTrue(
            "nothing is stored when the plan is refused",
            repository.observeExercises(template).first().single().sets.isEmpty(),
        )
    }

    @Test
    fun copyForward_duplicatesTheSets_soTheyCanBeAdjusted() = runTest {
        // The clause that makes a plan usable on a phone: one set authored, then
        // doubled twice, rather than thirty forms (ROADMAP N14).
        val template = create("Legs")
        val exercise = plannedExercise(template)
        repository.addSet(exercise, TemplateSetEdit(targetWeightGrams = 100_000L, targetRepsMax = 3))

        repository.duplicateSets(exercise)
        repository.duplicateSets(exercise)

        val sets = repository.observeExercises(template).first().single().sets
        assertEquals(4, sets.size)
        assertEquals("appended in order", listOf(0, 1, 2, 3), sets.map { it.setIndex })
        assertTrue("the copies carry the targets", sets.all { it.targetWeightGrams == 100_000L })
        assertEquals("copies are their own rows", 4, sets.map { it.id }.toSet().size)
    }

    @Test
    fun updatingAPlannedSet_keepsItsPlace_andCanClearATarget() = runTest {
        val template = create("Legs")
        val exercise = plannedExercise(template)
        repository.addSet(exercise, TemplateSetEdit(targetRepsMax = 5))
        repository.addSet(exercise, TemplateSetEdit(targetRepsMax = 5))
        val first = repository.observeExercises(template).first().single().sets.first()

        repository.updateSet(first.id, TemplateSetEdit(role = SetType.DROP, targetRepsMax = 8))

        val sets = repository.observeExercises(template).first().single().sets
        assertEquals(2, sets.size)
        assertEquals("the order is untouched", listOf(0, 1), sets.map { it.setIndex })
        assertEquals(SetType.DROP, sets[0].role)
        assertEquals(8, sets[0].targetRepsMax)
        assertNull("a cleared target is null, not zero", sets[0].targetWeightGrams)
    }

    @Test
    fun removingAPlannedSet_leavesTheOthersAlone() = runTest {
        val template = create("Legs")
        val exercise = plannedExercise(template)
        repository.addSet(exercise, TemplateSetEdit(targetRepsMax = 3))
        repository.addSet(exercise, TemplateSetEdit(targetRepsMax = 5))
        val first = repository.observeExercises(template).first().single().sets.first()

        repository.removeSet(first.id)

        val sets = repository.observeExercises(template).first().single().sets
        assertEquals(1, sets.size)
        assertEquals(5, sets.single().targetRepsMax)
    }

    @Test
    fun aPlanForAnExerciseThatIsGone_isNotFound() = runTest {
        assertTrue(repository.addSet("nope", TemplateSetEdit()) is DataResult.Failure)
        assertTrue(repository.removeSet("nope") is DataResult.Failure)
        assertTrue(repository.updateSet("nope", TemplateSetEdit()) is DataResult.Failure)
    }

    @Test
    fun aPlansRestAndCue_reachTheExercise_andBlankBecomesUnset() = runTest {
        val template = create("Legs")
        val exercise = plannedExercise(template)

        repository.setExercisePlan(exercise, restSeconds = 180, techniqueNote = "  Slow descent  ")
        val written = repository.observeExercises(template).first().single()
        assertEquals(180, written.restSeconds)
        assertEquals("Slow descent", written.techniqueNote)

        // Blank is "use the library's" (N5), and a rest of zero is not a rest.
        repository.setExercisePlan(exercise, restSeconds = null, techniqueNote = "   ")
        val cleared = repository.observeExercises(template).first().single()
        assertNull(cleared.restSeconds)
        assertNull(cleared.techniqueNote)
        assertTrue(
            repository.setExercisePlan(exercise, restSeconds = 0, techniqueNote = null) is
                DataResult.Failure,
        )
    }

    /** A template with one exercise added, for the plan tests to write sets against. */
    private suspend fun plannedExercise(templateId: String): String {
        repository.addExercise(templateId, "back-squat")
        return repository.observeExercises(templateId).first().single().id
    }

}
