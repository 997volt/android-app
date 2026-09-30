#!/usr/bin/env python3
"""Print the JVM tests' total execution time, as JUnit measured it.

Used by the D1 experiment: `maxParallelForks` changes test execution and nothing else,
so the comparison reads this number rather than the job's wall clock, which is dominated
by setup, SDK downloads and Robolectric's resource merging.
"""
from __future__ import annotations

import pathlib
import re

RESULTS = pathlib.Path("app/build/test-results/testDebugUnitTest")

total = 0.0
for path in RESULTS.rglob("TEST-*.xml"):
    match = re.search(r'<testsuite[^>]*time="([0-9.]+)"', path.read_text())
    if match:
        total += float(match.group(1))

print(f"TEST-SECONDS {total:.1f}")
