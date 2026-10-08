# CleanGuard V0.2 OEM Device Validation Report

Generated: 2026-10-08 (Asia/Shanghai)

## 1. Outcome

R2 has started but is not complete. The Android 16 / API 36 official emulator
and a Redmi 23049RAD8C physical device running Android 15 / API 35 and HyperOS
3.0 both passed the current complete 39-test instrumented suite. The Redmi also
passed signed-release installation, permission-denied launch, lifecycle smoke,
and targeted Android system-routing checks.

Android 10-14, the remaining P0/P1 OEM families, and the manual dataset,
permission-dialog, trash, undo, and long-running Redmi scenarios remain.

This is a partial evidence report. It does not approve R2 and it does not permit
entry into R3.

## 2. Baseline under test

- Application ID: `com.cleanguard.app`
- Version code: `2`
- Version name: `0.2.0`
- Branch: `feature/v0.2`
- RC tag: `v0.2.0-rc1`
- RC target: `2694f256eeb490fb9f26ad696fb5546c30109611`
- Current R1 documentation baseline before R2 changes: `2cb8b70`
- Latest R2-built signed Release APK: 25,277,457 bytes; SHA-256 `6BABD2EDDF0E15955C165AC607EEC260B1694FFBB82D98297A3641A342D90A01`
- Latest R2-built signed Release AAB: 8,729,186 bytes; SHA-256 `901441E73750C775E1595DDC06E8C4ADFC8F2AC3F89CAAD77BB12BFFB1557FF9`

The RC tag remains immutable. The R2 test changes are test-only and do not
alter production behavior. These are the hashes of the artifacts regenerated
during R2; the R1 report retains its own historical build hashes.

## 3. Available device inventory

Two validation targets were available.

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
| Test time | 2026-10-08, Asia/Shanghai |

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
| Test time | 2026-10-08, Asia/Shanghai |
| Media inventory | Private media count not collected; tests used three synthetic app-owned rows |

The API 36 image remains the only valid local emulator image. Redmi evidence is
valid for this exact model and HyperOS build and must not be generalized to all
Xiaomi devices or the remaining OEM families.

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
- Build result: successful; 148 tasks, 12 executed and 136 up-to-date

### API 36 instrumented suite

```text
adb -s emulator-5554 shell am instrument -w -r \
  com.cleanguard.app.test/androidx.test.runner.AndroidJUnitRunner
```

- Tests: 39
- Passed: 39
- Failed: 0
- Errors: 0
- Skipped: 0
- Current-code suite time: 48.514 seconds
- Result: 39/39 passed after the cold-started emulator finished system initialization

The first immediate post-boot attempt was discarded because the emulator was
still starting Google system processes and terminated the test process while
also reporting ANRs in unrelated system applications. After the emulator
reported boot and process readiness, the targeted UI test and complete suite
both passed.

### Redmi / HyperOS instrumented suite

```text
adb -s be250aeb shell am instrument -w -r \
  com.cleanguard.app.test/androidx.test.runner.AndroidJUnitRunner
```

- Tests: 39
- Passed: 39
- Failed: 0
- Errors: 0
- Skipped: 0
- Suite time: 16.148 seconds
- Real MediaStore platform test: passed
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

The suite's R2 platform test inserts three synthetic, app-owned rows into the
real Android MediaStore (image, video, and audio), runs the unchanged
`MediaStoreScanner`, verifies category, URI, size, and relative path, and
removes every fixture in a `finally` block. The test no longer grants any media
read permission, so it does not enumerate or read private user media.

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
| First launch with permissions denied | Cold start completed in 369 ms on first run and 423 ms after final reinstall | Pass |
| Permission state | Image, video, and audio permissions confirmed denied after final reinstall | Pass |
| Usage Access | HyperOS resolved `Settings$UsageAccessSettingsActivity` | Pass (routing only) |
| Downloads SAF | System resolved Google DocumentsUI `PickActivity` | Pass (routing only) |
| Background / resume | Home then Release relaunch; 84 ms hot launch | Pass |
| Rotation | Portrait/landscape setting changed and restored to original values | Pass |
| Lock / unlock | Sleep, wake, dismiss keyguard, and re-entry completed | Pass |
| Process-reclaim command | Background kill command followed by relaunch remained hot | Inconclusive; OEM reclaim not proven |
| Runtime health | No CleanGuard fatal exception, OOM, ANR, or database exception after Release smoke | Pass |

