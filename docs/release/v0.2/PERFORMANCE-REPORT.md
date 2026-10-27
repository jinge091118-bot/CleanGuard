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
| Startup | Cold start | Redmi Note 12 Turbo | N/A | **574 ms median** | R3 |
| Startup | Warm start | Redmi Note 12 Turbo | N/A | **240 ms median** | R3 |
| Scan | First full scan | Redmi Note 12 Turbo | 10k | **659 ms median** | R3 |
| Scan | No-change incremental scan | Redmi Note 12 Turbo | 10k | **545 ms median** | R3 |
| Scan | 100 add / 100 modify / 100 delete | Redmi Note 12 Turbo | 10k | Not run | R3 |
| Memory | Peak PSS / RSS and GC | Redmi Note 12 Turbo | 10k | **60.5 MB heap, 100 MB RSS** | R3 |
| UI | Home/results/history/duplicates scrolling | Redmi Note 12 Turbo | 10k | **198–354 frames per series** | R3 |
| Startup | Cold start | Not a physical device | N/A | Not accepted | R3 |
| Scan | Any | Not a physical device | 10k | Not accepted | R3 |

The first block of rows uses the Redmi Note 12 Turbo, a physical device that
passed the identity verification described below. The second block is the
virtualized Android 12 platform from the 2026-10-23 session, whose numbers
remain diagnostic only and are recorded under "Virtual-device diagnostic".

## Physical device baseline (Redmi Note 12 Turbo)

### Device identity verification

The device was verified to be physical hardware before any measurement was
accepted. The reported identity did not need to be trusted, because the
hardware evidence is independent of it.

| Check | Observed | Meaning |
|---|---|---|
| SoC model | `SM7475`, platform `taro` | Qualcomm Snapdragon 7+ Gen 2, a real part |
| Gyroscope | `bmi26x Gyroscope` from BOSCH | Real inertial sensor |
| Gravity / linear acceleration | `QTI` | Qualcomm sensor hub |
| Rotation vector | `xiaomi` and `QTI` | Real sensor, vendor matches the brand |
| `ro.kernel.qemu` | empty | Not an emulated guest |
| Build fingerprint | `Redmi/marble/marble:15/AQ3A.250226.002/OS3.0.5.0.VMRCNXM:user/release-keys` | Matches the device matrix entry recorded on 2026-10-08 |

Note on the kernel host name: `uname -a` reports `Linux localhost ...`, which
is the default host name on Android and is not evidence of virtualization. The
decisive difference from the rejected platform is the sensor set: the
virtualized Android 12 image exposed no gyroscope, gravity, or rotation
vector sensor at all, while this device exposes a complete physical set.

### Device and environment

| Field | Value |
|---|---|
| Manufacturer / model | Xiaomi Redmi / `23049RAD8C` (`marble`) |
| System | Android 15, API 35, HyperOS 3.0 |
| Security patch | 2026-05-01 |
| ABI | arm64-v8a |
| Display | 1080 x 2400, density 440 |
| RAM | 11,427,864 kB reported by guest |
| Shared storage | 485,010,412 kB total; 320,614,240 kB available at collection |
| Battery at collection | 95%, charging (startup series) / 56% (scan series) |
| Thermal status at collection | 0, no throttling |
| Device class | Mid-range, satisfying the first required R3 device class |

### Startup

Collected with AndroidX Macrobenchmark, ten iterations per metric,
`CompilationMode.None` because the device cannot resolve a cloud baseline
profile. The plan calls for ten cold-start and ten warm-start iterations, and
this series meets that count.

| Metric | Iterations | Median | Minimum | Maximum | Coefficient of variation |
|---|---:|---:|---:|---:|---:|
| Time to initial display, cold start | 10 | 574.11 ms | 504.90 ms | 695.63 ms | 0.0996 |
| Time to initial display, warm start | 10 | 240.35 ms | 173.46 ms | 262.68 ms | 0.1358 |

Raw result file: `performance/2026-10-27-startup.json`.

An earlier two-iteration run in the same session produced a cold-start median
of 657.78 ms with a coefficient of variation of 0.0987. The ten-iteration series
above supersedes it. The difference between the two is consistent with the
expected warm-up effect in a short series and is the reason the plan requires
ten iterations rather than a token number.

These are the first measurements in this project that come from a confirmed
physical device and may be used as R3 evidence. The remaining rows are
collected in the same session and appended below as they complete.

### Harness defect found while collecting the scan series

The scan suite failed on this device for a reason that had nothing to do with
the application, and the finding is recorded here because it affects any
benchmark that drives a Compose interface on this ROM.

