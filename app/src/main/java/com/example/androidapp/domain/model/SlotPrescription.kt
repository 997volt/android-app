package com.example.androidapp.domain.model

/**
 * One set a program slot prescribes for one exercise (ROADMAP P3.8).
 *
 * A target, not a claim, exactly as a template's planned set is (N14): every part is nullable,
 * nothing verifies it, and a set that differs is expected. It carries the plan's own modifier
 * vocabulary — [role], the load split into weight and assistance, the rep range and
 * [targetRpeHalves] — plus **one** thing a template's planned set cannot say, [targetPercentOf1Rm].
 *
 * A set with a floor and no ceiling ([targetRepsMin] alone) is the AMRAP a plan can already
 * write, not a second way to say it.
 */
data class SlotSet(
    val id: String,
    val setIndex: Int,
    val role: SetType = SetType.NORMAL,
    val targetWeightGrams: Long? = null,
    /** The assistance the slot prescribes, as a magnitude (ROADMAP N15). */
    val targetAssistanceGrams: Long? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpeHalves: Int? = null,
    /**
     * A percentage of the exercise's estimated one-rep max, or null (P3.8).
     *
     * **Derived, never assumed**: the kilograms come from N17's Epley estimate, and an exercise
     * with nothing estimable — no working set at twelve reps or fewer — has no number and says so
     * rather than borrowing one.
     */
    val targetPercentOf1Rm: Int? = null,
    val note: String? = null,
)

/**
 * What a slot prescribes for one exercise of its template (ROADMAP P3.8).
 *
 * A slot that names only a template is a schedule; this is what lets two slots pointing at one
 * template train it differently. **An empty prescription leaves the template's targets
 * standing** (N14), which is why [restSeconds] and [techniqueNote] are nullable and [sets] may
 * be empty: the slot overrides only what it says.
 */
data class SlotPrescription(
    val exerciseId: String,
    /** A rest this slot prescribes, or null to use the template's and then the library's (N14). */
    val restSeconds: Int? = null,
    /** A cue this slot prescribes, or null to use the template's and then the library's (N14). */
    val techniqueNote: String? = null,
    val sets: List<SlotSet> = emptyList(),
) {
    /** True when the slot says nothing about this exercise, so the template's targets stand. */
    val isEmpty: Boolean
        get() = restSeconds == null && techniqueNote == null && sets.isEmpty()
}
