package com.example.androidapp

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The gate on the documentation itself (no feature id — no feature changed).
 *
 * Nothing else checks prose, and two claims in it have already gone stale once: the task
 * set AGENTS.md tells an agent to run, and the paths the docs link. A green build says
 * nothing about either, so they are asserted rather than trusted — the same reasoning as
 * `tools/ci-check-instrumented.py`, which exists because a report with no failures looks
 * exactly like a complete one.
 */
class DocsConsistencyTest {

    @Test
    fun the_agents_gate_set_is_the_task_set_ci_builds() {
        val agents = read("AGENTS.md")
            .substringAfter("## Before you call it done")
            .substringBefore("## Hard constraints")
        val ciBuildJob = read(".github/workflows/android.yml")
            .substringAfter("\n  build:")
            .substringBefore("\n  instrumented:")

        val agentsTasks = gradleTasks(agents)
        val ciTasks = gradleTasks(ciBuildJob)

        // An empty list on both sides would compare equal, which is how a parser that
        // quietly stopped matching would pass this test.
        assertThat(agentsTasks).isNotEmpty()
        assertThat(ciTasks).isNotEmpty()
        assertThat(agentsTasks).containsExactlyElementsIn(ciTasks)
    }

    @Test
    fun every_path_the_docs_link_resolves() {
        val missing = listOf("AGENTS.md", "README.md").flatMap { doc ->
            linkTargets(read(doc)).filterNot { File(root, it).exists() }.map { "$doc -> $it" }
        }

        assertThat(missing).isEmpty()
    }

    /**
     * The task names of the first `./gradlew` invocation in [text], stopping at the first
     * flag. Flags are deliberately not compared: CI names `--configuration-cache`
     * explicitly while the local gate relies on `gradle.properties`, so the task set is
     * the invariant rather than the whole command line.
     */
    private fun gradleTasks(text: String): List<String> {
        val lines = text.lineSequence().map(String::trim).toList()
        val start = lines.indexOfFirst { it == "./gradlew" || it.startsWith("./gradlew ") }
        check(start >= 0) { "No ./gradlew invocation found in:\n$text" }

        return lines.drop(start)
            .flatMap { it.split(WHITESPACE) }
            .filter(String::isNotEmpty)
            .takeWhile { !it.startsWith("--") }
            .filter { it != "./gradlew" }
    }

    private fun linkTargets(markdown: String): List<String> =
        LINK.findAll(markdown)
            .map { it.groupValues[1].trim().substringBefore('#') }
            .filter { it.isNotEmpty() && !it.startsWith("http") && !it.startsWith("mailto:") }
            .toList()

    private fun read(name: String): String = File(root, name).readText()

    private val root: File by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return@lazy dir
            dir = dir.parentFile
        }
        error("No settings.gradle.kts above ${System.getProperty("user.dir")}")
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val LINK = Regex("""\]\(([^)]+)\)""")
    }
}
