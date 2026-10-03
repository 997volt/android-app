package com.example.androidapp

import java.io.File

/**
 * The project's own files, for the tests that assert on them rather than on behaviour.
 *
 * Two tests now read the source tree — the documentation gate and D4's tag coverage — and both
 * need the same walk up to `settings.gradle.kts`, because Gradle runs tests from the module
 * directory rather than from the repository root.
 */
internal val repoRoot: File by lazy {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
        if (File(dir, "settings.gradle.kts").isFile) return@lazy dir
        dir = dir.parentFile
    }
    error("No settings.gradle.kts above ${System.getProperty("user.dir")}")
}

/** Every `.kt` file under the given repository-relative directories. */
internal fun kotlinSources(vararg relativeDirs: String): List<File> =
    relativeDirs.flatMap { dir ->
        File(repoRoot, dir).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }
