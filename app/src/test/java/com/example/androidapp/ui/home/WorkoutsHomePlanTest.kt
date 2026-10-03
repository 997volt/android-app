package com.example.androidapp.ui.home

import com.example.androidapp.domain.model.ProgramSlot
import com.example.androidapp.domain.model.WorkoutProgram
import com.example.androidapp.domain.model.WorkoutTemplate
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import org.junit.Test

/**
 * What home shows for today (ROADMAP P3.3, falling back to N16's pins).
 *
 * The rule is short and easy to get subtly wrong: with a program active the program *is*
 * the schedule — an empty day is rest — and only "no active program" returns the pins.
 */
class WorkoutsHomePlanTest {

    private val program = WorkoutProgram(id = "p1", name = "Upper/Lower", isActive = true)

    private fun slot(id: String, weekday: DayOfWeek?, position: Int, templateId: String) =
        ProgramSlot(
            id = id,
            programId = "p1",
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

        val plan = todaysPlanFor(program, slots, templates = emptyList(), day = DayOfWeek.FRIDAY)

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

        val plan = todaysPlanFor(program, slots, emptyList(), DayOfWeek.FRIDAY)

        assertThat(plan.map { it.id }).containsExactly("s1", "s2")
        assertThat(plan.map { it.templateId }).containsExactly("t1", "t1")
    }

    @Test
    fun withAProgramActive_aDayItSchedulesNothing_isRest_notThePins() {
        val slots = listOf(slot("s1", DayOfWeek.MONDAY, position = 0, templateId = "t1"))
        val pins = listOf(pinned("pin", DayOfWeek.FRIDAY))

        val plan = todaysPlanFor(program, slots, pins, DayOfWeek.FRIDAY)

        assertThat(plan).isEmpty()
    }

    @Test
    fun withNoProgramActive_thePinsAreThePlan() {
        val pins = listOf(
            pinned("a", DayOfWeek.FRIDAY),
            pinned("b", DayOfWeek.MONDAY),
            pinned("c", DayOfWeek.FRIDAY),
        )

        val plan = todaysPlanFor(program = null, slots = emptyList(), templates = pins, day = DayOfWeek.FRIDAY)

        assertThat(plan.map { it.id }).containsExactly("a", "c")
        // A pinned plan starts itself, so the row's own id is also its template.
        assertThat(plan.map { it.templateId }).containsExactly("a", "c")
    }
}
