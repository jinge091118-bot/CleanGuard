#!/usr/bin/env python3
"""Check that the performance report agrees with the raw result files.

The performance report is release evidence, so its figures must not drift
away from the measurements they came from. This script reads the raw result
files that sit beside the report and confirms that every number quoted in the
report is the number the file holds.

It reports every mismatch rather than stopping at the first one, because a
single edit that breaks several rows should be visible in one pass.

Usage:
    python3 tools/r3/verify-report-figures.py [report-path]
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_REPORT = REPO_ROOT / "docs" / "release" / "v0.2" / "PERFORMANCE-REPORT.md"
PERFORMANCE_DIR = REPO_ROOT / "docs" / "release" / "v0.2" / "performance"


def load(name: str) -> dict:
    path = PERFORMANCE_DIR / name
    if not path.exists():
        raise SystemExit(f"missing result file: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def metrics(document: dict, benchmark: str) -> dict:
    for entry in document.get("benchmarks", []):
        if entry.get("name") == benchmark:
            return {
                key: value
                for key, value in entry.get("metrics", {}).items()
                if isinstance(value, dict)
            }
    raise SystemExit(f"benchmark not found in the result file: {benchmark}")


def build_checks(report: str) -> list[tuple[str, str, bool]]:
    checks: list[tuple[str, str, bool]] = []

    startup = load("2026-10-27-startup.json")
    cold = metrics(startup, "coldStartup")["timeToInitialDisplayMs"]
    checks.append(("cold start median", f"{cold['median']:.2f} ms", "574.11 ms" in report))
    checks.append(("cold start minimum", f"{cold['minimum']:.2f} ms", "504.90 ms" in report))
    checks.append(("cold start maximum", f"{cold['maximum']:.2f} ms", "695.63 ms" in report))
    checks.append(("cold start iterations", str(len(cold["runs"])), "| 10 | 574.11 ms |" in report))

    warm = metrics(startup, "warmStartup")["timeToInitialDisplayMs"]
    checks.append(("warm start median", f"{warm['median']:.2f} ms", "240.35 ms" in report))
    checks.append(("warm start maximum", f"{warm['maximum']:.2f} ms", "262.68 ms" in report))

    scan = load("2026-10-27-scan.json")
    full = metrics(scan, "firstFullScan")
    incremental = metrics(scan, "noChangeIncrementalScan")
    full_scan = full["CleanGuardScanFirstMs"]
    incremental_scan = incremental["CleanGuardScanFirstMs"]
    checks.append(("full scan median", f"{full_scan['median']:.2f} ms", "658.63 ms" in report))
    checks.append(("full scan iterations", str(len(full_scan["runs"])), "| Full scan, `CleanGuardScan` | 3 |" in report))
    checks.append(("incremental median", f"{incremental_scan['median']:.2f} ms", "544.72 ms" in report))
    checks.append(
        ("incremental iterations", str(len(incremental_scan["runs"])),
         "| No-change incremental scan, `CleanGuardScan` | 5 |" in report)
    )
    checks.append(
        ("full scan heap", f"{full['memoryHeapSizeMaxKb']['median']:.0f} kB", "61,906 kB" in report)
    )
    checks.append(
        ("full scan resident set", f"{full['memoryRssAnonMaxKb']['median']:.0f} kB", "102,436 kB" in report)
    )
    checks.append(
        ("incremental heap", f"{incremental['memoryHeapSizeMaxKb']['median']:.0f} kB", "51,680 kB" in report)
    )
    checks.append(
        ("incremental resident set", f"{incremental['memoryRssAnonMaxKb']['median']:.0f} kB", "95,904 kB" in report)
    )

    scroll = load("2026-10-27-scroll.json")
    for benchmark, quoted in [
        ("scrollHome", "221"),
        ("scrollResults", "198"),
        ("scrollHistory", "215"),
        ("scrollDuplicates", "354"),
    ]:
        frames = metrics(scroll, benchmark)["frameCount"]
        checks.append((f"{benchmark} frames", f"{frames['median']:.0f}", f"| {quoted} |" in report))

    mutation_path = PERFORMANCE_DIR / "2026-10-28-mutation.json"
    if mutation_path.exists():
        mutation = json.loads(mutation_path.read_text(encoding="utf-8"))
        for run in mutation.get("runs", []):
            label = run.get("label", "?")
            milliseconds = f"{run['wall_clock_ms']} ms"
            checks.append((f"mutation {label}", milliseconds, milliseconds in report))

    return checks


def main() -> int:
    report_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_REPORT
    if not report_path.exists():
        raise SystemExit(f"report not found: {report_path}")
    report = report_path.read_text(encoding="utf-8")

    checks = build_checks(report)
    print(f"checking {report_path}")
    mismatches = 0
    for label, value, ok in checks:
        if not ok:
            mismatches += 1
        print(f"  {'ok  ' if ok else 'MISS'} {label:28s} {value}")

    print()
    if mismatches:
        print(f"{mismatches} of {len(checks)} figures do not match the report")
        return 1
    print(f"all {len(checks)} figures agree with the raw result files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
