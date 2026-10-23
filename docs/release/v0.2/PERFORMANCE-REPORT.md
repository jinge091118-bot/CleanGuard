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
| Startup | Cold start | Not a physical device | N/A | Not accepted | R3 |
| Startup | Warm start | Not a physical device | N/A | Not accepted | R3 |
| Scan | First full scan | Not a physical device | 10k | Not accepted | R3 |
| Scan | No-change incremental scan | Not a physical device | 10k | Not accepted | R3 |
| Scan | 100 add / 100 modify / 100 delete | Not a physical device | 10k | Not accepted | R3 |
| Memory | Peak PSS / RSS and GC | Not a physical device | 10k | Not accepted | R3 |
| UI | Home/results/history/duplicates scrolling | Not a physical device | 10k | Not accepted | R3 |

No row of this table is satisfied. The only device available during the
2026-10-23 session reported itself as a Google Pixel 5 but is a virtualized
Android 12 platform, and R3 does not accept emulator or virtualized
measurements. The scan and startup numbers collected on that device are
recorded below under "Virtual-device diagnostic" because they are useful for
correctness verification of the incremental engine, but they are not product
performance evidence and must not be used for acceptance limits, marketing
claims, or cross-device comparison.

## Virtual-device diagnostic (not R3 evidence)

Session date: 2026-10-23. Device serial `2023081000029`, adb-reported
identity `Pixel 5 / redfin / Android 12 / API 31`, fingerprint
`google/redfin/redfin:12/SP1A.210812.015/7685516:user/release-keys`.

### Device identity finding

The device identity was verified before any measurement was accepted, and the
reported identity did not survive verification:

- Sensor service reports 43 sensors from `Sprd Group Ltd.`, and exposes no
  gyroscope, gravity, or rotation-vector sensor. A physical Pixel 5 exposes
  three.
- Kernel identity is `Linux localhost 5.4.161`, not a device-specific host
  name, and `/proc/cpuinfo` exposes no `Hardware` model line.
- The system SELinux policy references `qemu_sf_lcd_density_prop`, and the
  system sets `ro.virtual_ab.enabled=true` with `ro.virtual_ab.compression.enabled=true`.
- The platform is a Spreadtrum (`init.svc.vendor.sprd.*`) Android 12 stack.

The device is therefore a virtualized Android 12 platform that presents
`ro.product.model=Pixel 5`. It is not a physical Google Pixel. The R2 device
matrix correctly lists physical Google Pixel coverage as `Not run`, and this
session does not change that row.

### Correctness findings that do not depend on device class

The 10,015-row synthetic dataset was generated inside the dedicated
`/sdcard/CleanGuardR3` fixture root and indexed by MediaStore. The incremental
engine behaved exactly as designed, and these are functional results rather
than performance results:

| Scenario | Execution type | Duration (ms) | Changed | Removed | Indexed items | Errors |
|---|---|---:|---:|---:|---:|---:|
| Baseline scan of 10,015 rows | `FULL` | 493 | 10015 | 0 | 10015 | 0 |
| Incremental after 100 additions | `INCREMENTAL` | 764 | 100 | 0 | 10115 | 0 |
| Incremental after 50 modifications | `INCREMENTAL` | 708 | 50 | 0 | 10115 | 0 |
| Incremental after 50 deletions | `INCREMENTAL` | 684 | 0 | 50 | 10065 | 0 |

This confirms that the Android 11+ generation-based incremental path observes
additions, modifications, and removals correctly at the 10k tier, that the
no-change incremental scan reports zero changes, and that no read error was
produced across 40,000+ scanned rows.

Startup timings collected on the same virtual device were cold start 1,376 /
1,405 / 1,407 / 1,414 / 1,445 ms (`am start -W` after `am force-stop`, five
iterations) and warm start 70 / 72 / 78 / 80 / 81 ms. These are recorded for
diagnostic continuity only.

### Harness limitation observed on this platform

AndroidX Macrobenchmark could not complete a single measured iteration on
this platform. A 10-iteration cold-start run produced one profile install and
then no further progress for 40 minutes, and a reduced 2-iteration run with
`CompilationMode.None()` stalled in the same place. Device-side `am start -W`
completes in 150 ms to 491 ms and the CleanGuard home screen renders
correctly, so the stall is in the Macrobenchmark/UiAutomator interaction on
this virtualized platform rather than in the application. The R3 measurement
collection therefore has to run through the direct scanner instrumentation
described below until a confirmed physical device is connected.

## Measurement tooling added in this session

Two measurement paths are now available and are reusable on a confirmed
physical device.

- `tools/r3/generate-fixture.sh <serial> <rows>` writes synthetic media into
  `/sdcard/CleanGuardR3` and triggers a MediaStore index pass.
  `tools/r3/remove-fixture.sh <serial>` removes exactly that directory and
  re-indexes. Both scripts touch no path outside the fixture root.
- `app/src/androidTest/.../R3ScanPerformanceTest.kt` drives the production
  scanner against the device dataset and reports execution type, duration,
  change and removal counts, indexed row count, and heap usage.
- `app/src/androidTest/.../R3ScanMutationScenarioTest.kt` executes the
  100 add / 100 modify / 100 delete sequence and asserts the observed change
  and removal counts.
- The `macrobenchmark` module now accepts `cleanguardIterationsStartup`,
  `cleanguardIterationsUi`, `cleanguardIterationsFullScan`,
  `cleanguardIterationsIncrementalScan`, and `cleanguardCompilation`
  instrumentation arguments, so a harness-validation run can use reduced
  iteration counts or skip cloud profile resolution. Defaults are unchanged
  and remain the R3 acceptance counts.

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
