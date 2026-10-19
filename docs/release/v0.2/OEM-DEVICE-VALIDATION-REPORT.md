# CleanGuard V0.2 OEM Device Validation Report

Updated: 2026-10-19 (Asia/Shanghai)

## 1. Outcome

R2 has started but is not complete. Official Google APIs emulators on Android
10 / API 29, Android 11 / API 30, Android 14 / API 34, and Android 16 / API 36
each passed the current 41-test suite on 2026-10-19. A Redmi 23049RAD8C physical
device running Android 15 / API 35 and HyperOS 3.0 and an OPPO A58 5G physical
device running Android 13 / API 33 and ColorOS 13 both passed the expanded
current 41-test suite on 2026-10-10. A Huawei nova 7 Pro 5G running Android 12 /
API 31 and HarmonyOS 4.2 also passed the 41-test suite after four viewport-
dependent Compose assertions were hardened. All three phones passed real
incremental MediaStore and duplicate hash/cache validation plus signed-release
installation, permission-denied launch, lifecycle smoke, and targeted Android
system-routing checks within the limits recorded below.

The remaining P0/P1 OEM families and the manual permission-dialog,
large/special dataset, trash, undo, and long-running scenarios remain.

This remains partial evidence and does not approve R2. Because additional OEM
phones are unavailable, the project owner approved conditional continuation to
R3 preparation on 2026-10-19 under `R2-RISK-ACCEPTANCE.md`. That decision does
not treat untested OEMs as passed or authorize broad public release.

## 2. Baseline under test

- Application ID: `com.cleanguard.app`
- Version code: `2`
- Version name: `0.2.0`
- Branch: `feature/v0.2`
- RC tag: `v0.2.0-rc1`
- RC target: `2694f256eeb490fb9f26ad696fb5546c30109611`
- Current R1 documentation baseline before R2 changes: `2cb8b70`
- Latest R2-built signed Release APK: 25,277,457 bytes; SHA-256 `F3CEDA288FE2C7C363AD2464629D4B20201DE79B3979B1719DB8610E02F01FA3`
- Latest R2-built signed Release AAB: 8,729,184 bytes; SHA-256 `064E1FA98AA18569FC32A218972072B235A9BE31D5C4D254CFE7325840B8D224`

The RC tag remains immutable. The R2 test changes are test-only and do not
alter production behavior. These are the hashes of the artifacts regenerated
during R2; the R1 report retains its own historical build hashes.

## 3. Available device inventory

Seven validation targets were available: three physical phones and four
official Android emulators.

### API 36 official emulator

| Field | Value |
|---|---|
| Serial | `emulator-5554` |
| AVD | `CleanGuard_API_36` |
| Manufacturer / model | Google / `sdk_gphone64_x86_64` |
| System | Android 16, API 36, Google APIs x86_64 |
| Display | 1080 x 2400 |
| RAM | 2,531,836 kB reported by guest |
| Data partition | 10,167,132 kB total; 8,542,536 kB available at capture |
| Device class | Official emulator; not physical Pixel or OEM evidence |
| Test time | Initial 2026-10-08; expanded 41-test rerun 2026-10-19, Asia/Shanghai |

### Additional Android-version emulators

| AVD | Android / API | Image | Current suite | Release smoke |
|---|---|---|---:|---|
| `CleanGuard_API_29` | Android 10 / API 29 | Google APIs x86_64 | 41/41 passed | Signed APK installed and launched |
| `CleanGuard_API_30` | Android 11 / API 30 | Google APIs x86_64 | 41/41 passed | Signed APK installed and launched |
| `CleanGuard_API_34` | Android 14 / API 34 | Google APIs x86_64 | 41/41 passed | Signed APK installed and launched |

API 29 exercised the defined `API_29_FULL` path. The API 30 image did not
expose the MediaStore Generation extension, so the production gateway correctly
selected the full-scan fallback. API 34 and API 36 exercised generation-aware
behavior. Test expectations now read the gateway capability instead of assuming
that every API 30+ image supports Generation.

### Redmi / HyperOS physical device

| Field | Value |
|---|---|
| Serial | `be250aeb` |
| Manufacturer / brand | Xiaomi / Redmi |
| Model / device | `23049RAD8C` / `marble` |
| System | Android 15, API 35, HyperOS 3.0 |
| Incremental build | `OS3.0.5.0.VMRCNXM` |
| Security patch | 2026-05-01 |
| ABI | arm64-v8a |
| Display | 1080 x 2400 |
| RAM | 11,427,864 kB reported by guest |
| Shared storage | 485,010,412 kB total; 324,316,820 kB available at inventory |
| Battery | 76%, charging, 36.4 C at inventory |
| Test time | 2026-10-08 and expanded rerun 2026-10-10, Asia/Shanghai |
| Media inventory | Private media count not collected; six synthetic app-owned rows were created across isolated tests and deleted afterward |

