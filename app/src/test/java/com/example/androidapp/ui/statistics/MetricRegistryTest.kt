package com.example.androidapp.ui.statistics

import org.junit.Assert.assertNull
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.domain.model.TrendMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry covers every series, and says the truth about each (ROADMAP N35).
 *
 * The coverage assertions matter more than they look: the whole point of the registry is that "everything"
 * is one picker, and a series missing from it is a series the screen silently cannot show — the kind of gap
 * nobody reports because nothing looks broken.
 */
class MetricRegistryTest {

    private val entries = MetricRegistry.entries

    @Test
    fun everySeriesIsRegistered_andNothingElse() {
        TrendMetric.entries.forEach { metric ->
            assertEquals(
                "the workout series are all here",
                1,
                entries.count { it.key == MetricKey.Workout(metric) },
            )
        }
        ExerciseTrendMetric.entries.forEach { metric ->
            assertEquals(
                "and so are the exercise ones",
                1,
                entries.count { it.key == MetricKey.Exercise(metric) },
            )
        }
        BodyMetric.entries.forEach { metric ->
            assertEquals(1, entries.count { it.key == MetricKey.Body(metric) })
        }
        TapeSite.entries.forEach { site ->
            assertEquals("every tape site is a series", 1, entries.count { it.key == MetricKey.Tape(site) })
        }

        // Three ratings, eight exercise metrics, three body metrics and seven tape sites.
        assertEquals(21, entries.size)
    }

    @Test
    fun everyIdIsUnique_andRoundTripsThroughTheRegistry() {
        // The ids are what a test tag, a stored preference and the picker all agree on, so a duplicate or a
        // missed round trip would show one metric while claiming another.
        val ids = entries.map { it.key.id }

        assertEquals("no two series share an id", ids.size, ids.distinct().size)
        entries.forEach { entry ->
            assertEquals(entry, MetricRegistry.byId(entry.key.id))
        }
        assertNull("an unknown id is nothing, not a default", MetricRegistry.byId("WORKOUT:NOPE"))
        assertNull(MetricRegistry.byId(null))
    }

    @Test
    fun everyEntryCanBeFoundByItsKey() {
        entries.forEach { entry ->
            assertEquals(entry, MetricRegistry.entryFor(entry.key))
        }
    }

    @Test
    fun withinAGroup_theLabelsAreDistinct() {
        // The three rating strings are shared on purpose — RPE means the same thing at both levels — so
        // the invariant is per group, which is how the picker shows them.
        MetricGroup.entries.forEach { group ->
            val labels = entries.filter { it.group == group }.map { it.labelRes }
            assertEquals(
                "two series in $group share a label, so one of them is unreadable",
                labels.size,
                labels.distinct().size,
            )
        }
    }

    @Test
    fun onlyAssistanceAndPain_areBetterWhenLower() {
        val lower = entries.filterNot { it.higherIsBetter }.map { it.key }.toSet()

        assertEquals(
            "assistance and joint pain are the two where less is better",
            setOf(
                MetricKey.Exercise(ExerciseTrendMetric.ASSISTANCE),
                MetricKey.Workout(TrendMetric.JOINT_PAIN),
                MetricKey.Exercise(ExerciseTrendMetric.JOINT_PAIN),
            ),
            lower,
        )
    }

    @Test
    fun countLikeSeriesAreBars_andTheRestAreLines() {
        val bars = entries.filter { it.isBars }.map { it.key }.toSet()

        assertEquals(
            "a quantity is bars; a position on a scale is a line",
            setOf(
                MetricKey.Exercise(ExerciseTrendMetric.VOLUME),
                MetricKey.Exercise(ExerciseTrendMetric.TOTAL_REPS),
            ),
            bars,
        )
    }

    @Test
    fun loadsStartAtZero_andRatingsAndBodyReadingsDoNot() {
        // A weight axis that started at the lightest set would exaggerate every change; a weight of zero
        // is a real weight, which is why a load is drawn from it.
        ExerciseTrendMetric.entries.filter { it.isLoad }.forEach { metric ->
            assertTrue("$metric is a load", MetricRegistry.entryFor(MetricKey.Exercise(metric)).fromZero)
        }
        assertFalse(MetricRegistry.entryFor(MetricKey.Workout(TrendMetric.RPE)).fromZero)
        assertFalse(MetricRegistry.entryFor(MetricKey.Body(BodyMetric.WEIGHT)).fromZero)
    }

    @Test
    fun onlyTheExerciseGroupNeedsALiftChosen() {
        entries.forEach { entry ->
            assertEquals(
                "${entry.key} chooses for itself whether a lift is needed",
                entry.group == MetricGroup.EXERCISE,
                entry.needsExercise,
            )
        }
    }
}
