package com.example.androidapp.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the exercise DAO. Room needs a real SQLite
 * implementation, so these cannot run on the JVM.
 *
 *   ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ExerciseDaoTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        dao = database.exerciseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeAll_returnsRowsSortedByName() = runTest {
        dao.insertAll(listOf(row("z", "Zercher Squat"), row("a", "Arnold Press")))

        assertEquals(
            listOf("Arnold Press", "Zercher Squat"),
            dao.observeAll().first().map { it.name },
        )
    }

    @Test
    fun rowSurvivesARoundTripUnchanged() = runTest {
        val original = row("squat", "Back Squat").copy(
            primaryMuscle = MuscleGroup.QUADS,
            secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.CORE),
            equipment = Equipment.BARBELL,
            movementPattern = MovementPattern.SQUAT,
        )

        dao.insertAll(listOf(original))

        assertEquals(original, dao.findById("squat"))
    }

    @Test
    fun softDeletedRow_disappearsFromBothReads() = runTest {
        dao.insertAll(listOf(row("gone", "Deleted Lift")))

        dao.softDelete("gone", deletedAt = 1_000L)

        assertEquals(emptyList<ExerciseEntity>(), dao.observeAll().first())
        assertNull(dao.findById("gone"))
    }

    @Test
    fun countStillSeesSoftDeletedRows() = runTest {
        // Seeding uses this to decide whether the database has been populated, so
        // it must not be fooled by a user having deleted library entries.
        dao.insertAll(listOf(row("a", "A"), row("b", "B")))

        dao.softDelete("a", deletedAt = 1L)

        assertEquals(2, dao.count())
    }

    @Test
    fun insertAll_replacesAnExistingRow() = runTest {
        dao.insertAll(listOf(row("squat", "Back Squat")))

        dao.insertAll(listOf(row("squat", "Back Squat (renamed)")))

        assertEquals("Back Squat (renamed)", dao.findById("squat")?.name)
        assertEquals(1, dao.count())
    }

    @Test
    fun update_writesEveryColumn_andReturnsOne() = runTest {
        val original = row("custom", "Sled Push").copy(
            isCustom = true,
            createdAt = 10L,
            updatedAt = 10L,
        )
        dao.insert(original)

        val updated = dao.update(
            original.copy(
                name = "Sled Push Heavy",
                primaryMuscle = MuscleGroup.QUADS,
                secondaryMuscles = listOf(MuscleGroup.GLUTES),
                equipment = Equipment.MACHINE,
                movementPattern = MovementPattern.SQUAT,
                updatedAt = 99L,
            ),
        )

        assertEquals(1, updated)
        val stored = dao.findById("custom")!!
        assertEquals("Sled Push Heavy", stored.name)
        assertEquals(MuscleGroup.QUADS, stored.primaryMuscle)
        assertEquals(listOf(MuscleGroup.GLUTES), stored.secondaryMuscles)
        assertEquals(Equipment.MACHINE, stored.equipment)
        assertEquals(MovementPattern.SQUAT, stored.movementPattern)
        assertEquals(99L, stored.updatedAt)
        // An attribute edit is not a re-creation, and it does not change where the
        // row came from.
        assertEquals(10L, stored.createdAt)
        assertEquals(true, stored.isCustom)
    }

    @Test
    fun update_missingRow_returnsZero() = runTest {
        assertEquals(0, dao.update(row("nope", "Nope")))
    }

    private fun row(id: String, name: String) = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscle = MuscleGroup.CHEST,
        secondaryMuscles = emptyList(),
        equipment = Equipment.BARBELL,
        movementPattern = MovementPattern.HORIZONTAL_PUSH,
        isCustom = false,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )
}
