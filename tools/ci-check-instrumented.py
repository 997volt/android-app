#!/usr/bin/env python3
"""Fail when the instrumented run executed fewer tests than the source declares.

ROADMAP B8. The emulator job reported green while its result XML recorded well under
half of the declared tests: the whole `data/` suite and the two newest migration tests
were absent. Nothing in the build noticed, because a JUnit report with no failures looks
exactly like a complete one.

The count is what closes that hole. This script counts the `@Test` annotations in
`app/src/androidTest`, reads the executed total out of the JUnit XML Gradle wrote, and
fails if the second is smaller than the first — printing which classes did not report,
since "62 of 127" is not actionable on its own.

It also fails when the results file predates the run, because a Gradle task that is
UP-TO-DATE replays the previous XML: that is how the green artifact with 62 tests was
produced in the first place.
"""
from __future__ import annotations

import pathlib
import re
import sys

ANDROID_TEST = pathlib.Path("app/src/androidTest")
RESULTS = pathlib.Path("app/build/outputs/androidTest-results")


def declared_tests() -> dict[str, int]:
    """Every `@Test` in the instrumented sources, per class file."""
    declared: dict[str, int] = {}
    for path in sorted(ANDROID_TEST.rglob("*.kt")):
        count = len(re.findall(r"^\s*@Test\b", path.read_text(), flags=re.MULTILINE))
        if count:
            declared[path.stem] = count
    return declared


def executed_tests() -> tuple[int, set[str], float]:
    """The XML's total, the classes that reported, and the newest report mtime."""
    total = 0
    reported: set[str] = set()
    newest = 0.0
    for path in RESULTS.rglob("TEST-*.xml"):
        text = path.read_text()
        newest = max(newest, path.stat().st_mtime)
        for match in re.finditer(r'<testsuite name="([^"]+)"[^>]*tests="(\d+)"', text):
            reported.add(match.group(1).split(".")[-1])
            total += int(match.group(2))
    return total, reported, newest


def main() -> int:
    declared = declared_tests()
    declared_total = sum(declared.values())
    if declared_total == 0:
        print("no instrumented tests declared — the source layout moved?", file=sys.stderr)
        return 1

    total, reported, newest = executed_tests()
    if total == 0:
        print(
            "no instrumented results found: the run produced no JUnit XML at all",
            file=sys.stderr,
        )
        return 1

    missing = {name: count for name, count in declared.items() if name not in reported}
    print(f"declared {declared_total} instrumented tests in {len(declared)} classes")
    print(f"executed {total} across {len(reported)} classes")
    if missing:
        print("\nclasses that declared tests but reported nothing:")
        for name, count in sorted(missing.items()):
            print(f"  {name}: {count} declared")

    # A results file older than the checkout cannot be this run's. Gradle's own
    # up-to-date check is the likeliest reason, and it is invisible in the log.
    build_file = ANDROID_TEST.stat().st_mtime
    if newest < build_file:
        print(
            "\nthe result XML is older than the test sources: this run replayed a "
            "previous report instead of executing the tests",
            file=sys.stderr,
        )
        return 1

    if total < declared_total:
        print(
            f"\nFAIL: {declared_total - total} declared tests did not execute. "
            "A green job that tested part of the suite is worth less than a red one.",
            file=sys.stderr,
        )
        return 1

    print("\nOK: every declared instrumented test executed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