### OPPO / ColorOS physical device

| Field | Value |
|---|---|
| Serial | `RO8HFIPZUO7HOVCU` |
| Manufacturer / model | OPPO / OPPO A58 5G (`PHJ110`, device `OP526D`) |
| System | Android 13, API 33, ColorOS 13 (`PHJ110_11_C.20`) |
| Incremental build | `S.23689e7-1` |
| Security patch | 2026-01-01 |
| ABI | arm64-v8a |
| Display | 720 x 1612 |
| RAM | 5,753,468 kB reported by guest |
| Shared storage | 111,892,448 kB total; 80,390,040 kB available at inventory |
| Battery | 41%, USB charging, approximately 32.0 C at inventory |
| Test time | 2026-10-10, Asia/Shanghai |
| Media inventory | Private media count not collected; six synthetic app-owned rows were created across isolated runs and deleted afterward |

### Huawei / HarmonyOS physical device

| Field | Value |
|---|---|
| Serial | `2NTDU20430007200` |
| Manufacturer / model | Huawei / nova 7 Pro 5G (`JER-AN10`, device `HWJER`) |
| System | Android 12, API 31, HarmonyOS 4.2.0 (`JER-AN10 4.2.0.121`) |
| EMUI compatibility property | `EmotionUI_14.2.0` |
| Incremental build | `104.2.0.121C00` |
| Security patch | 2021-10-05 |
| ABI | arm64-v8a |
| Display | 1080 x 2340 at 540 dpi |
| RAM | 7,708,008 kB reported by guest |
| Shared storage | 239,560,704 kB total; 58,455,276 kB available at inventory |
| Battery | 44%, USB charging, 35.0 C at inventory |
| Test time | 2026-10-10; latest Release artifact reinstall 2026-10-17, Asia/Shanghai |
| Media inventory | Private media count not collected; synthetic app-owned rows were created in isolated tests and deleted afterward |

Physical-device evidence is valid only for the exact model and ROM build
recorded above and
must not be generalized to an entire OEM family. Huawei / HarmonyOS evidence
does not replace the required Honor / MagicOS row.

## 4. Automated validation

### Full build and static checks

The following offline build set completed successfully:

```text
gradlew test lint assembleDebug assembleRelease bundleRelease assembleDebugAndroidTest --offline --stacktrace
```

- Debug JVM tests: 109/109 passed
- Release JVM tests: 109/109 passed
- Lint: `No issues found.`
- Debug APK: generated
- Signed Release APK: generated
- Signed Release AAB: generated
- Debug Android test APK: generated
- Release APK signature: verified with APK Signature Scheme v2; signer certificate SHA-256 `c15dce5c840e6c7383d470340d51da1c9d1df9ddb3fdef3c56421b9f523b5d35`
- Build result: successful; final verification 148 tasks, 13 executed and 135 up-to-date

### Android-version emulator suites

```text
adb -s emulator-5554 shell am instrument -w -r \
  com.cleanguard.app.test/androidx.test.runner.AndroidJUnitRunner
```

- Tests per API: 41
- Passed per API: 41
- Failed: 0
- Errors: 0
- Skipped: 0
- APIs completed: 29, 30, 34, and 36
- Result: all four official AVDs passed the current expanded suite on 2026-10-19

The API 29 image omitted a normal ART heap-growth property and initially gave
the instrumentation process an abnormal 16 MB limit. The AVD-only property was
initialized to 256 MB; no production Manifest or `largeHeap` setting changed.
The 100k Room stress test and complete suite then passed. An API 36 attempt that
reported zero tests was discarded because the signed Release package blocked
Debug installation; after removing it, the actual 41-test run passed.

### Redmi / HyperOS instrumented suite

```text
adb -s be250aeb shell am instrument -w -r \
  com.cleanguard.app.test/androidx.test.runner.AndroidJUnitRunner
```

- Tests: 41
- Passed: 41
- Failed: 0
- Errors: 0
- Skipped: 0
- Suite time: 27.997 seconds
- Real MediaStore platform tests: 3/3 passed
- Room, migration, DataStore, UI, and safety suites: passed

HyperOS initially blocked the Compose instrumentation host Activity with
`MIUILOG- Permission Denied Activity`. Enabling the per-app “后台弹出界面”
permission allowed the Android test host to run. This permission is an OEM test
harness prerequisite only: CleanGuard does not request it in its Manifest, and
the signed Release application launched normally without it.

