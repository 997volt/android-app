package com.example.androidapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Double progression (ROADMAP N22).
 *
 * The rule is only worth having if it can be argued with, so each case states the situation
 * and the proposal: a rep short of the ceiling adds a rep, reaching it adds weight, assisted
 * work comes off the machine, and no history proposes nothing rather than a guess.
 */
class ProgressionSuggestionTest {

    private fun did(weightGrams: Long, reps: Int, role: SetType = SetType.NORMAL, assistanceGrams: Long = 0L) =
        PerformedSetSpec(role = role, weightGrams = weightGrams, assistanceGrams = assistanceGrams, reps = reps)

    private fun target(minReps: Int?, maxReps: Int?, weightGrams: Long? = null, assistanceGrams: Long? = null) =
        PlannedSetSpec(
            role = SetType.NORMAL,
            weightGrams = weightGrams,
            assistanceGrams = assistanceGrams,
            minReps = minReps,
            maxReps = maxReps,
        )

    @Test
    fun belowTheCeiling_theLoadStaysAndARepIsAdded() {
        val suggestion = suggestProgression(
            lastTime = listOf(did(90_000L, 2), did(90_000L, 2)),
            target = target(minReps = 2, maxReps = 4),
        )

        assertEquals(90_000L, suggestion.weightGrams)
        assertEquals(3, suggestion.reps)
        assertEquals(ProgressionReason.MORE_REPS, suggestion.reason)
    }

    @Test
    fun atTheCeiling_aStepIsAdded_andTheRangeStartsAgain() {
        val suggestion = suggestProgression(
            lastTime = listOf(did(90_000L, 4), did(90_000L, 4)),
            target = target(minReps = 2, maxReps = 4),
        )

        assertEquals("a pair of 1.25 kg plates", 92_500L, suggestion.weightGrams)
        assertEquals("back to the bottom of the range the plan wrote", 2, suggestion.reps)
        assertEquals(ProgressionReason.MORE_WEIGHT, suggestion.reason)
    }

    @Test
    fun aPlanWithOnlyACeiling_resetsToWhatWasJustDone() {
        // A plan may write one bound (N14). With no floor, repeating the reps that were
        // reached is the honest reset — inventing a number would be the app programming.
        val suggestion = suggestProgression(
            lastTime = listOf(did(100_000L, 5)),
            target = target(minReps = null, maxReps = 5),
        )

        assertEquals(102_500L, suggestion.weightGrams)
        assertEquals(5, suggestion.reps)
    }

    @Test
    fun assistedWork_takesTheHelpDown_insteadOfAddingWeight() {
        val suggestion = suggestProgression(
            lastTime = listOf(did(0L, 8, assistanceGrams = 20_000L)),
            target = target(minReps = 6, maxReps = 8, assistanceGrams = 20_000L),
        )

        assertEquals("the machine does 2.5 kg less", 17_500L, suggestion.assistanceGrams)
        assertEquals(0L, suggestion.weightGrams)
        // Back to the bottom of the range, exactly as adding weight does: taking help off makes
        // the set harder, and keeping the ceiling reps would be a jump rather than a step. This
        // expectation used to read 8, which was the inconsistency B26 was about.
        assertEquals(6, suggestion.reps)
        assertEquals(ProgressionReason.LESS_ASSISTANCE, suggestion.reason)
    }

    @Test
    fun assistanceNeverGoesBelowZero() {
        val suggestion = suggestProgression(
            lastTime = listOf(did(0L, 10, assistanceGrams = 1_000L)),
            target = target(minReps = 8, maxReps = 10, assistanceGrams = 1_000L),
        )

        assertEquals(0L, suggestion.assistanceGrams)
    }

    @Test
    fun aWarmUpNeverSetsTheTarget() {
        // The reason N17, N20 and this all ignore warm-ups: a 60 kg warm-up must not become
        // the number the next session is asked to beat.
        val suggestion = suggestProgression(
            lastTime = listOf(
                did(60_000L, 5, role = SetType.WARMUP),
                did(100_000L, 3),
            ),
            target = target(minReps = 3, maxReps = 5),
        )

        assertEquals(100_000L, suggestion.weightGrams)
        assertEquals(4, suggestion.reps)
        assertEquals(ProgressionReason.MORE_REPS, suggestion.reason)
    }

    @Test
    fun withNoHistory_itProposesThePlan_andSaysSo() {
        // Nothing recorded: the plan is echoed rather than guessed at, and the reason tells
        // the screen not to present it as progress.
        val suggestion = suggestProgression(
            lastTime = emptyList(),
            target = target(minReps = 5, maxReps = 8, weightGrams = 60_000L),
        )

        assertEquals(60_000L, suggestion.weightGrams)
        assertEquals(8, suggestion.reps)
        assertEquals(ProgressionReason.NO_HISTORY, suggestion.reason)
    }

    @Test
    fun withNoHistoryAndNoPlan_thereIsNothingToSuggest() {
        val suggestion = suggestProgression(lastTime = emptyList())

        assertEquals(ProgressionReason.NO_HISTORY, suggestion.reason)
        assertFalse("a screen must be able to tell that it has nothing to say", suggestion.isUsable)
    }

    @Test
    fun repsWithoutAPlan_stillClimb() {
        // Freestyle work has no ceiling to reach, so the app proposes one more rep and stops
        // there: without a target, adding weight would be inventing a programme.
        val suggestion = suggestProgression(lastTime = listOf(did(80_000L, 6)))

        assertEquals(80_000L, suggestion.weightGrams)
        assertEquals(7, suggestion.reps)
        assertTrue(suggestion.isUsable)
    }

    @Test
    fun assistedWork_belowTheCeiling_addsARepBeforeTakingHelpOff() {
        // ROADMAP B26: the assisted branch was tested before the ceiling, so an assisted lifter at
        // the *bottom* of a 6–8 range was told to reduce assistance and keep the reps — "add a rep
        // first" was unreachable for assisted work. The one test sat at the ceiling, so it could
        // not tell the two rules apart; this one sits at the bottom.
        val suggestion = suggestProgression(
            lastTime = listOf(did(0L, 6, assistanceGrams = 20_000L)),
            target = target(minReps = 6, maxReps = 8, assistanceGrams = 20_000L),
        )

        assertEquals("one more rep", 7, suggestion.reps)
        assertEquals("with the same help", 20_000L, suggestion.assistanceGrams)
        assertEquals(ProgressionReason.MORE_REPS, suggestion.reason)
    }
}
