# CleanGuard V0.2 OEM Device Validation Report

Generated: 2026-10-10 (Asia/Shanghai)

## 1. Outcome

R2 has started but is not complete. The Android 16 / API 36 official emulator
passed the 39-test suite available on 2026-10-08. A Redmi 23049RAD8C physical
device running Android 15 / API 35 and HyperOS 3.0 passed the expanded current
41-test suite on 2026-10-10, including real incremental MediaStore mutations
and duplicate hash/cache validation. The Redmi also passed signed-release
installation, permission-denied launch, lifecycle smoke, and targeted Android
system-routing checks.

Android 10-14, an API 36 rerun of the two added tests, the remaining P0/P1 OEM
families, and the manual permission-dialog, large/special dataset, trash, undo,
and long-running Redmi scenarios remain.

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
- Latest R2-built signed Release APK: 25,277,457 bytes; SHA-256 `C538BA2305288EF36437DDA173D24BED66D014BB3522023B7C6BA36070D4191A`
- Latest R2-built signed Release AAB: 8,729,187 bytes; SHA-256 `2C3ED85FB0FE1707A8D1270D824536C71E84D128F122F411430A5F51F8C8215F`

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
| Test time | 2026-10-08 and expanded rerun 2026-10-10, Asia/Shanghai |
| Media inventory | Private media count not collected; six synthetic app-owned rows were created across isolated tests and deleted afterward |

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
- Release APK signature: verified with APK Signature Scheme v2; signer certificate SHA-256 `c15dce5c840e6c7383d470340d51da1c9d1df9ddb3fdef3c56421b9f523b5d35`
- Build result: successful; final verification 148 tasks, 2 executed and 146 up-to-date

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
- Suite time: 48.514 seconds
- Result: the 39/39 pre-expansion suite passed after the cold-started emulator finished system initialization

Two R2-only platform tests were added after this run. They pass on the Redmi,
but API 36 must be rerun before the emulator row can claim the current 41-test
suite.

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

## 6. Required scenario status

| Scenario group | Current API 35/36 status | Remaining work |
|---|---|---|
| Install / first launch | Partial pass on API 35 physical device and API 36 AVD | Signed upgrade path and retained-data reinstall behavior remain |
| Permissions | Partial pass | Redmi deny-all and system routing passed; partial photos, actual SAF grant/revoke, OEM dialogs, and cross-version behavior remain |
| Scanning | Partial pass | Real MediaStore full, unchanged incremental, modify, and delete reconciliation passed using app-owned rows without media permission; large file, APK, screenshot, screen recording, and Downloads datasets remain |
| Duplicate detection | Partial real-platform pass | Exact duplicate quick hash, full SHA-256, cache reuse, and stale-version invalidation passed; nonduplicate, cancellation, and large-video flows remain |
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
| Android 15 / API 35 | Expanded 41-test automated baseline passed on Redmi 23049RAD8C / HyperOS 3.0 |
| Android 16 / API 36 | Pre-expansion 39-test suite passed on official emulator; two added tests need rerun |
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
- Resolved test-only portability defects: 2 (LazyColumn selector and MediaStore URI representation assumptions)
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