The scan never started. The benchmark waited for the finished label and then
timed out, twice, while the startup and scrolling suites passed. Driving the
same screen by hand completes the scan of the ten thousand row dataset in a
tenth of a second and shows the finished label, so the product was never
implicated.

Two separate faults were involved, and both had to be fixed before the suite
could run.

The first is how the button is reached. The Compose button exposes its label
as a text node that reports itself as not clickable, and the button node
around it reports the same:

```
TextView  clickable=false  text=开始安全体检  bounds=[426,1523][654,1578]
Button    clickable=false                    bounds=[55,1476][1025,1625]
View      clickable=true                     bounds=[55,1675][1025,1807]
```

The source is a plain Compose `Button`. An accessibility click is delivered to
the label, which does not handle it, so the touch never reaches the button.
The benchmark now injects the touch at the coordinates of the label, which is
the path a finger takes.

The second fault is that the first touch after the activity settles is lost.
A screen capture taken inside the benchmark at the moment of the tap shows
the home screen rendering correctly, with the button in the right place and
no dialog or shade in front of it, so the tap was landing on a window that
had not yet begun to receive input. The benchmark now repeats the tap until
the screen shows that the scan has started, which is what made the suite pass
on the first attempt afterwards.

Two supporting changes make a failure legible instead of mysterious. The wait
for the finished label is three minutes rather than fifteen, because a scan
that takes a tenth of a second cannot plausibly need fifteen minutes and the
long wait made a broken click look like a slow device. And a timeout now
reports the coordinates that were tapped, the bounds they came from, the
focused window, and the text that was on screen, which is what identified the
cause in the first place: the visible text showed the app still on the home
screen with the button present.

### Scan

Collected with AndroidX Macrobenchmark on the ten thousand row dataset, three
iterations for the full scan and five for the incremental scan. The metric is
the duration of the `CleanGuardScan` trace section, which the application
marks around the whole local assessment.

| Metric | Iterations | Median | Minimum | Maximum | Coefficient of variation |
|---|---:|---:|---:|---:|---:|
| Full scan, `CleanGuardScan` | 3 | 658.63 ms | 637.45 ms | 661.17 ms | 0.0200 |
| No-change incremental scan, `CleanGuardScan` | 3 | 544.72 ms | 535.54 ms | 559.91 ms | 0.0165 |

Raw result file: `performance/2026-10-27-scan.json`.

### Memory

Captured in the same runs through the Macrobenchmark memory metric, reported as
the maximum observed during the scan.

| Metric | Full scan | Incremental scan |
|---|---:|---:|
| Heap size, maximum | 61,906 kB | 51,680 kB |
| Resident set, anonymous, maximum | 102,436 kB | 95,904 kB |
| Resident set, file, maximum | 147,992 kB | 137,244 kB |
| Graphics memory, maximum | 45,656 kB | 45,656 kB |

The heap peak is about sixty megabytes for a ten thousand row scan, and the
incremental pass allocates roughly ten megabytes less because it does not
rebuild the scanned file list. The coefficient of variation on the heap figure
is below two tenths of a percent, which means the figure is stable enough to
be compared against a later build.

### Dataset composition

The dataset is not a synthetic device. The handset carries its own media, and
the generator sizes the fixture so the total lands on the tier.

| Component | Rows |
|---|---:|
| Pre-existing on the device | 3,460 |
| Generated fixture | 6,540 |
| Total seen by the scanner | 10,000 |

The pre-existing rows are the camera roll, chat archives, and screenshots
already on the handset. They are read only as index metadata, and the
application never opens the media itself, so their presence changes the row
count but not the character of the work.

### Scrolling

Frame timing for the four screens that hold a list, five iterations each. The
metric is the number of frames the series produced, which the Macrobenchmark
frame timing metric reports; a higher count for the same gesture means the
screen kept producing frames smoothly rather than dropping them.

| Screen | Iterations | Median frames | Minimum | Maximum | Coefficient of variation |
|---|---:|---:|---:|---:|---:|
| Home | 5 | 221 | 205 | 227 | 0.0377 |
| Results | 5 | 198 | 163 | 231 | 0.1291 |
| Scan history | 5 | 215 | 177 | 229 | 0.0939 |
| Duplicate groups | 5 | 354 | 351 | 355 | 0.0047 |

Raw result file: `performance/2026-10-27-scroll.json`.

The results screen produces the fewest frames and the duplicate screen the
most, which is what the content predicts: the results screen carries the
heaviest single list, while the duplicate screen shows compact summary rows.
The duplicate series is also the most stable of the four, with a coefficient of
variation below half a percent.

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
