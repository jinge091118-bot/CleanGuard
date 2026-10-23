# CleanGuard V0.2 Release Bug Tracker

Status: **No P0/P1 issue observed on tested Android 10-16 targets; OEM/manual coverage remains incomplete**

## Severity policy

- P0: data loss, wrong deletion, crash loop, DANGER/UNKNOWN deletion, permission bypass, or database corruption.
- P1: scan failure, trash/restore failure, severe ANR, or an unusable primary screen.
- P2: reproducible OEM incompatibility, significant performance regression, or major UI error.
- P3: minor UI, copy, or non-core experience issue.

## Active bugs

| ID | Severity | Environment | Reproduction | Expected / actual | Owner | Status | Regression evidence |
|---|---|---|---|---|---|---|---|
| - | - | API 29/30/34/36 official AVDs plus API 31 Huawei, API 33 OPPO, and API 35 Redmi | - | Each target passed the current 41-test suite; no application defect recorded | - | No active application bug | OEM/manual coverage limitations remain documented |

## Resolved R2 test issue

| ID | Type | Environment | Cause | Resolution | Regression evidence |
|---|---|---|---|---|---|
| R2-TEST-01 | Test portability | Redmi 23049RAD8C / HyperOS 3.0 | LazyColumn test expected an off-screen item to already exist in the semantics tree | Select the LazyColumn by its scroll-to-index action, scroll to the same required text, then assert it is displayed | Targeted test passed; complete 39/39 passed on Redmi and API 36 AVD |
| R2-TEST-02 | Test portability | Redmi 23049RAD8C / HyperOS 3.0 | MediaStore insertion returned the generic `external` URI while volume-aware incremental queries returned `external_primary`; string equality did not represent item identity | Match the same synthetic row by its stable MediaStore numeric ID while retaining URI checks in the direct scanner test | Targeted 3/3 platform tests and complete 41/41 Redmi suite passed |
| R2-TEST-03 | Test portability | Huawei JER-AN10 / HarmonyOS 4.2 at 540 dpi | Four Compose tests assumed off-screen LazyColumn children already existed in the semantics tree or required repeated nodes from separate rows to be composed simultaneously | Scroll the LazyColumn through its stable scroll-to-index semantics before asserting the required item; remove viewport-dependent counts unrelated to the behavior under test | Hardened Phase 10 UI class passed 9/9; complete Huawei suite passed 41/41 |
| R2-TEST-04 | Test portability | API 29 and API 30 official AVDs | The test inferred incremental capability from API level and relied on MediaStore to refresh synthetic fixture size; API 29 uses the defined full-scan fallback and the API 30 image exposes no Generation extension | Assert the gateway's real `supportsGeneration` capability, preserve API 29 fallback expectations, and write synthetic fixture `SIZE` explicitly | Current suite passed 41/41 on API 29, 30, 34, and 36; no production scanner change |
| R2-ENV-03 | Test environment | API 29 official AVD | Manually installed image omitted `dalvik.vm.heapgrowthlimit`, leaving an abnormal 16 MB ART growth limit and causing the 100k Room stress test to exhaust the test process | Initialize the AVD growth limit to 256 MB, within normal device-class values; production Manifest was not changed and `largeHeap` was not enabled | 100k Room stress test and complete 41/41 suite passed |

These issues changed test navigation/identity matching only. No production UI, scanner, cleanup,
permission, or safety behavior changed.

## R2 coverage blockers

These are validation-environment blockers, not confirmed CleanGuard defects.

| ID | Scope | Blocker | Impact | Status |
|---|---|---|---|---|
| R2-ENV-01 | Android 10-11 and 14 | SDK manager downloads were unreliable | Official Google APIs images were downloaded with validated byte ranges and ZIP integrity, then API 29/30/34 each passed 41/41 | Resolved 2026-10-19 |
| R2-ENV-02 | Remaining OEM matrix | No vivo/iQOO, Honor, Samsung, or physical Pixel has been validated | Cannot determine compatibility for the remaining required OEM families | Open |
| R2-COV-01 | Huawei, Redmi, and OPPO manual scenarios | Expanded automated baselines passed, but partial-photo selection, actual SAF grant/revoke, large/special datasets, trash confirmation, undo, cancellation, OEM reclaim, and OPPO manual rotation remain | Required physical-device scenario coverage remains incomplete | Open |

## R3 validation-environment blockers

| ID | Scope | Blocker | Impact | Status |
|---|---|---|---|---|
| R3-ENV-01 | Physical-device classes | The device reachable on 2026-10-23 reported `ro.product.model=Pixel 5` but is a virtualized Android 12 platform: Spreadtrum sensor vendor strings, no gyroscope / gravity / rotation-vector sensor, kernel host `localhost`, no `Hardware` line in `/proc/cpuinfo`, and QEMU-style SELinux properties | R3 requires a physical device, so no performance row can be accepted from this device. The physical Google Pixel row in the device matrix stays `Not run` | Open |
| R3-ENV-02 | Macrobenchmark execution | AndroidX Macrobenchmark completes no measured iteration on the virtualized platform. A 10-iteration cold-start run performed one profile install and then stopped producing output for 40 minutes; a reduced 2-iteration run with `CompilationMode.None()` stalled identically. Device-side `am start -W` completes in 150-491 ms and the app renders correctly, so the stall is in the Macrobenchmark and UiAutomator interaction rather than in the application | The Macrobenchmark-based collection path could not be exercised. The direct scanner instrumentation path was added and does complete | Open |

Both R3 blockers are environment limitations. Neither indicates a CleanGuard
defect, and neither may be recorded as a passed R3 row.

P0 and P1 release gates cannot be declared globally clear until the required OEM
and Android-version matrix is executed.

`R2-RISK-ACCEPTANCE.md` permits development and R3 preparation to continue
despite the remaining OEM/manual gaps. It does not authorize a public rollout
or represent untested devices as compatible.

R8 may fix only release-blocking bugs. Every fix requires reproduction, root-cause evidence, regression tests, OEM verification where relevant, and a new immutable RC tag.
