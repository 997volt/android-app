package com.example.androidapp.ui.statistics

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.example.androidapp.domain.model.ExerciseTrendMetric
import com.example.androidapp.domain.model.TapeSite
import com.example.androidapp.domain.model.TrendMetric
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
            assertWithMessage("the workout series are all here")
                .that(entries.count { it.key == MetricKey.Workout(metric) })
                .isEqualTo(1)
        }
        ExerciseTrendMetric.entries.forEach { metric ->
            assertWithMessage("and so are the exercise ones")
                .that(entries.count { it.key == MetricKey.Exercise(metric) })
                .isEqualTo(1)
        }
        BodyMetric.entries.forEach { metric ->
            assertThat(entries.count { it.key == MetricKey.Body(metric) }).isEqualTo(1)
        }
        TapeSite.entries.forEach { site ->
            assertWithMessage("every tape site is a series").that(entries.count { it.key == MetricKey.Tape(site) })
                .isEqualTo(1)
        }

        // Three ratings, eight exercise metrics, three body metrics and seven tape sites.
        assertThat(entries.size).isEqualTo(21)
    }

    @Test
    fun everyIdIsUnique_andRoundTripsThroughTheRegistry() {
        // The ids are what a test tag, a stored preference and the picker all agree on, so a duplicate or a
        // missed round trip would show one metric while claiming another.
        val ids = entries.map { it.key.id }

        assertWithMessage("no two series share an id").that(ids.distinct().size).isEqualTo(ids.size)
        entries.forEach { entry ->
            assertThat(MetricRegistry.byId(entry.key.id)).isEqualTo(entry)
        }
        assertWithMessage("an unknown id is nothing, not a default").that(MetricRegistry.byId("WORKOUT:NOPE")).isNull()
        assertThat(MetricRegistry.byId(null)).isNull()
    }

    @Test
    fun parsingIsTheInverseOfFormatting() {
        // A target is typed in the unit on screen and stored in the unit on disk, so the two have to agree:
        // 80 kilograms is 80000 grams, which is what every other weight in the app is.
        assertThat(MetricUnit.KILOGRAMS.parse("80")).isEqualTo(80_000.0)
        assertThat(MetricUnit.CENTIMETRES.parse("82.5")).isEqualTo(825.0)
        assertThat(MetricUnit.PERCENT.parse("18")).isEqualTo(180.0)
        assertThat(MetricUnit.RATING.parse("7.5")).isEqualTo(15.0)
        assertThat(MetricUnit.REPS.parse("5")).isEqualTo(5.0)
    }

    @Test
    fun somethingThatIsNotANumber_parsesToNothing() {
        // Null rather than zero: a typo must not become a target of zero.
        assertThat(MetricUnit.KILOGRAMS.parse("eighty")).isNull()
        assertThat(MetricUnit.KILOGRAMS.parse("")).isNull()
    }

    @Test
    fun everyUnitSaysWhatItIs() {
        // A rate without a unit is an ambiguous number, which is how "−0.483 per week" shipped: the slope was
        // formatted correctly and the thing it was a slope *of* was missing.
        MetricUnit.entries.forEach { unit ->
            assertWithMessage("$unit has no label").that(unit.labelRes != 0).isTrue()
        }
        assertThat(MetricUnit.entries.map { it.labelRes }.distinct().size).isEqualTo(MetricUnit.entries.size)
    }

    @Test
    fun everyEntryCanBeFoundByItsKey() {
        entries.forEach { entry ->
            assertThat(MetricRegistry.entryFor(entry.key)).isEqualTo(entry)
        }
    }

    @Test
    fun withinAGroup_theLabelsAreDistinct() {
        // The three rating strings are shared on purpose — RPE means the same thing at both levels — so
        // the invariant is per group, which is how the picker shows them.
        MetricGroup.entries.forEach { group ->
            val labels = entries.filter { it.group == group }.map { it.labelRes }
            assertWithMessage("two series in $group share a label, so one of them is unreadable")
                .that(labels.distinct().size)
                .isEqualTo(labels.size)
        }
    }

    @Test
    fun onlyAssistanceAndPain_areBetterWhenLower() {
        val lower = entries.filterNot { it.higherIsBetter }.map { it.key }.toSet()

        assertWithMessage("assistance and joint pain are the two where less is better").that(lower).isEqualTo(setOf(
                MetricKey.Exercise(ExerciseTrendMetric.ASSISTANCE),
                MetricKey.Workout(TrendMetric.JOINT_PAIN),
                MetricKey.Exercise(ExerciseTrendMetric.JOINT_PAIN),
            ))
    }

    @Test
    fun countLikeSeriesAreBars_andTheRestAreLines() {
        val bars = entries.filter { it.isBars }.map { it.key }.toSet()

        assertWithMessage("a quantity is bars; a position on a scale is a line").that(bars).isEqualTo(setOf(
                MetricKey.Exercise(ExerciseTrendMetric.VOLUME),
                MetricKey.Exercise(ExerciseTrendMetric.TOTAL_REPS),
            ))
    }

    @Test
    fun loadsStartAtZero_andRatingsAndBodyReadingsDoNot() {
        // A weight axis that started at the lightest set would exaggerate every change; a weight of zero
        // is a real weight, which is why a load is drawn from it.
        ExerciseTrendMetric.entries.filter { it.isLoad }.forEach { metric ->
            assertWithMessage("$metric is a load").that(MetricRegistry.entryFor(MetricKey.Exercise(metric)).fromZero)
                .isTrue()
        }
        assertThat(MetricRegistry.entryFor(MetricKey.Workout(TrendMetric.RPE)).fromZero).isFalse()
        assertThat(MetricRegistry.entryFor(MetricKey.Body(BodyMetric.WEIGHT)).fromZero).isFalse()
    }

    @Test
    fun onlyTheExerciseGroupNeedsALiftChosen() {
        entries.forEach { entry ->
            assertWithMessage("${entry.key} chooses for itself whether a lift is needed").that(entry.needsExercise)
                .isEqualTo(entry.group == MetricGroup.EXERCISE)
        }
    }
}
