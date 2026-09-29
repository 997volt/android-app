#!/usr/bin/env python3
"""Print failing tests as Markdown, for the GitHub Actions job summary.

F18. Gradle reports `There were failing tests` and nothing else with
`--console=plain`; the names live only in the JUnit XML. Twice now that meant
reading a red X with no idea which test failed, once while the logs and artifacts
were both inaccessible. This puts the names where they can be seen: the job
summary, and the log.

Usage:  python3 tools/ci-summarise-failures.py [results-dir ...]
Defaults to the unit-test and instrumented-test result directories.
"""
from __future__ import annotations

import glob
import os
import sys
import xml.etree.ElementTree as ET

DEFAULT_ROOTS = [
    "app/build/test-results",
    "app/build/outputs/androidTest-results",
]


def failing(path: str) -> list[str]:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return []
    names = []
    for case in root.iter("testcase"):
        if list(case.iter("failure")) or list(case.iter("error")):
            klass = case.get("classname", "?")
            names.append(f"{klass}.{case.get('name')}")
    return names


def main(argv: list[str]) -> int:
    roots = argv[1:] or DEFAULT_ROOTS
    found: list[tuple[str, str]] = []
    scanned = 0

    for root in roots:
        for path in sorted(glob.glob(os.path.join(root, "**", "*.xml"), recursive=True)):
            scanned += 1
            for name in failing(path):
                found.append((path, name))

    print("### Failing tests")
    if found:
        for _, name in sorted(set(found)):
            print(f"- `{name}`")
    elif scanned:
        print(f"- No failures in {scanned} result file(s).")
    else:
        print("- No JUnit XML found — the failure was probably earlier than the tests.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
