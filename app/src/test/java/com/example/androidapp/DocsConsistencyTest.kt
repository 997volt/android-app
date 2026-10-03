package com.example.androidapp

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The gate on the documentation itself (no feature id — no feature changed).
 *
 * Nothing else checks prose, and three claims in it have already gone stale: the task set
 * AGENTS.md tells an agent to run, the paths the docs link, and an evidence anchor pointing
 * at a heading that did not exist. A green build says nothing about any of them, so they are
 * asserted rather than trusted — the same reasoning as `tools/ci-check-instrumented.py`,
 * which exists because a report with no failures looks exactly like a complete one.
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
        val missing = DOCS.flatMap { doc ->
            linkTargets(read(doc)).filterNot { File(repoRoot, it).exists() }.map { "$doc -> $it" }
        }

        assertThat(missing).isEmpty()
    }

    @Test
    fun every_doc_anchor_resolves() {
        val headings = mutableMapOf<String, Set<String>>()
        fun headingsOf(path: String): Set<String>? {
            if (path.isNotEmpty() && !path.endsWith(".md")) return null
            return headings.getOrPut(path) {
                HEADING.findAll(read(path)).map { slug(it.groupValues[1]) }.toSet()
            }
        }

        val broken = mutableListOf<String>()
        var checked = 0
        DOCS.forEach { doc ->
            ANCHOR.findAll(read(doc)).forEach { match ->
                val targetDoc = match.groupValues[1].ifEmpty { doc }
                val known = headingsOf(targetDoc) ?: return@forEach
                val fragment = match.groupValues[2]
                checked++
                if (fragment !in known) broken += "$doc -> $targetDoc#$fragment"
            }
        }

        // A regex that quietly stopped matching would check nothing and pass.
        assertThat(checked).isGreaterThan(0)
        assertThat(broken).isEmpty()
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

    /** GitHub's heading anchor: lowercased, punctuation dropped, spaces to hyphens. */
    private fun slug(heading: String): String =
        heading.lowercase()
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
            .replace(' ', '-')

    private fun read(name: String): String = File(repoRoot, name).readText()

    private companion object {
        val DOCS = listOf(
            "AGENTS.md",
            "README.md",
            "DECISIONS.md",
            "DECISIONS-EVIDENCE.md",
            "ROADMAP.md",
            "CHANGELOG.md",
            "RELEASING.md",
        )
        val WHITESPACE = Regex("\\s+")
        val LINK = Regex("""\]\(([^)]+)\)""")
        val HEADING = Regex("^#{1,6} (.+)$", RegexOption.MULTILINE)
        val ANCHOR = Regex("""\]\(([^)#\s]*)#([A-Za-z0-9_-]+)\)""")
    }
}
