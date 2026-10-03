package com.example.androidapp.ui.home

import com.example.androidapp.domain.model.ProgramRun
import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.domain.model.WorkoutTemplate
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import org.junit.Test

/**
 * What home shows for today (ROADMAP P3.3, unioned by P3.12, falling back to N16's pins).
 *
 * The rule is short and easy to get subtly wrong: with anything active the union *is* the
 * schedule — an empty day is rest — and only "no active program" returns the pins.
 */
class WorkoutsHomePlanTest {

    private val program = WorkoutProgram(id = "p1", name = "Upper/Lower", isActive = true, position = 0)
    private val second = WorkoutProgram(id = "p2", name = "Conditioning", isActive = true, position = 1)

    private fun slot(
        id: String,
        weekday: DayOfWeek?,
        position: Int,
        templateId: String,
        programId: String = "p1",
    ) = ProgramSlot(
        id = id,
        programId = programId,
        templateId = templateId,
        position = position,
        weekday = weekday,
        templateName = "Template $templateId",
        exerciseCount = position + 1,
    )

    private fun pinned(id: String, weekday: DayOfWeek?) =
        WorkoutTemplate(id = id, name = "Plan $id", exerciseCount = 3, weekday = weekday)

    @Test
    fun withAProgramActive_theSlotsDayIsThePlan_inProgramOrder() {
        val slots = listOf(
            slot("s2", DayOfWeek.FRIDAY, position = 1, templateId = "t2"),
            slot("s1", DayOfWeek.FRIDAY, position = 0, templateId = "t1"),
            slot("s3", DayOfWeek.MONDAY, position = 2, templateId = "t3"),
        )

        val plan = todaysPlanFor(listOf(program), slots, templates = emptyList(), day = DayOfWeek.FRIDAY)

        assertThat(plan.map { it.id }).containsExactly("s1", "s2").inOrder()
        assertThat(plan.map { it.templateId }).containsExactly("t1", "t2").inOrder()
    }

    @Test
    fun aProgramsRowIsKeyedByItsSlot_notByItsTemplate() {
        // The same template twice in a program is legal, and a list keyed by template would
        // collide the moment it happened.
        val slots = listOf(
            slot("s1", DayOfWeek.FRIDAY, position = 0, templateId = "t1"),
            slot("s2", DayOfWeek.FRIDAY, position = 1, templateId = "t1"),
        )

        val plan = todaysPlanFor(listOf(program), slots, emptyList(), DayOfWeek.FRIDAY)

        assertThat(plan.map { it.id }).containsExactly("s1", "s2")
        assertThat(plan.map { it.templateId }).containsExactly("t1", "t1")
    }

    @Test
    fun twoActivePrograms_areUnioned_byProgramPositionThenSlotPosition() {
        // Both programs number their slots from zero, so ordering by slot position alone would
        // interleave them; the program's authored place comes first (ROADMAP P3.12).
        val slots = listOf(
            slot("b1", DayOfWeek.FRIDAY, position = 0, templateId = "tb", programId = "p2"),
            slot("a1", DayOfWeek.FRIDAY, position = 1, templateId = "ta1", programId = "p1"),
            slot("a0", DayOfWeek.FRIDAY, position = 0, templateId = "ta0", programId = "p1"),
        )

        val plan = todaysPlanFor(listOf(program, second), slots, emptyList(), DayOfWeek.FRIDAY)

        assertThat(plan.map { it.id }).containsExactly("a0", "a1", "b1").inOrder()
    }

    @Test
    fun withAProgramActive_aDayItSchedulesNothing_isRest_notThePins() {
        val slots = listOf(slot("s1", DayOfWeek.MONDAY, position = 0, templateId = "t1"))
        val pins = listOf(pinned("pin", DayOfWeek.FRIDAY))

        val plan = todaysPlanFor(listOf(program), slots, pins, DayOfWeek.FRIDAY)

        assertThat(plan).isEmpty()
    }

    @Test
    fun withNoProgramActive_thePinsAreThePlan() {
        val pins = listOf(
            pinned("a", DayOfWeek.FRIDAY),
            pinned("b", DayOfWeek.MONDAY),
            pinned("c", DayOfWeek.FRIDAY),
        )

        val plan = todaysPlanFor(programs = emptyList(), slots = emptyList(), templates = pins, day = DayOfWeek.FRIDAY)

        assertThat(plan.map { it.id }).containsExactly("a", "c")
        // A pinned plan starts itself, so the row's own id is also its template.
        assertThat(plan.map { it.templateId }).containsExactly("a", "c")
    }

    @Test
    fun aProgramWithNothingToday_showsWhereItsRunIs() {
        // ROADMAP P3.9: the order is a run, so a day the program schedules nothing still has an
        // answer to "which one is next".
        val slots = listOf(slot("s2", weekday = null, position = 1, templateId = "t2"))
        val run = ProgramRun(slot = slots.single(), isAtStart = false)

        val nextUp = nextUpFor(
            programs = listOf(program),
            slots = slots,
            runs = mapOf("p1" to run),
            day = DayOfWeek.FRIDAY,
        )

        assertThat(nextUp.map { it.plan.slotId }).containsExactly("s2")
        assertThat(nextUp.single().programName).isEqualTo("Upper/Lower")
        assertThat(nextUp.single().isAtStart).isFalse()
    }

    @Test
    fun aProgramWithASlotToday_hasNoNextUpRow() {
        // The today plan is then the answer; a next-up row beside it would contradict it.
        val slots = listOf(slot("s1", DayOfWeek.FRIDAY, position = 0, templateId = "t1"))

        val nextUp = nextUpFor(
            programs = listOf(program),
            slots = slots,
            runs = mapOf("p1" to ProgramRun(slot = slots.single(), isAtStart = true)),
            day = DayOfWeek.FRIDAY,
        )

        assertThat(nextUp).isEmpty()
    }

    @Test
    fun aProgramWithNoRunYet_hasNoNextUpRow() {
        val nextUp = nextUpFor(
            programs = listOf(program),
            slots = emptyList(),
            runs = mapOf("p1" to null),
            day = DayOfWeek.FRIDAY,
        )

        assertThat(nextUp).isEmpty()
    }

    @Test
    fun twoPrograms_offerTheirRuns_inTheAuthoredOrder() {
        val slotA = slot("a", weekday = null, position = 0, templateId = "ta", programId = "p1")
        val slotB = slot("b", weekday = null, position = 0, templateId = "tb", programId = "p2")

        val nextUp = nextUpFor(
            programs = listOf(program, second),
            slots = listOf(slotA, slotB),
            runs = mapOf(
                "p1" to ProgramRun(slot = slotA, isAtStart = true),
                "p2" to ProgramRun(slot = slotB, isAtStart = true),
            ),
            day = DayOfWeek.FRIDAY,
        )

        assertThat(nextUp.map { it.plan.slotId }).containsExactly("a", "b").inOrder()
        assertThat(nextUp.map { it.programName }).containsExactly("Upper/Lower", "Conditioning").inOrder()
    }
}
