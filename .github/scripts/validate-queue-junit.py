#!/usr/bin/env python3
"""Strict validator for one filtered PrioritizedInferenceQueueTest JUnit report."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

EXPECTED_CLASS = "app.shiv.PrioritizedInferenceQueueTest"


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: validate-queue-junit.py RESULT.xml", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    if not path.is_file() or path.stat().st_size == 0:
        print(f"missing or empty JUnit XML: {path}", file=sys.stderr)
        return 1
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        print(f"invalid JUnit XML: {exc}", file=sys.stderr)
        return 1

    suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
    cases = list(root.iter("testcase"))
    class_names = {suite.attrib.get("name", "") for suite in suites}
    class_names.update(case.attrib.get("classname", "") for case in cases)
    if EXPECTED_CLASS not in class_names:
        print(f"wrong or missing test class; expected {EXPECTED_CLASS}, found {sorted(n for n in class_names if n)}", file=sys.stderr)
        return 1

    def count(name: str) -> int:
        if root.tag == "testsuite":
            return int(root.attrib.get(name, "0"))
        return sum(int(suite.attrib.get(name, "0")) for suite in suites)

    tests = count("tests")
    failures = count("failures")
    errors = count("errors")
    skipped = count("skipped")
    print(f"tests={tests}")
    print(f"failures={failures}")
    print(f"errors={errors}")
    print(f"skipped={skipped}")
    if (tests, failures, errors, skipped) != (10, 0, 0, 0):
        print("JUnit acceptance requires tests=10, failures=0, errors=0, skipped=0", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
