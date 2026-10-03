package com.example.androidapp

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * D4's gate: every tag applied in production has a test that asserts on it, or is on
 * [DEFERRED] — a list that may only shrink.
 *
 * DECISIONS.md's rule used to rest on trust, and the backlog grew while it did. This makes the
 * rule mechanical in both directions: a new tag without its test fails here, and paying a tag
 * off fails here until its name leaves the list, so the list tracks reality instead of
 * remembering it.
 *
 * The limit is deliberate: a test that asserts on an English literal rather than the tag is
 * invisible to this scan. That is the rule the tags exist to keep, and a gate that noticed it
 * would be reading the test's mind.
 */
class TestTagCoverageTest {

    @Test
    fun everyProductionTag_isAssertedOrDeferred() {
        val unasserted = tagNames().filter { name ->
            productionSources.any { it.contains(nameMatch(name)) } &&
                testSources.none { it.contains(nameMatch(name)) }
        }

        assertThat(unasserted).containsExactlyElementsIn(DEFERRED)
    }

    private fun tagNames(): List<String> {
        val text = File(repoRoot, TAG_FILE).readText()
        return (CONST.findAll(text).map { it.groupValues[1] } +
            FUN.findAll(text).map { it.groupValues[1] })
            .distinct()
            .sorted()
            .toList()
    }

    private val productionSources: List<String> by lazy {
        kotlinSources("app/src/main")
            .filterNot { it.name == File(TAG_FILE).name }
            .map { stripComments(it.readText()) }
    }

    private val testSources: List<String> by lazy {
        kotlinSources("app/src/test", "app/src/androidTest")
            // This file names every deferred tag, which would otherwise read as the test that
            // asserts on it — the gate answering its own question.
            .filterNot { it.name == "${this::class.java.simpleName}.kt" }
            .map { stripComments(it.readText()) }
    }

    private companion object {
        const val TAG_FILE = "app/src/main/java/com/example/androidapp/ui/components/TestTags.kt"

        val CONST = Regex("""const val ([A-Za-z0-9_]+)""")
        val FUN = Regex("""fun ([A-Za-z0-9_]+)\s*\(""")

        fun nameMatch(name: String) = Regex("""\b${Regex.escape(name)}\b""")

        /**
         * Comments are stripped before counting: a KDoc that mentions a tag's name is not a
         * control that applies it, and counting prose would let a tag look asserted because
         * someone wrote about it.
         */
        fun stripComments(text: String): String =
            text.replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                .replace(Regex("""//[^\n]*"""), " ")

        /**
         * Tags applied in production with no test asserting them (D4).
         *
         * **This list may only shrink.** Taking a name off it is the last step of writing the
         * test that pays it off; adding a name is exactly the finding this gate exists to raise.
         */
        val DEFERRED = listOf(
            "EXERCISE_EDIT_EQUIPMENT",
            "EXERCISE_EDIT_PATTERN",
            "EXERCISE_TRENDS",
            "GOAL",
            "HOME_CLEAR_DATA",
            "HOME_NO_RECENT",
            "HOME_TITLE",
            "LIBRARY_SEARCH_FIELD",
            "READINESS_ROW",
            "SETTINGS_KEEP_SCREEN_ON",
            "SETTINGS_REST_CUE",
            "SETTINGS_REST_CURRENT",
            "SETTINGS_SCREEN",
            "SET_CANCEL",
            "SET_INCREASE_REPS",
            "SUMMARY_DIALOG",
            "TAB_BAR",
            "TEMPLATES_TITLE",
            "TEMPLATE_CUE_FIELD",
            "TEMPLATE_EDIT_TITLE",
            "TEMPLATE_PLAN_ADD",
            "TEMPLATE_PLAN_CLOSE",
            "TEMPLATE_PLAN_DUPLICATE",
            "TEMPLATE_PLAN_EMPTY",
            "TEMPLATE_PLAN_ROW",
            "TEMPLATE_REST_CUE_SAVE",
            "TEMPLATE_REST_FIELD",
            "TEMPLATE_SET_CANCEL",
            "templateExerciseRow",
            "templatePlanRemove",
            "templatePlanSet",
        )
    }
}
