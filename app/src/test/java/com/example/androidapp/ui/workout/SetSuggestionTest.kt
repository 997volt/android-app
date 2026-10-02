package com.example.androidapp.ui.workout

import com.example.androidapp.domain.model.SetEntry
import com.example.androidapp.domain.model.PreviousPerformance
import com.example.androidapp.domain.model.ProgressionReason
import com.example.androidapp.domain.Weight
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the next set is prefilled with (ROADMAP P1.3, N14).
 *
 * The file that holds the rule says its precedence is covered by fast tests rather
 * than by tapping — this is that file's test. The plan's precedence gets its own cases
 * because it is the one source that can say something about the *second* set of an
 * exercise, which is where a ramp lives.
 */
class SetSuggestionTest {

    @Test
    fun withNothingToGoOn_itFallsBackToTheDefault() {
        val suggestion = suggestionForNextSet(loggedSets = emptyList(), previous = null, )

        assertEquals(DEFAULT_REPS, suggestion.reps)
        assertEquals(Weight.DEFAULT_GRAMS, suggestion.weightGrams)
    }

    @Test
    fun withinASession_itRepeatsTheSetJustLogged() {
        val suggestion = suggestionForNextSet(
            loggedSets = listOf(SetRow(id = "s1", number = 1, reps = 5, weightGrams = 100_000)),
            previous = null,
        )

        assertEquals(5, suggestion.reps)
        assertEquals(100_000L, suggestion.weightGrams)
    }

    @Test
    fun thePlansTargetWins_overRepeatingTheLastSet() {
        // A ramp is the case that needs this: set two is not set one.
        val suggestion = suggestionForNextSet(
            loggedSets = listOf(SetRow(id = "s1", number = 1, reps = 3, weightGrams = 100_000)),
            previous = null,
            planned = PlannedTarget(reps = 3, weightGrams = 105_000),
        )

        assertEquals(3, suggestion.reps)
        assertEquals(105_000L, suggestion.weightGrams)
    }

    @Test
    fun aPlanThatNamesOnlyReps_keepsTheWeightFromTheFallback() {
        // "3 sets of 5" says nothing about the bar; a zero would be a claim.
        val suggestion = suggestionForNextSet(
            loggedSets = listOf(SetRow(id = "s1", number = 1, reps = 5, weightGrams = 80_000)),
            previous = null,
            planned = PlannedTarget(reps = 5, weightGrams = null),
        )

        assertEquals(5, suggestion.reps)
        assertEquals(80_000L, suggestion.weightGrams)
    }

    @Test
    fun aPlanThatNamesOnlyAWeight_keepsTheRepsFromTheFallback() {
        val suggestion = suggestionForNextSet(
            loggedSets = emptyList(),
            previous = null,
                        planned = PlannedTarget(reps = null, weightGrams = 120_000),
        )

        assertEquals(DEFAULT_REPS, suggestion.reps)
        assertEquals(120_000L, suggestion.weightGrams)
    }

    @Test
    fun withNoPlan_theOlderPrecedenceStillHolds() {
        // The plan being absent must not change what P1.3 already decided.
        val suggestion = suggestionForNextSet(
            loggedSets = emptyList(),
            previous = null,
                        planned = null,
        )

        assertEquals(DEFAULT_REPS, suggestion.reps)
        assertEquals(Weight.DEFAULT_GRAMS, suggestion.weightGrams)
    }

    @Test
    fun aPlanThatPrescribesAssistance_prefillsIt() {
        // ROADMAP N15: the assisted pull-up plan is a column of negative numbers, and
        // the workout has to show them back.
        val suggestion = suggestionForNextSet(
            loggedSets = emptyList(),
            previous = null,
                        planned = PlannedTarget(reps = 8, weightGrams = null, assistanceGrams = 20_000L),
        )

        assertEquals(8, suggestion.reps)
        assertEquals(20_000L, suggestion.assistanceGrams)
        // And no weight: an assisted set that also carried the fallback's default
        // would report 20 kg of volume for a set the machine did the work on.
        assertEquals(0L, suggestion.weightGrams)
    }

    @Test
    fun repeatingASet_repeatsItsAssistance() {
        val suggestion = suggestionForNextSet(
            loggedSets = listOf(
                SetRow(
                    id = "s1",
                    number = 1,
                    reps = 8,
                    weightGrams = 0L,
                    assistanceGrams = 20_000L,
                ),
            ),
            previous = null,
        )

        assertEquals(20_000L, suggestion.assistanceGrams)
    }

    @Test
    fun aPlanThatNamesRepsAndNoLoad_isProgressedFromHistory() {
        // ROADMAP B24's gap, and the N22 branch it names: a plan that says "2 reps" and no bar,
        // with nothing logged yet this session — so the rule, not the fallback, has to decide
        // whether that means one more rep or one more step. History says 100 kg × 2, which is the
        // ceiling, so it is a step.
        val suggestion = suggestionForNextSet(
            loggedSets = emptyList(),
            previous = PreviousPerformance(
                listOf(
                    SetEntry(
                        id = "old",
                        sessionExerciseId = "se-old",
                        setIndex = 0,
                        reps = 2,
                        weightGrams = 100_000,
                    ),
                ),
            ),
            planned = PlannedTarget(reps = 2, weightGrams = null),
        )

        // N33: the prefill is what you did last time, unchanged — the step is an offer beside it, so a
        // lifter who progresses by hand is not undoing the app's arithmetic on every first set.
        assertEquals("last time's bar, unchanged", 100_000L, suggestion.weightGrams)
        assertEquals("at the reps the plan asked for", 2, suggestion.reps)
        assertEquals("and the step is proposed", 102_500L, suggestion.offer?.weightGrams)
        assertEquals(ProgressionReason.MORE_WEIGHT, suggestion.offer?.reason)
    }

    @Test
    fun aPlanThatNamesRepsAndNoLoad_belowTheCeiling_addsARep() {
        // The same branch, on the other side of the ceiling: 90 kg for 2 against a plan of 5.
        val suggestion = suggestionForNextSet(
            loggedSets = emptyList(),
            previous = PreviousPerformance(
                listOf(
                    SetEntry(
                        id = "old",
                        sessionExerciseId = "se-old",
                        setIndex = 0,
                        reps = 2,
                        weightGrams = 90_000,
                    ),
                ),
            ),
            planned = PlannedTarget(reps = 5, weightGrams = null),
        )

        assertEquals("the same bar as last time", 90_000L, suggestion.weightGrams)
        assertEquals("the reps are the plan's to decide", 5, suggestion.reps)
        // The proposal is a rep, and it is still only a proposal (N33).
        assertEquals("one more rep offered, not applied", 3, suggestion.offer?.reps)
        assertEquals(90_000L, suggestion.offer?.weightGrams)
        assertEquals(ProgressionReason.MORE_REPS, suggestion.offer?.reason)
    }
}
