package com.example.androidapp.ui.workout

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
        val suggestion = suggestionForNextSet(loggedSets = emptyList(), previous = null, nextIndex = 0)

        assertEquals(DEFAULT_REPS, suggestion.reps)
        assertEquals(Weight.DEFAULT_GRAMS, suggestion.weightGrams)
    }

    @Test
    fun withinASession_itRepeatsTheSetJustLogged() {
        val suggestion = suggestionForNextSet(
            loggedSets = listOf(SetRow(id = "s1", number = 1, reps = 5, weightGrams = 100_000)),
            previous = null,
            nextIndex = 1,
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
            nextIndex = 1,
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
            nextIndex = 1,
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
            nextIndex = 0,
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
            nextIndex = 0,
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
            nextIndex = 0,
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
            nextIndex = 1,
        )

        assertEquals(20_000L, suggestion.assistanceGrams)
    }
}
