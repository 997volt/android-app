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
 *
 * [DOCS] reaches past the repo-root docs into the on-demand skill, because a gate that only
 * guards the always-loaded files is a gate on the files nobody had to be reminded to read.
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
            linkTargets(read(doc))
                .filterNot { resolveRelative(doc, it).exists() }
                .map { "$doc -> $it" }
        }

        assertThat(missing).isEmpty()
    }

    @Test
    fun every_doc_anchor_resolves() {
        val headings = mutableMapOf<String, Set<String>>()
        fun headingsOf(path: String): Set<String>? {
            if (!path.endsWith(".md")) return null
            return headings.getOrPut(relativeToRepo(path)) {
                HEADING.findAll(read(path)).map { slug(it.groupValues[1]) }.toSet()
            }
        }

        val broken = mutableListOf<String>()
        var checked = 0
        DOCS.forEach { doc ->
            ANCHOR.findAll(read(doc)).forEach { match ->
                // A same-document anchor names no path, so it points at `doc` itself.
                val targetDoc = match.groupValues[1].ifEmpty { doc }
                val known = headingsOf(resolveRelative(doc, targetDoc).invariantSeparatorsPath)
                    ?: return@forEach
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

    /**
     * Read a doc by a path that is either repository-relative (`AGENTS.md`, what [DOCS]
     * holds) or canonical (`resolveRelative` hands those out). Idempotent for both, so the
     * two shapes cannot disagree about which file a path names — the first cut of this
     * resolved the absolute form a second time and read
     * `<repo>/home/dev/android-app/DECISIONS.md` instead of the file.
     */
    private fun read(name: String): String {
        val f = if (name.isEmpty()) repoRoot else File(name).let { if (it.isAbsolute) it else File(repoRoot, name) }
        return f.readText()
    }

    /**
     * Resolve a link/anchor target the way a markdown reader does: relative to the
     * directory of the document that contains it, not to the repository root. Only the
     * repo-root docs live in the root, so the root-relative reading worked for them and
     * broke the moment a nested one (`.dsh/skills/.../SKILL.md`, whose README link is
     * `../../../README.md`) joined [DOCS] — the file it names exists, the root-relative
     * reading just looks for it three directories above the repo. The result stays a
     * repository-relative path, so `read`/`exists` keep working unchanged.
     */
    private fun resolveRelative(doc: String, target: String): File {
        // A doc in DOCS always has a directory to resolve against; the repo root has no parent.
        val containing = requireNotNull(File(repoRoot, doc).parentFile) { "$doc has no parent directory" }
        val resolved = containing
            .resolve(target.replace('/', File.separatorChar))
            .canonicalFile
        // A target that climbs out of the repository is a broken link, not a file to read.
        check(resolved.toPath().startsWith(repoRoot.canonicalFile.toPath())) {
            "$doc links outside the repository: $target"
        }
        return resolved
    }

    /**
     * A repository-relative [path], canonicalised so the same file has one cache key.
     * Relative inputs are rooted at [repoRoot] rather than at the process working
     * directory, which is the module directory under Gradle — `Path.relativize` needs two
     * absolute paths and silently produces nonsense when one is not.
     */
    private fun relativeToRepo(path: String): String {
        val root = repoRoot.canonicalFile.toPath()
        val absolute = File(path).let { if (it.isAbsolute) it else File(repoRoot, path) }.canonicalFile
        return root.relativize(absolute.toPath()).toString().replace(File.separatorChar, '/')
    }

    private companion object {
        val DOCS = listOf(
            "AGENTS.md",
            "README.md",
            "DECISIONS.md",
            "DECISIONS-EVIDENCE.md",
            "ROADMAP.md",
            "CHANGELOG.md",
            "RELEASING.md",
            // Loaded on demand, so its links and anchors rot with nothing to notice. Nested,
            // which is what resolveRelative exists for.
            ".dsh/skills/android-device-loop/SKILL.md",
        )
        val WHITESPACE = Regex("\\s+")
        val LINK = Regex("""\]\(([^)]+)\)""")
        val HEADING = Regex("^#{1,6} (.+)$", RegexOption.MULTILINE)
        val ANCHOR = Regex("""\]\(([^)#\s]*)#([A-Za-z0-9_-]+)\)""")
    }
}