The Redmi run exposed a test portability issue: a LazyColumn assertion assumed
the target item was already composed at the emulator density. The test now
selects the LazyColumn by its stable scroll-to-index semantics before locating
the same required text. Product UI behavior and wording were not relaxed.

The suite's R2 platform tests use only synthetic, app-owned rows in the real
Android MediaStore. They verify image/video/audio discovery through the
unchanged `MediaStoreScanner`; a full scan followed by unchanged incremental,
modify, and delete reconciliation; exact duplicate quick hash and full SHA-256;
cache reuse; and stale file-version cache invalidation. Every fixture is removed
in a `finally` block. No media read permission is granted, so private user media
is not made accessible to these tests.

The mutation test initially compared the generic insertion URI (`external`)
with the volume-aware query URI (`external_primary`). Redmi correctly returned
the same MediaStore numeric ID through both representations. The test now uses
that stable ID for identity and passed on rerun; production scanner behavior was
not changed.

### OPPO / ColorOS instrumented suite

```text
adb -s RO8HFIPZUO7HOVCU shell am instrument -w -r \
  com.cleanguard.app.test/androidx.test.runner.AndroidJUnitRunner
```

- Tests: 41
- Passed: 41
- Failed: 0
- Errors: 0
- Skipped: 0
- Suite time: 53.463 seconds
- Targeted real MediaStore platform tests: 3/3 passed in 3.540 seconds
- Room, migration, DataStore, UI, and safety suites: passed

ColorOS allowed the instrumentation host and all Compose UI tests without an
additional per-app background-activity permission. The first ADB installation
of each main APK type required ColorOS “安装增强防护” and its user-visible deep
scan; installation succeeded after that system-controlled check. This is an OEM
installer workflow, not a permission requested by CleanGuard.

As on Redmi, only synthetic app-owned image, video, and audio rows were used.
No media read permission was granted. The targeted run and complete suite both
removed their rows in `finally`; the three resulting empty fixture directories
were verified and removed after testing.

### Huawei / HarmonyOS instrumented suite

```text
adb -s 2NTDU20430007200 shell am instrument -w -r \
  com.cleanguard.app.test/androidx.test.runner.AndroidJUnitRunner
```

- Tests: 41
- Passed: 41
- Failed: 0
- Errors: 0
- Skipped: 0
- Suite time: 15.015 seconds
- Targeted real MediaStore platform tests: 3/3 passed in 2.124 seconds
- Targeted hardened Phase 10 UI suite: 9/9 passed in 8.008 seconds
- Room, migration, DataStore, UI, and safety suites: passed

The first complete run passed 37 tests and exposed four test-only assumptions
that off-screen LazyColumn children would already exist in the Compose semantics
tree, or that repeated nodes would be composed simultaneously. HarmonyOS used
a 540 dpi viewport where those assumptions were false. The tests now scroll the
LazyColumn by its stable scroll-to-index semantics before asserting the same
required item and no longer require unrelated repeated controls to be visible
at once. Production UI, wording, scanner, cleanup, and safety behavior were not
changed. The hardened class passed 9/9, followed by the complete 41/41 suite.

The tests used only app-owned synthetic MediaStore rows without storage
permission. All rows were deleted by test cleanup; the three empty fixture
directories were verified and removed afterward.

## 5. Signed-release and system smoke

### API 36 official emulator

| Area | Executed evidence | Result |
|---|---|---|
| Install | Existing app removed; signed Release APK installed | Pass |
| First launch with permissions denied | Cold start completed in 872 ms | Pass |
| Full media permissions | Image, video, and audio permissions granted; cold start completed in 1,093 ms | Pass |
| Revoke and reauthorize media | Permissions revoked and granted again through system controls | Pass |
| Usage Access | System Usage Access settings activity resolved | Pass |
| Downloads SAF | Android DocumentsUI tree picker resolved | Pass (routing only) |
| Background / resume | Home then relaunch | Pass |
| Lock / unlock | Device sleep and wake flow | Pass |
| Rotation | Activity handled orientation change | Pass |
| Process recreation | App process killed by system command and relaunched | Pass |
| Runtime health | No fatal exception, OOM, ANR, `SecurityException`, or `SQLiteException` marker observed | Pass |

This smoke does not replace hands-on user-confirmation testing for partial photo
access, actual SAF authorization/revocation, MediaStore trash confirmation, or
undo cancellation.

### Redmi / HyperOS physical device

