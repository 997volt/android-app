package com.example.androidapp.data.local

import androidx.room.Room
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the template DAO (ROADMAP N3).
 *
 * Two things here are load-bearing rather than incidental: the exercise count comes
 * from SQL, because the list renders it without loading the join table, and a
 * reorder is a transaction, because a half-applied swap would leave two exercises
 * fighting over one position — and the order is the whole point of a template.
 */
@RunWith(AndroidJUnit4::class)
class TemplateDaoTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var dao: TemplateDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        dao = database.templateDao()
        runTest { database.exerciseDao().insertAll(listOf(exercise("back-squat"), exercise("bench-press"))) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun templates_areListedByName_withTheirExerciseCounts() = runTest {
        dao.insertTemplate(template("t2", "Push day"))
        dao.insertTemplate(template("t1", "Legs"))
        dao.insertTemplateExercise(templateExercise("te1", "t2", "bench-press", 0))
        dao.insertTemplateExercise(templateExercise("te2", "t2", "back-squat", 1))

        val rows = dao.observeTemplates().first()

        assertEquals(listOf("Legs", "Push day"), rows.map { it.name })
        assertEquals(listOf(0, 2), rows.map { it.exerciseCount })
    }

    @Test
    fun aDeletedTemplate_leavesTheList_withoutTakingItsRowsWithIt() = runTest {
        dao.insertTemplate(template("t1", "Legs"))
        dao.insertTemplateExercise(templateExercise("te1", "t1", "back-squat", 0))

        assertEquals(1, dao.softDeleteTemplate("t1", at = 5_000L))

        assertTrue("a soft delete must hide the template", dao.observeTemplates().first().isEmpty())
        assertNull(dao.findById("t1"))
        // The row is still there for an export to carry (P1.12): a template the
        // user deleted is still data they own.
        assertNotNull(dao.findTemplateExercise("te1"))
    }

    @Test
    fun renaming_updatesOnlyALiveTemplate() = runTest {
        dao.insertTemplate(template("t1", "Legs"))

        assertEquals(1, dao.rename("t1", "Leg day", at = 2_000L))
        assertEquals("Leg day", dao.findById("t1")?.name)

        dao.softDeleteTemplate("t1", at = 3_000L)
        assertEquals("a deleted template must not be renameable", 0, dao.rename("t1", "Nope", at = 4_000L))
    }

    @Test
    fun exercises_comeBackInStoredOrder_withTheirLibraryDetails() = runTest {
        dao.insertTemplate(template("t1", "Legs"))
        // Inserted out of order on purpose: the order is `position`, not insertion.
        dao.insertTemplateExercise(templateExercise("te2", "t1", "bench-press", 1))
        dao.insertTemplateExercise(templateExercise("te1", "t1", "back-squat", 0))

        val rows = dao.observeTemplateExercises("t1").first()

        assertEquals(listOf("te1", "te2"), rows.map { it.id })
        assertEquals(listOf("Back Squat", "Bench Press"), rows.map { it.exerciseName })
        assertEquals(listOf("back-squat", "bench-press"), dao.findExerciseIdsInOrder("t1"))
    }

    @Test
    fun aSoftDeletedExercise_leavesTheTemplateList() = runTest {
        dao.insertTemplate(template("t1", "Legs"))
        dao.insertTemplateExercise(templateExercise("te1", "t1", "back-squat", 0))
        dao.insertTemplateExercise(templateExercise("te2", "t1", "bench-press", 1))

        assertEquals(1, dao.softDeleteTemplateExercise("te1", at = 5_000L))

        assertEquals(listOf("te2"), dao.observeTemplateExercises("t1").first().map { it.id })
        assertEquals(listOf("bench-press"), dao.findExerciseIdsInOrder("t1"))
    }

    @Test
    fun maxPosition_countsEmptyAsMinusOne_soCallersAppendFromZero() = runTest {
        dao.insertTemplate(template("t1", "Legs"))
        assertEquals(-1, dao.maxPosition("t1"))

        dao.insertTemplateExercise(templateExercise("te1", "t1", "back-squat", 0))
        dao.insertTemplateExercise(templateExercise("te2", "t1", "bench-press", 1))

        assertEquals(1, dao.maxPosition("t1"))
    }

    @Test
    fun swapPositions_exchangesBothRows_inOneTransaction() = runTest {
        dao.insertTemplate(template("t1", "Legs"))
        dao.insertTemplateExercise(templateExercise("te1", "t1", "back-squat", 0))
        dao.insertTemplateExercise(templateExercise("te2", "t1", "bench-press", 1))

        dao.swapPositions(
            firstId = "te1",
            firstPosition = 1,
            secondId = "te2",
            secondPosition = 0,
            at = 9_000L,
        )

        assertEquals(listOf("te2", "te1"), dao.observeTemplateExercises("t1").first().map { it.id })
    }

    @Test
    fun aTemplateExercise_isFoundById_soAMoveCanReadItsOwner() = runTest {
        dao.insertTemplate(template("t1", "Legs"))
        dao.insertTemplateExercise(templateExercise("te1", "t1", "back-squat", 0))

        assertEquals("t1", dao.findTemplateExercise("te1")?.templateId)
        assertNull(dao.findTemplateExercise("nope"))
    }

    private fun template(id: String, name: String) = TemplateEntity(
        id = id,
        name = name,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = null,
    )

    private fun templateExercise(
        id: String,
        templateId: String,
        exerciseId: String,
        position: Int,
    ) = TemplateExerciseEntity(
        id = id,
        templateId = templateId,
        exerciseId = exerciseId,
        position = position,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = null,
    )

    private fun exercise(id: String) = ExerciseEntity(
        id = id,
        name = id.split("-").joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } },
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
