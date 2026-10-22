# CleanGuard V0.2 Performance Report

Status: **R3 in progress — benchmark harness verified; physical evidence pending**

R0 established the report structure. R3 now includes a release-like Macrobenchmark target, startup benchmarks, a home-scrolling benchmark, and automatic Perfetto trace export. Emulator timing is not accepted as physical-device performance evidence.

Execution procedure: `R3-PERFORMANCE-TEST-PLAN.md`.

## Required devices

- At least one mainstream mid-range Android device.
- At least one flagship Android device.
- Preferably one Android 10-12 device.

## Required datasets

- 10,000 media items.
- 50,000 media items.
- Target stress case: 100,000 media items.

## Measurements

| Area | Metric | Device | Dataset | Result | Gate |
|---|---|---|---:|---:|---|
| Startup | Cold start | Pending | N/A | Pending | R3 |
| Startup | Warm start | Pending | N/A | Pending | R3 |
| Scan | First full scan | Pending | 10k/50k/100k | Pending | R3 |
| Scan | No-change incremental scan | Pending | 10k/50k/100k | Pending | R3 |
| Scan | 100 add / 100 modify / 100 delete | Pending | 50k+ | Pending | R3 |
| Memory | Peak PSS / RSS and GC | Pending | 100k target | Pending | R3 |
| UI | Home/results/history/duplicates scrolling | Pending | Large dataset | Pending | R3 |

## Harness smoke result

On 2026-10-20, the API 36 `CleanGuard_API_36` AVD completed 10/10 cold-start iterations, 10/10 warm-start iterations, and 5/5 home-scrolling iterations. On 2026-10-21 it also completed 3/3 first-full-scan and 5/5 no-change-incremental-scan iterations. AndroidX Macrobenchmark exported benchmark JSON and a Perfetto trace for every iteration. These results prove the R3 collection pipeline works; they are explicitly **not** accepted as physical-device performance evidence and do not satisfy any row in the measurement table.

The emulator-only cold-start median time to initial display was 1,044.83 ms (minimum 901.72 ms, maximum 1,615.38 ms). The warm-start median was 458.05 ms (minimum 169.93 ms, maximum 834.39 ms). The scrolling smoke run reported CPU frame duration P50 15.01 ms and P90 64.98 ms. These numbers are retained only for harness diagnostics and must not be used in product claims or physical-device comparisons.

The zero-fixture emulator scan smoke reported a 571.56 ms median for the full-scan trace section and 699.75 ms for the no-change incremental trace section. The dataset contained no R3 fixture tier, so the apparent ordering and absolute values have no acceptance meaning. The run additionally confirmed that peak heap and anonymous/file RSS counters are exported.

## Implementation status

- Macrobenchmark module: ready.
- Release-like non-debuggable `benchmark` app variant: ready.
- Benchmark-only shell profiling declaration: ready; production manifest unchanged.
- Cold and warm startup collection: smoke verified.
- Home frame collection: smoke verified.
- First-full-scan trace and memory collection: zero-fixture smoke verified.
- No-change incremental trace and memory collection: zero-fixture smoke verified.
- Physical-device runs: pending.
- 10k/50k/100k fixture execution: pending.
- 100 add/modify/delete, GC review, and secondary-screen evidence: pending.

## Engineering verification

On 2026-10-22, the R3 harness and scan trace integration passed the following local gates:

- Debug unit tests: 109/109 passed.
- Release unit tests: 109/109 passed.
- Android Lint: no issues found.
- Debug APK, Release APK, Release AAB, Debug AndroidTest APK, and Macrobenchmark benchmark APK: built successfully.
- Production manifest permissions: unchanged; the benchmark-only profileable declaration does not add a runtime permission.

The build emitted only the existing environment/tooling warnings documented by the project (SDK XML tool-version mismatch, experimental path override, native libraries packaged without stripping, and Kotlin daemon fallback in the restricted build environment). None was a blocking error.

## Tools

Use Android Macrobenchmark and Perfetto; use Android Studio Profiler only where additional diagnosis is necessary. Record traces and test conditions without collecting user media content.

R3 is not complete and the release checklist remains unchecked.