The phone was left with the signed Release APK installed, version code 2,
version name 0.2.0, and all media permissions denied. Test APKs were removed and
system animation scales were restored to their original values.

## 6. Required scenario status

| Scenario group | Current API 35/36 status | Remaining work |
|---|---|---|
| Install / first launch | Partial pass on API 35 physical device and API 36 AVD | Signed upgrade path and retained-data reinstall behavior remain |
| Permissions | Partial pass | Redmi deny-all and system routing passed; partial photos, actual SAF grant/revoke, OEM dialogs, and cross-version behavior remain |
| Scanning | Partial pass | Real MediaStore scanner verified without media permission using three app-owned rows; no-change incremental, add/delete/modify, large file, APK, screenshot, screen recording, and Downloads datasets remain |
| Duplicate detection | Automated regression only | Quick hash, full SHA-256, exact/nonduplicate, cancellation, and large-video flows require device datasets |
| Cleanup safety | Automated regression only | REVIEW default-off and DANGER/UNKNOWN protection remain green in tests; real trash confirmation, cancellation, success, undo, and undo-cancel require physical-device execution |
| Lifecycle | Partial pass | Redmi background, lock/unlock, and rotation passed; actual OEM system reclaim and cancellation during live scans remain |
| OEM behavior | Redmi automated baseline passed | Redmi manual scenario set plus OPPO/OnePlus, vivo/iQOO, Honor, Samsung, and physical Pixel remain |

## 7. Android-version and OEM matrix

| Coverage | Result |
|---|---|
| Android 10 / API 29 | Not run |
| Android 11 / API 30 | Not run |
| Android 12 / API 31 or 32 | Not run |
| Android 13 / API 33 | Not run |
| Android 14 / API 34 | Not run |
| Android 15 / API 35 | Automated baseline passed on Redmi 23049RAD8C / HyperOS 3.0 |
| Android 16 / API 36 | Current 39-test suite passed on official emulator |
| Xiaomi / Redmi HyperOS | Partial pass; full manual scenario matrix remains |
| OPPO / OnePlus ColorOS | Not run |
| vivo / iQOO OriginOS | Not run |
| Honor MagicOS | Not run |
| Samsung One UI | Not run |
| Physical Google Pixel | Not run |

## 8. Environment blockers

Attempts were made to install official API 29, 30, 31, 33, 34, and 35 emulator
images without changing the project toolchain. The existing SDK manager failed
while retrieving repository metadata and produced invalid ZIP downloads. The
new Android command-line manager also failed to connect to the remote
repository. A partial API 35 installer directory is not a valid system image and
is not counted as coverage.

These failures are host SDK/network blockers, not CleanGuard application bugs.
They must be resolved before the remaining Android 10-14 coverage can run.

Physical OEM coverage additionally requires the remaining listed families to
be connected with USB debugging enabled. The available Huawei device may add
useful compatibility evidence but does not replace the required Honor / MagicOS
row unless it is actually an Honor device running MagicOS. Synthetic media
should be used; no private user media is required.

## 9. Defect gate

- P0 found in tested API 35 Redmi and API 36 AVD environments: 0
- P1 found in tested API 35 Redmi and API 36 AVD environments: 0
- P2 application defects found: 0
- P3 application defects found: 0
- Resolved test-only portability defects: 1 LazyColumn selector assumption
- Global P0/P1 clearance: not determinable because required coverage is incomplete
- Application behavior changed by R2 work: no
- Production Manifest or permissions changed: no
- Production scanner or cleanup logic changed: no

## 10. R2 completion requirements

Before R2 may be accepted:

1. Restore SDK repository access and execute Android 10-14 coverage.
2. Complete the manual Redmi scenarios and execute the full scenario set on the
   other three P0 OEM families.
3. Execute the P1 Samsung and physical Pixel coverage, or record an approved,
   explicit release-risk decision where the execution plan permits it.
4. Attach per-device metadata, media counts, scenario evidence, and OEM-specific
   deviations to the device matrix.
5. Record and resolve every P0/P1 application defect; explicitly decide every P2.
6. Re-run the complete build, JVM tests, lint, and instrumented regression suite.
7. Only then check R2 in `RELEASE-CHECKLIST.md`.

## 11. Gate decision

`NOT READY FOR R3`
