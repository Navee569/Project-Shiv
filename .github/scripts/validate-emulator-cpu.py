#!/usr/bin/env python3
"""Validate independently measured emulator CPU evidence without INI coercion."""
import pathlib
import re
import sys


def expect(label, observed, expected):
    if observed != expected:
        raise ValueError(f"{label}: expected {expected!r}, observed {observed!r}")


def validate(report):
    evidence = (report / "effective-cpu-evidence.txt").read_text()
    configs = re.findall(r"^--- (.+)$", evidence, re.M)
    if len(configs) != 1:
        raise ValueError(f"canonical AVD configs: expected exactly one, observed {configs!r}")
    expect("requested cores", re.findall(r"^requested_cores=(.*)$", evidence, re.M), ["4"])
    # Read the retained canonical file rather than matching unbounded diagnostic text.
    values = []
    for line in (report / "canonical-avd-config.ini").read_text().splitlines():
        key, separator, value = line.partition("=")
        if separator and key.strip() == "hw.cpu.ncore":
            values.append(value.strip())
    expect("hw.cpu.ncore (one exact key)", values, ["4"])
    expect("guest online CPU mask", (report / "cpu-online-mask.txt").read_text().strip(), "0-3")
    expect("guest online CPU count", (report / "cpu-online-count.txt").read_text().strip(), "4")
    processors = re.findall(r"^processor\s*:\s*(\d+)\s*$", (report / "cpu-processors.txt").read_text(), re.M)
    expect("guest processor IDs", processors, ["0", "1", "2", "3"])


if __name__ == "__main__":
    report = pathlib.Path(sys.argv[1])
    try:
        validate(report)
    except (ValueError, OSError) as failure:
        print(f"CPU_ASSERTIONS=FAIL {failure}", file=sys.stderr)
        sys.exit(1)
    with (report / "effective-cpu-evidence.txt").open("a") as output:
        output.write("\nCPU_ASSERTIONS=PASS requested=4 configured=4 mask=0-3 online=4 processors=0,1,2,3\n")
    print("CPU_ASSERTIONS=PASS")
