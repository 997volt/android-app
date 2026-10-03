package com.example.androidapp.domain.model

import java.time.DayOfWeek

/**
 * A named, ordered list of training slots (ROADMAP P3.3).
 *
 * The container the N16 weekday pins cannot be on their own: a pin says what happens on
 * a Tuesday, but nothing orders the pins against each other, so "which one is next" and
 * "was that a skip or a rest day" have no answer. A program supplies the order; a slot
 * supplies the day.
 *
 * [isActive] means home follows this program, and **more than one program may be active**
 * (ROADMAP P3.12, amending P3.3): home's today plan, the missed-day question and adherence
 * all read the *union* of the active programs. When no live program is active, the home
 * screen falls back to the template pins it already reads.
 *
 * [position] is the authored order, low first: which active program comes first is a choice,
 * not an accident of its name or when it was created (P3.12).
 */
data class WorkoutProgram(
    val id: String,
    val name: String,
    val slotCount: Int = 0,
    val isActive: Boolean = false,
    val position: Int = 0,
)

/**
 * One slot of a program: a template, and the weekday it is done on (ROADMAP P3.3).
 *
 * [weekday] null means the slot is **order-only** — it takes part in "which one is next"
 * but can never be missed, because there is no day to miss.
 */
data class ProgramSlot(
    val id: String,
    val programId: String,
    val templateId: String,
    val position: Int,
    val weekday: DayOfWeek? = null,
    /** The template's name, carried by the slot's read so a list needs one subscription. */
    val templateName: String = "",
    val exerciseCount: Int = 0,
)
