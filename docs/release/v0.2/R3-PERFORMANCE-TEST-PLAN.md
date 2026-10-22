# CleanGuard V0.2 R3 Performance Test Plan

Status: **In progress**

This plan controls R3 physical-device performance validation. Emulator runs validate only the test harness; they are never accepted as product performance evidence.

## Scope

R3 measures the frozen V0.2 release candidate. It does not add scanning, deletion, analytics, networking, or permissions.

- Cold and warm startup.
- First full scan.
- No-change incremental scan.
- Incremental scan after 100 additions, 100 modifications, and 100 deletions.
- Peak PSS/RSS and observable GC pressure.
- Home, results, history, and duplicate-list scrolling.

## Device matrix

Use one connected device at a time and record its model, Android/API version, RAM, storage type, free space, build fingerprint, battery level, and thermal state.

1. A mainstream mid-range physical Android device.
2. A flagship physical Android device.
3. Preferably at least one Android 10-12 device.

A device must have USB debugging authorized, at least 50% battery, adequate free storage for the selected fixture, and no active system update or battery-saver mode. Let the device return to a stable temperature before each measured series.

## Dataset matrix

Only generated, non-private fixtures may be used. Store them in a dedicated CleanGuard R3 directory and delete only that exact directory after validation.

| Tier | Media rows | Required runs |
|---|---:|---|
| Small | 10,000 | Full, no-change incremental |
| Medium | 50,000 | Full, no-change incremental, 100 add/modify/delete |
| Stress target | 100,000 | Full, no-change incremental, memory and scrolling |

Record actual MediaStore row counts before every run. A tier is not valid when indexing is still in progress or the expected row count is not present.

## Test build

The `benchmark` app variant is release-like, non-debuggable, and locally debug-signed. Its benchmark-only manifest enables shell profiling. Production `main` and `release` manifests are unchanged.

Build the harness:

```powershell
.\gradlew.bat :macrobenchmark:assembleBenchmark
```

Run startup and home scrolling on the single connected device:

```powershell
.\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest
```

Do not suppress `EMULATOR`, `LOW-BATTERY`, or thermal warnings during an accepted physical-device run. A warned or throttled series must be discarded and repeated after correcting the condition.

## Measurement procedure

For every device and dataset tier:

1. Record device and environmental metadata; verify one authorized device is connected.
2. Install the benchmark target without changing source permissions.
3. Grant only the media permissions appropriate to that Android version.
4. Verify the fixture count and run one unmeasured scan to confirm readiness.
5. Run ten cold-start and ten warm-start iterations through Macrobenchmark.
6. Measure first full scan from an empty CleanGuard database.
7. Immediately run a no-change incremental scan.
8. For the 50k tier or larger, add 100 rows, modify 100 rows, delete 100 rows, wait for MediaStore indexing, then run the incremental scan.
9. Capture PSS/RSS snapshots before scan, at peak scan load, after completion, and after a five-minute idle period. Retain a Perfetto trace for GC and scheduling analysis.
10. Run five scrolling iterations on each required screen with the large dataset.
11. Export result JSON, instrumentation XML, and Perfetto traces before changing the dataset.
12. Restore the signed Release build and remove only the generated fixture.

## Evidence locations

Generated local evidence is under:

```text
macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/
macrobenchmark/build/outputs/androidTest-results/connected/benchmark/
```

Build output is disposable and not committed. The reviewed numeric summary, exact conditions, and trace filenames are recorded in `PERFORMANCE-REPORT.md`.

## Gate rules

- Every accepted measurement must come from a physical device and include its conditions.
- A crash, ANR, test failure, safety regression, data corruption, unbounded growth, or permission expansion fails R3.
- Throttled, low-battery, emulator, incomplete-fixture, or background-interference runs are invalid and must be repeated.
- Absolute performance acceptance limits must be approved from physical baseline evidence; emulator timing cannot establish them.
- R3 remains open until both required physical-device classes and all mandatory dataset tiers are represented.

## Current harness coverage

- `StartupBenchmark.coldStartup`: ten cold-start iterations with `StartupTimingMetric`.
- `StartupBenchmark.warmStartup`: ten warm-start iterations with `StartupTimingMetric`.
- `HomeScrollBenchmark.scrollHome`: five home scrolling iterations with `FrameTimingMetric`.
- `ScanBenchmark.firstFullScan`: three iterations; clears only CleanGuard app data, restores the existing media permissions through the test shell, and measures the `CleanGuardScan` trace section plus peak heap/RSS counters.
- `ScanBenchmark.noChangeIncrementalScan`: prepares one unmeasured full-scan baseline, then measures five no-change incremental scans with the same trace and memory metrics.
- Each iteration emits Perfetto trace evidence through AndroidX Macrobenchmark.

The app emits only the fixed `CleanGuardScan` asynchronous trace-section name; it does not place URI, path, filename, hash, or other media data in traces.

The 100 add/modify/delete case, large-data secondary-screen scrolling, and physical dataset execution remain open. They must not be marked passed from API 36 emulator smoke runs.
