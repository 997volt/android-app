package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.WorkoutDatabase
import com.example.androidapp.data.local.WorkoutSessionEntity
import com.example.androidapp.domain.DataError
import com.example.androidapp.domain.DataResult
import com.example.androidapp.domain.TimeSource
import com.example.androidapp.domain.getOrNull
import com.example.androidapp.domain.model.Equipment
import com.example.androidapp.domain.model.MovementPattern
import com.example.androidapp.domain.model.MuscleGroup
import com.example.androidapp.domain.model.ProgramSlot
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
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
 * Program writes and the missed-day read, end to end through the schema (ROADMAP P3.3).
 *
 * The occurrence arithmetic itself is a pure JVM test; what needs a device is the part that
 * only exists once rows do — the program's order, "one active program only" as one statement,
 * and an occurrence settled by the session that was actually started from its template.
 */
@RunWith(AndroidJUnit4::class)
class ProgramRepositoryTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomProgramRepository

    private val clock = TimeSource { Instant.parse("2026-10-07T12:00:00Z") }

    // A Wednesday, so Tuesday's slot is behind it and Friday's is ahead.
    private val today = LocalDate.of(2026, 10, 7)
    private val monday = LocalDate.of(2026, 10, 5)
    private val utc = ZoneOffset.UTC

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WorkoutDatabase::class.java,
        ).build()
        repository = RoomProgramRepository(database, clock)
        runTest {
            database.exerciseDao().insertAll(listOf(exercise("back-squat")))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aCreatedProgram_comesBackTrimmed_andNotActive() = runTest {
        val created = repository.createProgram("  Upper/Lower  ") as DataResult.Success

        val program = repository.observeProgram(created.data).first()
        assertEquals("Upper/Lower", program?.name)
        assertEquals(false, program?.isActive)
        assertNull(repository.observeActiveProgram().first())
    }

    @Test
    fun aBlankName_isRefused_andStoresNothing() = runTest {
        val failure = repository.createProgram("   ") as DataResult.Failure

        assertTrue(failure.error is DataError.Invalid)
        assertTrue(repository.observePrograms().first().isEmpty())
    }

    @Test
    fun slots_keepTheOrderTheyWereAdded_andMovingSwapsTwo() = runTest {
        val program = create("Upper/Lower")
        val first = createTemplate("Heavy lower")
        val second = createTemplate("Push")
        repository.addSlot(program, first, DayOfWeek.MONDAY)
        repository.addSlot(program, second, DayOfWeek.WEDNESDAY)

        assertEquals(
            listOf("Heavy lower", "Push"),
            repository.observeSlots(program).first().map { it.templateName },
        )

        val pushSlot = repository.observeSlots(program).first().first { it.templateName == "Push" }
        repository.moveSlot(pushSlot.id, delta = -1)

        assertEquals(
            listOf("Push", "Heavy lower"),
            repository.observeSlots(program).first().map { it.templateName },
        )
    }

    @Test
    fun aSlot_carriesItsWeekday_andUnpinningItLeavesItOrderOnly() = runTest {
        val program = create("Upper/Lower")
        repository.addSlot(program, createTemplate("Heavy lower"), DayOfWeek.MONDAY)
        val slot = slot(program)

        assertEquals(DayOfWeek.MONDAY, slot.weekday)

        repository.setSlotWeekday(slot.id, null)
        assertNull(repository.observeSlots(program).first().single().weekday)
    }

    @Test
    fun onlyOneProgram_isActive_atATime() = runTest {
        val first = create("Upper/Lower")
        val second = create("PPL")

        repository.setActiveProgram(first)
        assertEquals(first, repository.observeActiveProgram().first()?.id)

        repository.setActiveProgram(second)
        assertEquals(second, repository.observeActiveProgram().first()?.id)
        assertEquals(1, repository.observePrograms().first().count { it.isActive })
    }

    @Test
    fun clearingTheActiveProgram_leavesThePinsToAnswer() = runTest {
        val program = create("Upper/Lower")
        repository.setActiveProgram(program)

        repository.clearActiveProgram()

        assertNull(repository.observeActiveProgram().first())
    }

    @Test
    fun deletingTheActiveProgram_leavesNoneActive() = runTest {
        val program = create("Upper/Lower")
        repository.setActiveProgram(program)

        repository.deleteProgram(program)

        assertTrue(repository.observePrograms().first().isEmpty())
        assertNull(repository.observeActiveProgram().first())
    }

    @Test
    fun deletingATemplate_dropsItsSlotFromTheList() = runTest {
        val program = create("Upper/Lower")
        val template = createTemplate("Push")
        repository.addSlot(program, template, DayOfWeek.FRIDAY)

        database.templateDao().softDeleteTemplate(template, clock.now().toEpochMilli())

        assertTrue(repository.observeSlots(program).first().isEmpty())
    }

    @Test
    fun aMissedDay_isPending_untilASessionStartedFromThatTemplateSettlesIt() = runTest {
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.TUESDAY)
        repository.setActiveProgram(program)
        val slotId = slot(program).id

        assertEquals(
            listOf(slotId),
            repository.pendingOccurrences(today, utc).getOrNull()?.map { it.slotId },
        )

        // Started from the template, and done *late* — on Wednesday, for Tuesday's slot.
        database.workoutDao().insertSession(
            WorkoutSessionEntity(
                id = "s1",
                startedAt = Instant.parse("2026-10-07T09:00:00Z").toEpochMilli(),
                finishedAt = null,
                notes = null,
                restEndsAt = null,
                readinessNote = null,
                zoneOffsetMinutes = 0,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null,
                templateId = template,
            ),
        )

        assertTrue(repository.pendingOccurrences(today, utc).getOrNull().isNullOrEmpty())
    }

    @Test
    fun recordingASkip_settlesTheOccurrence_andASecondOneDoesNotDuplicateIt() = runTest {
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.TUESDAY)
        repository.setActiveProgram(program)
        val slotId = slot(program).id

        repository.skipOccurrences(listOf(slotId), monday)
        repository.skipOccurrences(listOf(slotId), monday)

        assertTrue(repository.pendingOccurrences(today, utc).getOrNull().isNullOrEmpty())
        assertEquals(1, database.programDao().findSkipsForWeek(monday.toEpochDay()).size)
    }

    @Test
    fun withNoActiveProgram_nothingIsEverPending() = runTest {
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.TUESDAY)

        assertTrue(repository.pendingOccurrences(today, utc).getOrNull().isNullOrEmpty())
    }

    @Test
    fun aFinishedSession_isDone_andAnAbandonedStart_isMissed() = runTest {
        // The rule that makes adherence stricter than the prompt: a session that was started
        // silences P3.3's question, but only one that was *finished* counts as training here.
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.TUESDAY)
        repository.setActiveProgram(program)
        insertSession(id = "done", date = "2026-10-06T09:00:00Z", finished = true, templateId = template)
        insertSession(id = "abandoned", date = "2026-10-13T09:00:00Z", finished = false, templateId = template)

        val report = repository.monthAdherence(
            month = YearMonth.of(2026, 10),
            today = LocalDate.of(2026, 10, 15),
            zone = utc,
        ).getOrNull()

        assertTrue(report!!.hasActiveProgram)
        assertEquals(1, report.adherence.done)
        assertEquals(1, report.adherence.missed)
        // The unfinished session marks no trained day at all.
        assertEquals(setOf(LocalDate.of(2026, 10, 6)), report.adherence.trainedDays)
        assertEquals(
            setOf(
                LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 13),
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 27),
            ),
            report.adherence.scheduledDays,
        )
    }

    @Test
    fun aRecordedSkip_reachesTheMonth_asSkipped_notMissed() = runTest {
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.TUESDAY)
        repository.setActiveProgram(program)
        repository.skipOccurrences(listOf(slot(program).id), monday)

        val report = repository.monthAdherence(
            month = YearMonth.of(2026, 10),
            today = today,
            zone = utc,
        ).getOrNull()

        assertEquals(1, report!!.adherence.skipped)
        assertEquals(0, report.adherence.missed)
    }

    @Test
    fun withNoActiveProgram_theDaysTrainedAreStillRead_butNothingIsScored() = runTest {
        // The calendar needs no schedule; the pins home falls back to carry no skip record, so
        // there is deliberately no ratio to compute.
        insertSession(id = "hand-started", date = "2026-10-06T09:00:00Z", finished = true, templateId = null)

        val report = repository.monthAdherence(
            month = YearMonth.of(2026, 10),
            today = today,
            zone = utc,
        ).getOrNull()

        assertEquals(false, report!!.hasActiveProgram)
        assertEquals(0, report.adherence.scored)
        assertEquals(null, report.adherence.ratio)
        assertEquals(setOf(LocalDate.of(2026, 10, 6)), report.adherence.trainedDays)
    }

    /** A session row, finished or abandoned, started from [templateId] or by hand. */
    private suspend fun insertSession(id: String, date: String, finished: Boolean, templateId: String?) {
        val startedAt = Instant.parse(date).toEpochMilli()
        database.workoutDao().insertSession(
            WorkoutSessionEntity(
                id = id,
                startedAt = startedAt,
                finishedAt = if (finished) startedAt + 3_600_000 else null,
                notes = null,
                restEndsAt = null,
                readinessNote = null,
                zoneOffsetMinutes = 0,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null,
                templateId = templateId,
            ),
        )
    }

    private suspend fun create(name: String): String =
        (repository.createProgram(name) as DataResult.Success).data

    /** The single slot of a program the test has already filled, in its order (P3.3). */
    private suspend fun slot(programId: String): ProgramSlot =
        repository.observeSlots(programId).first().single()

    private suspend fun createTemplate(name: String): String {
        val templates = RoomTemplateRepository(database, clock)
        val id = (templates.createTemplate(name) as DataResult.Success).data
        templates.addExercise(id, "back-squat")
        return id
    }

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
