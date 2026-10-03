package com.example.androidapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.data.local.ExerciseEntity
import com.example.androidapp.data.local.SessionExerciseEntity
import com.example.androidapp.data.local.SetEntryEntity
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
import com.example.androidapp.domain.model.SetType
import com.example.androidapp.domain.repository.SlotSetEdit
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
        assertTrue(repository.observeActivePrograms().first().isEmpty())
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
    fun moreThanOneProgram_canBeActive_atOnce() = runTest {
        // P3.12 amends P3.3's "at most one": a lifting block and a conditioning one are two
        // schedules at once, and both are followed until one is turned off.
        val first = create("Upper/Lower")
        val second = create("PPL")

        repository.activateProgram(first)
        repository.activateProgram(second)

        assertEquals(
            listOf(first, second),
            repository.observeActivePrograms().first().map { it.id },
        )
        assertEquals(2, repository.observePrograms().first().count { it.isActive })
    }

    @Test
    fun deactivatingAProgram_stopsFollowingOnlyThatOne() = runTest {
        val first = create("Upper/Lower")
        val second = create("PPL")
        repository.activateProgram(first)
        repository.activateProgram(second)

        repository.deactivateProgram(first)

        assertEquals(listOf(second), repository.observeActivePrograms().first().map { it.id })

        repository.deactivateProgram(second)
        assertTrue(repository.observeActivePrograms().first().isEmpty())
    }

    @Test
    fun programs_keepTheAuthoredOrder_andMovingSwapsTwo() = runTest {
        // The order is authored rather than alphabetical or by creation time, so it has to
        // survive a move (ROADMAP P3.12).
        val first = create("Alpha")
        val second = create("Beta")
        val third = create("Gamma")

        assertEquals(
            listOf("Alpha", "Beta", "Gamma"),
            repository.observePrograms().first().map { it.name },
        )

        repository.moveProgram(third, delta = -1)

        assertEquals(
            listOf(first, third, second),
            repository.observePrograms().first().map { it.id },
        )
    }

    @Test
    fun theMissedDayQuestion_walksEveryActiveProgram() = runTest {
        // Two schedules, each with yesterday's slot undone: the union is what the prompt lists,
        // earliest first (ROADMAP P3.12).
        val lifting = create("Upper/Lower")
        val conditioning = create("Conditioning")
        repository.addSlot(lifting, createTemplate("Heavy lower"), DayOfWeek.TUESDAY)
        repository.addSlot(conditioning, createTemplate("Intervals"), DayOfWeek.TUESDAY)
        repository.activateProgram(lifting)
        repository.activateProgram(conditioning)

        val pending = repository.pendingOccurrences(today, utc).getOrNull()

        assertEquals(
            setOf(
                repository.observeSlots(lifting).first().single().id,
                repository.observeSlots(conditioning).first().single().id,
            ),
            pending?.map { it.slotId }?.toSet(),
        )
    }

    @Test
    fun clearingTheActiveProgram_leavesThePinsToAnswer() = runTest {
        val program = create("Upper/Lower")
        repository.activateProgram(program)

        repository.deactivateProgram(program)

        assertTrue(repository.observeActivePrograms().first().isEmpty())
    }

    @Test
    fun deletingTheActiveProgram_leavesNoneActive() = runTest {
        val program = create("Upper/Lower")
        repository.activateProgram(program)

        repository.deleteProgram(program)

        assertTrue(repository.observePrograms().first().isEmpty())
        assertTrue(repository.observeActivePrograms().first().isEmpty())
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
        repository.activateProgram(program)
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
        repository.activateProgram(program)
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
        repository.activateProgram(program)
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
        repository.activateProgram(program)
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
    fun adherence_scoresTheUnionOfEveryActiveProgram() = runTest {
        // Two schedules at once (P3.12): the month's counts add up across them, and the days they
        // fall on are the union too.
        val lifting = create("Upper/Lower")
        val conditioning = create("Conditioning")
        repository.addSlot(lifting, createTemplate("Heavy lower"), DayOfWeek.TUESDAY)
        repository.addSlot(conditioning, createTemplate("Intervals"), DayOfWeek.THURSDAY)
        repository.activateProgram(lifting)
        repository.activateProgram(conditioning)

        val report = repository.monthAdherence(
            month = YearMonth.of(2026, 10),
            today = LocalDate.of(2026, 10, 8),
            zone = utc,
        ).getOrNull()

        assertTrue(report!!.hasActiveProgram)
        // Both programs' elapsed days were missed: Tuesday the 6th and Thursday the 1st.
        assertEquals(2, report.adherence.missed)
        // And the grid draws the union: both programs' scheduled weekdays are in the month.
        assertTrue(report.adherence.scheduledDays.contains(LocalDate.of(2026, 10, 6)))
        assertTrue(report.adherence.scheduledDays.contains(LocalDate.of(2026, 10, 8)))
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

    @Test
    fun aSlotPrescribesItsOwnSets_inOrder_andTheTemplateStandsWhereItSaysNothing() = runTest {
        // ROADMAP P3.8: two slots pointing at one template can train it differently, because the
        // prescription belongs to the slot. An empty one is absent, not an empty row.
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.MONDAY)
        val slotId = slot(program).id

        assertTrue(repository.observeSlotPrescriptions(slotId).first().isEmpty())

        repository.addSlotSet(
            slotId,
            "back-squat",
            SlotSetEdit(targetWeightGrams = 100_000L, targetRepsMin = 3, targetRepsMax = 5),
        )
        repository.addSlotSet(
            slotId,
            "back-squat",
            SlotSetEdit(role = SetType.TOP_SET, targetPercentOf1Rm = 85, targetRepsMin = 1),
        )

        val prescription = repository.observeSlotPrescriptions(slotId).first().single()
        assertEquals("back-squat", prescription.exerciseId)
        assertEquals(listOf(0, 1), prescription.sets.map { it.setIndex })
        assertEquals(100_000L, prescription.sets[0].targetWeightGrams)
        assertEquals(3, prescription.sets[0].targetRepsMin)
        // The percentage is the one target a template's planned set cannot carry.
        assertEquals(85, prescription.sets[1].targetPercentOf1Rm)
        assertEquals(SetType.TOP_SET, prescription.sets[1].role)
    }

    @Test
    fun aRestTheSlotPrescribes_isReadBack_andClearingItWithNoSetsRemovesTheRow() = runTest {
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.MONDAY)
        val slotId = slot(program).id

        repository.setSlotExercisePlan(slotId, "back-squat", restSeconds = 150, techniqueNote = "brace")

        val prescribed = repository.observeSlotPrescriptions(slotId).first().single()
        assertEquals(150, prescribed.restSeconds)
        assertEquals("brace", prescribed.techniqueNote)

        repository.setSlotExercisePlan(slotId, "back-squat", restSeconds = null, techniqueNote = null)

        assertTrue(
            "a prescription with nothing left to say is absent, not an empty row",
            repository.observeSlotPrescriptions(slotId).first().isEmpty(),
        )
    }

    @Test
    fun removingTheLastPrescribedSet_leavesTheSlotSayingNothing() = runTest {
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.MONDAY)
        val slotId = slot(program).id
        repository.addSlotSet(slotId, "back-squat", SlotSetEdit(targetRepsMin = 5))
        val setId = repository.observeSlotPrescriptions(slotId).first().single().sets.single().id

        repository.removeSlotSet(setId)

        assertTrue(repository.observeSlotPrescriptions(slotId).first().isEmpty())
    }

    @Test
    fun prescribingAnExerciseTheTemplateDoesNotTrain_isRefused() = runTest {
        // A prescription for an exercise the template does not have would be invisible on every
        // screen the moment it was written.
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.MONDAY)
        val slotId = slot(program).id

        val result = repository.addSlotSet(slotId, "front-squat", SlotSetEdit(targetRepsMin = 5))

        assertTrue(result is DataResult.Failure)
        assertTrue(repository.observeSlotPrescriptions(slotId).first().isEmpty())
    }

    @Test
    fun aPercentageOutsideTheScale_isRefused() = runTest {
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.MONDAY)
        val slotId = slot(program).id

        val result = repository.addSlotSet(slotId, "back-squat", SlotSetEdit(targetPercentOf1Rm = 120))

        assertTrue(result is DataResult.Failure)
    }

    @Test
    fun aSlotsOwnHistory_isWhatThatSlotProgressesFrom() = runTest {
        // ROADMAP P3.8: one template in two slots, and the heavy Monday and the light Friday
        // progress apart. A session names only the template, so which slot it belongs to is
        // settled the way P3.3 settles an occurrence.
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.MONDAY)
        repository.addSlot(program, template, DayOfWeek.FRIDAY)
        val slots = repository.observeSlots(program).first()
        val mondaySlot = slots.first { it.weekday == DayOfWeek.MONDAY }
        val fridaySlot = slots.first { it.weekday == DayOfWeek.FRIDAY }

        insertSessionWithSet("mon", "2026-09-28T09:00:00Z", template, weightGrams = 100_000L)
        insertSessionWithSet("fri", "2026-10-02T09:00:00Z", template, weightGrams = 80_000L)

        val fromMonday = repository
            .slotPreviousPerformance(mondaySlot.id, "back-squat", "current", utc)
            .getOrNull()
        val fromFriday = repository
            .slotPreviousPerformance(fridaySlot.id, "back-squat", "current", utc)
            .getOrNull()

        assertEquals(100_000L, fromMonday?.sets?.single()?.weightGrams)
        assertEquals(80_000L, fromFriday?.sets?.single()?.weightGrams)
    }

    /** A finished session from [templateId] with one logged set of `back-squat` (P3.8). */
    private suspend fun insertSessionWithSet(
        id: String,
        date: String,
        templateId: String,
        weightGrams: Long,
    ) {
        val startedAt = Instant.parse(date).toEpochMilli()
        database.workoutDao().insertSession(
            WorkoutSessionEntity(
                id = id,
                startedAt = startedAt,
                finishedAt = startedAt + 3_600_000,
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
        database.workoutDao().insertSessionExercise(
            SessionExerciseEntity(
                id = "$id-se",
                sessionId = id,
                exerciseId = "back-squat",
                position = 0,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null,
            ),
        )
        database.workoutDao().insertSet(
            SetEntryEntity(
                id = "$id-set",
                sessionExerciseId = "$id-se",
                setIndex = 0,
                reps = 5,
                weightGrams = weightGrams,
                setType = SetType.NORMAL,
                completedAt = null,
                createdAt = 0L,
                updatedAt = 0L,
                deletedAt = null,
            ),
        )
    }

    @Test
    fun estimatedOneRepMax_comesFromTheHeaviestWorkingSet_andIsNullWhenNothingIs() = runTest {
        // ROADMAP P3.8: a percentage prescription resolves against N17's estimate, and an exercise
        // with nothing estimable has no number rather than a borrowed one.
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.MONDAY)
        insertSessionWithSet("heavy", "2026-09-28T09:00:00Z", template, weightGrams = 100_000L)

        // Epley: 100 kg for 5 is 100 * (1 + 5/30) = 116.67 kg, rounded to the nearest half-kilo.
        assertEquals(116_500L, repository.estimatedOneRepMax("back-squat").getOrNull())
        assertNull(repository.estimatedOneRepMax("front-squat").getOrNull())
    }

    @Test
    fun theRun_followsTheLastSlotTrained_notTheDay() = runTest {
        // ROADMAP P3.9: an order-only program still runs A -> B, and a day passing moves nothing.
        val program = create("Upper/Lower")
        val first = createTemplate("Workout A")
        val second = createTemplate("Workout B")
        repository.addSlot(program, first)
        repository.addSlot(program, second)
        val slots = repository.observeSlots(program).first()

        val atStart = repository.observeProgramRun(program).first()
        assertEquals(slots[0].id, atStart?.slot?.id)
        assertEquals(true, atStart?.isAtStart)

        insertSessionWithSet("a-session", "2026-10-06T09:00:00Z", first, weightGrams = 100_000L)

        val moved = repository.observeProgramRun(program).first()
        assertEquals(slots[1].id, moved?.slot?.id)
        assertEquals(false, moved?.isAtStart)
    }

    @Test
    fun aSkip_movesTheRunOn_too() = runTest {
        val program = create("Upper/Lower")
        val first = createTemplate("Workout A")
        repository.addSlot(program, first)
        repository.addSlot(program, createTemplate("Workout B"))
        val slots = repository.observeSlots(program).first()

        repository.skipOccurrences(listOf(slots[0].id), monday)

        assertEquals(slots[1].id, repository.observeProgramRun(program).first()?.slot?.id)
    }

    @Test
    fun aDeloadWeek_isNotScored_untilItIsUnmarked() = runTest {
        // ROADMAP P3.10: a deliberate back-off must not read as a failure, and unmarking restores
        // the ordinary reading.
        val program = create("Upper/Lower")
        val template = createTemplate("Heavy lower")
        repository.addSlot(program, template, DayOfWeek.TUESDAY)
        repository.activateProgram(program)

        repository.setDeloadWeek(program, monday, marked = true)
        // Idempotent: marking a marked week must not double-record.
        repository.setDeloadWeek(program, monday, marked = true)
        assertEquals(1, database.programDeloadDao().countDeload(program, monday.toEpochDay()))

        val marked = repository.monthAdherence(YearMonth.of(2026, 10), today, utc).getOrNull()
        assertEquals(0, marked!!.adherence.scored)
        assertEquals(setOf(monday), marked.deloadWeeks[program])
        assertTrue(marked.programs.any { it.id == program })

        repository.setDeloadWeek(program, monday, marked = false)

        val unmarked = repository.monthAdherence(YearMonth.of(2026, 10), today, utc).getOrNull()
        assertEquals(1, unmarked!!.adherence.missed)
        assertTrue(unmarked.deloadWeeks[program].isNullOrEmpty())
    }

    /** A session row, finished or abandoned, started from [templateId] or by hand. */
    private suspend fun insertSession(id: String, date: String, finished: Boolean, templateId: String?) {        val startedAt = Instant.parse(date).toEpochMilli()
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