| Area | Executed evidence | Result |
|---|---|---|
| Install | Signed Release APK installed, replaced by test build, then restored after testing | Pass |
| First launch with permissions denied | Cold start completed in 369 ms on first run, 423 ms after the first final reinstall, and 549 ms after the expanded-suite reinstall | Pass |
| Permission state | Image, video, and audio permissions confirmed denied after final reinstall | Pass |
| Usage Access | HyperOS resolved `Settings$UsageAccessSettingsActivity` | Pass (routing only) |
| Downloads SAF | System resolved Google DocumentsUI `PickActivity` | Pass (routing only) |
| Background / resume | Home then Release relaunch; 84 ms hot launch | Pass |
| Rotation | Portrait/landscape setting changed and restored to original values | Pass |
| Lock / unlock | Sleep, wake, dismiss keyguard, and re-entry completed | Pass |
| Process-reclaim command | Background kill command followed by relaunch remained hot | Inconclusive; OEM reclaim not proven |
| Runtime health | No CleanGuard fatal exception, OOM, ANR, or database exception after Release smoke | Pass |

The phone was left with the signed non-debuggable Release APK installed, version
code 2, version name 0.2.0, and all media permissions denied. Test APKs and the
three empty `CleanGuardR2` fixture directories were removed; system animation
scales remained at their restored values.

### OPPO / ColorOS physical device

| Area | Executed evidence | Result |
|---|---|---|
| Install | Signed Release installed, replaced by Debug and Android test packages, then restored | Pass; ColorOS deep scan required for first main-APK installs |
| First launch with permissions denied | Initial signed Release cold start 514 ms; final restored Release cold start 535 ms | Pass |
| Release identity | Version code 2, version name 0.2.0; package flags do not contain `DEBUGGABLE` | Pass |
| Permission state | Image, video, and audio permissions confirmed denied after final reinstall | Pass |
| Usage Access | ColorOS resolved `Settings$UsageAccessSettingsActivity` | Pass (routing only) |
| Downloads SAF | System DocumentsUI `PickActivity` resolved | Pass (routing only) |
| Background / resume | Home then Release relaunch; 88 ms hot launch | Pass |
| Rotation | ADB change rejected because ColorOS does not grant shell `WRITE_SETTINGS`; no setting changed | Not run; requires manual interaction |
| Lock / unlock | Sleep, wake, dismiss keyguard, and return to `MainActivity` completed | Pass |
| Runtime health | No CleanGuard fatal exception, OOM, ANR, `SecurityException`, or database exception after Release smoke | Pass |

The phone was left with only the signed nondebuggable Release APK installed,
version code 2, version name 0.2.0, and all media permissions denied. The test
package, synthetic MediaStore rows, empty fixture directories, and temporary
UI diagnostic files were removed.

### Huawei / HarmonyOS physical device

| Area | Executed evidence | Result |
|---|---|---|
| Install | Signed Release installed, replaced by paired Debug/test packages, then latest rebuilt Release restored | Pass |
| First launch with permission denied | Initial Release cold start 416 ms; post-test reinstall 364 ms; latest artifact reinstall 590 ms | Pass |
| Release identity | Version code 2, version name 0.2.0; package flags do not contain `DEBUGGABLE` | Pass |
| Permission state | Android 12 `READ_EXTERNAL_STORAGE` remained denied; legacy storage app-op remained ignored | Pass |
| Usage Access | HarmonyOS resolved `Settings$UsageAccessSettingsActivity` | Pass (routing only) |
| Downloads SAF | System DocumentsUI `PickActivity` resolved | Pass (routing only) |
| Background / resume | Home then Release relaunch; 75 ms hot launch | Pass |
| Rotation | Portrait to landscape succeeded; `MainActivity` remained resumed; original settings restored | Pass |
| Lock / unlock | Sleep, wake, dismiss keyguard, and return to `MainActivity` completed | Pass |
| Process recreation | Background app process was absent after kill and relaunched cold in 344 ms | Pass |
| Runtime health | No CleanGuard fatal exception, OOM, ANR, `SecurityException`, or database exception after Release smoke | Pass |

The device was left with only the latest signed nondebuggable Release APK
installed and storage permission denied. Test packages and empty fixture
directories were removed; rotation settings were restored to their original
values. Error-level log entries observed after final launch belonged to Huawei
system-manager/settings services, not CleanGuard.

## 6. Required scenario status

