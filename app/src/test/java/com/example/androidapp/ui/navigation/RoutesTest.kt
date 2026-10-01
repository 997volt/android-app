package com.example.androidapp.ui.navigation

import kotlinx.serialization.serializer
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every navigation route must be serialisable (ROADMAP N21's lesson).
 *
 * Type-safe navigation serialises the route object, and a route missing `@Serializable` is
 * **not** a compile error: it compiles, then throws `SerializationException` the first time
 * the destination is reached. That is how it got as far as a device — the crash that
 * verification caught, with `Settings` as the culprit.
 *
 * This asserts the property for every route at once, so adding one without the annotation
 * fails here rather than on someone's phone.
 */
class RoutesTest {

    @Test
    fun everyRoute_hasASerializer() {
        val routes = mapOf(
            "WorkoutsHome" to serializer<WorkoutsHome>().descriptor.serialName,
            "ExerciseLibrary" to serializer<ExerciseLibrary>().descriptor.serialName,
            "ExerciseDetail" to serializer<ExerciseDetail>().descriptor.serialName,
            "ExerciseTrends" to serializer<ExerciseTrends>().descriptor.serialName,
            "Settings" to serializer<Settings>().descriptor.serialName,
            "ActiveWorkout" to serializer<ActiveWorkout>().descriptor.serialName,
            "ExercisePicker" to serializer<ExercisePicker>().descriptor.serialName,
            "WorkoutTrends" to serializer<WorkoutTrends>().descriptor.serialName,
            "WorkoutTemplates" to serializer<WorkoutTemplates>().descriptor.serialName,
            "TemplateEditor" to serializer<TemplateEditor>().descriptor.serialName,
            "WorkoutHistory" to serializer<WorkoutHistory>().descriptor.serialName,
            "WorkoutDetail" to serializer<WorkoutDetail>().descriptor.serialName,
        )

        // The serial name is the qualified class name, so the assertion is that the route
        // resolves to a serializer at all — which is what a missing `@Serializable` breaks.
        routes.forEach { (name, serialName) ->
            assertTrue("$name must serialise, got '$serialName'", serialName.endsWith(name))
        }
    }
}
