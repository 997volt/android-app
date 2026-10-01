package com.example.androidapp.ui.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every route is registered, and every registration has a route (ROADMAP B22).
 *
 * Two bugs shipped from the space this covers. A route missing `@Serializable` compiles and then
 * throws `SerializationException` the first time it is reached, and a route with no
 * `composable<...>` registration compiles and then throws *"Destination with route X cannot be
 * found"* — which is exactly what the settings screen did on its first run. A missing annotation
 * is now caught by asking each route for its serializer; a missing registration cannot be, because
 * nothing in the app enumerates the routes it can navigate to.
 *
 * So this reads both files. The list is **derived, never written down**: adding a route to
 * `Routes.kt` without registering it fails here, which the previous version of this test could
 * not do — it named twelve routes by hand, so a thirteenth was invisible to it.
 *
 * Reading source from a test is unusual and deliberate: the alternative is a navigation graph
 * built under Hilt, which this project has no test harness for, and the invariant being protected
 * is a textual one — that a declaration and its registration were both edited.
 */
class RoutesTest {

    @Test
    fun everyRoute_isRegisteredInTheGraph() {
        val declared = routeNames()
        assertTrue("the parser found routes at all", declared.size >= 10)

        val registered = registeredRouteNames()

        assertEquals(
            "a route declared but never registered crashes when it is navigated to",
            emptySet<String>(),
            declared - registered,
        )
        assertEquals(
            "a registration for a route that no longer exists is dead code",
            emptySet<String>(),
            registered - declared,
        )
    }

    /** The route types `Routes.kt` declares, read from the file. */
    private fun routeNames(): Set<String> {
        val source = File(ROUTES).readText()
        return Regex("""data (?:object|class) (\w+)""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSet()
    }

    /** The types `AppNavHost.kt` registers through `composable<...>`. */
    private fun registeredRouteNames(): Set<String> {
        val source = File(NAV_HOST).readText()
        return Regex("""composable<(\w+)>""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSet()
    }

    /**
     * Paths relative to the module, which is the working directory a JVM test runs in. A
     * restructure to a different module would fail loudly here rather than pass silently.
     */
    private companion object {
        const val ROUTES = "src/main/java/com/example/androidapp/ui/navigation/Routes.kt"
        const val NAV_HOST = "src/main/java/com/example/androidapp/ui/navigation/AppNavHost.kt"
    }
}