| Scenario group | Current API 29/30/31/33/34/35/36 status | Remaining work |
|---|---|---|
| Install / first launch | Pass on API 29/30/34/36 AVDs and partial pass on API 31/33/35 physical devices | Signed upgrade path and retained-data reinstall behavior remain |
| Permissions | Partial pass | Huawei, Redmi, and OPPO deny-all and system routing passed; partial photos, actual SAF grant/revoke, OEM dialogs, and cross-version behavior remain |
| Scanning | Partial pass | Real MediaStore full, unchanged incremental, modify, and delete reconciliation passed using app-owned rows without media permission; large file, APK, screenshot, screen recording, and Downloads datasets remain |
| Duplicate detection | Partial real-platform pass | Exact duplicate quick hash, full SHA-256, cache reuse, and stale-version invalidation passed; nonduplicate, cancellation, and large-video flows remain |
| Cleanup safety | Automated regression only | REVIEW default-off and DANGER/UNKNOWN protection remain green in tests; real trash confirmation, cancellation, success, undo, and undo-cancel require physical-device execution |
| Lifecycle | Partial pass | Huawei, Redmi, and OPPO background and lock/unlock passed; Huawei and Redmi rotation passed while OPPO rotation remains manual; actual OEM system reclaim and cancellation during live scans remain |
| OEM behavior | Huawei supplemental plus Redmi and OPPO required-family automated baselines passed | Manual scenario sets plus vivo/iQOO, Honor, Samsung, and physical Pixel remain |

## 7. Android-version and OEM matrix

| Coverage | Result |
|---|---|
| Android 10 / API 29 | Current 41-test suite passed on official Google APIs emulator; API 29 full-scan fallback verified |
| Android 11 / API 30 | Current 41-test suite passed on official Google APIs emulator; no-Generation full-scan fallback verified |
| Android 12 / API 31 or 32 | Expanded 41-test automated baseline passed on Huawei nova 7 Pro 5G / HarmonyOS 4.2 |
| Android 13 / API 33 | Expanded 41-test automated baseline passed on OPPO A58 5G / ColorOS 13 |
| Android 14 / API 34 | Current 41-test suite passed on official Google APIs emulator |
| Android 15 / API 35 | Expanded 41-test automated baseline passed on Redmi 23049RAD8C / HyperOS 3.0 |
| Android 16 / API 36 | Current expanded 41-test suite passed on official Google APIs emulator |
| Xiaomi / Redmi HyperOS | Partial pass; full manual scenario matrix remains |
| OPPO / OnePlus ColorOS | Partial pass on OPPO A58 5G; full manual scenario matrix remains |
| Huawei HarmonyOS (supplemental) | Automated baseline and signed Release lifecycle smoke passed; does not replace Honor / MagicOS |
| vivo / iQOO OriginOS | Not run |
| Honor MagicOS | Not run |
| Samsung One UI | Not run |
| Physical Google Pixel | Not run |

## 8. Environment blockers

The SDK manager remained unreliable, so official API 29, 30, and 34 Google APIs
archives were retrieved in validated byte ranges, checked as complete ZIPs, and
installed without changing the project toolchain. The former Android-version
coverage blocker is closed. A partial API 35 installer directory is not counted;
Android 15 is covered by the Redmi physical device.

Physical OEM coverage additionally requires the remaining listed families to
be connected with USB debugging enabled. The validated Huawei / HarmonyOS
device adds Android 12 and supplemental compatibility evidence but does not
replace the required Honor / MagicOS row. Synthetic media should be used; no
private user media is required.

## 9. Defect gate

- P0 found in tested API 29/30/34/36 AVDs and API 31 Huawei, API 33 OPPO, API 35 Redmi: 0
- P1 found in tested API 29/30/34/36 AVDs and API 31 Huawei, API 33 OPPO, API 35 Redmi: 0
- P2 application defects found: 0
- P3 application defects found: 0
- Resolved test-only portability/environment defects: 5 (Compose viewport assumptions, MediaStore URI representation, API capability assumption, and API 29 AVD heap configuration)
- Global P0/P1 clearance: not determinable because required coverage is incomplete
- Application behavior changed by R2 work: no
- Production Manifest or permissions changed: no
- Production scanner or cleanup logic changed: no

## 10. R2 completion requirements

Before R2 may be accepted:

1. Complete the manual Huawei, Redmi, and OPPO scenarios and execute the full scenario
   set on the other two P0 OEM families.
2. Execute the P1 Samsung and physical Pixel coverage, or record an approved,
   explicit release-risk decision where the execution plan permits it.
3. Attach per-device metadata, media counts, scenario evidence, and OEM-specific
   deviations to the device matrix.
4. Record and resolve every P0/P1 application defect; explicitly decide every P2.
5. Re-run the complete build, JVM tests, lint, and instrumented regression suite.
6. Only then check R2 in `RELEASE-CHECKLIST.md`.

## 11. Gate decision

`CONDITIONAL CONTINUATION TO R3 APPROVED — R2 REMAINS OPEN`
