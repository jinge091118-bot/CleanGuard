# CleanGuard V0.2 OEM Device Validation Report

Generated: 2026-10-07 (Asia/Shanghai)

## 1. Outcome

R2 has started but is not complete. The available Android 16 / API 36 official
emulator passed the complete 39-test instrumented suite, a signed-release
compatibility smoke, and targeted platform checks. The required Android 10-15
coverage and every required physical OEM family remain unavailable.

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
- R2-built signed Release APK: 25,277,457 bytes; SHA-256 `B69C1B6962B3E4BDB8C964AEA976759C93CBCA13FA8ABD9BC83227945A4B4815`
- R2-built signed Release AAB: 8,729,186 bytes; SHA-256 `CA463F16BF680FA807D2583E8888EB26AE8DD75D33C7CC9B26466A5B9D77C5D5`

The RC tag remains immutable. The R2 test addition is test-only and does not
alter production behavior. These are the hashes of the artifacts regenerated
during R2; the R1 report retains its own historical build hashes.

## 3. Available device inventory

One device was available:

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
| Test time | 2026-10-07, Asia/Shanghai |

No physical Android device was connected. The only valid local system image was
API 36. Therefore no result in this report may be generalized to OEM-specific
behavior.

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
- Build result: successful; 148 tasks, 10 executed and 138 up-to-date

### API 36 instrumented suite

```text
gradlew connectedDebugAndroidTest --offline --stacktrace
```

- Tests: 39
- Passed: 39
- Failed: 0
- Errors: 0
- Skipped: 0
- Suite time: 42.735 seconds
- Gradle result: `BUILD SUCCESSFUL` in 57 seconds

The suite includes one new R2 platform test. It inserts three synthetic,
app-owned rows into the real Android MediaStore (image, video, and audio), runs
the existing `MediaStoreScanner`, verifies category, URI, size, and relative
path, and removes every fixture in a `finally` block. It does not rewrite the
scanner, access private user content, or add deletion capability.

## 5. Signed-release and system smoke on API 36

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

## 6. Required scenario status

| Scenario group | API 36 status | Remaining work |
|---|---|---|
| Install / first launch | Partial pass | Reinstall data-state behavior and signed upgrade path require explicit physical-device runs |
| Permissions | Partial pass | Partial photos, actual SAF grant/revoke, OEM permission dialogs, and cross-version behavior remain |
| Scanning | Partial pass | Real MediaStore scanner verified with three synthetic rows; no-change incremental, add/delete/modify, large file, APK, screenshot, screen recording, and Downloads datasets remain |
| Duplicate detection | Automated regression only | Quick hash, full SHA-256, exact/nonduplicate, cancellation, and large-video flows require device datasets |
| Cleanup safety | Automated regression only | REVIEW default-off and DANGER/UNKNOWN protection remain green in tests; real trash confirmation, cancellation, success, undo, and undo-cancel require physical-device execution |
| Lifecycle | Partial pass | Background, lock/unlock, rotation, and process recreation passed; OEM system reclaim and cancellation during live scans remain |
| OEM behavior | Not run | All P0 and P1 OEM families remain |

## 7. Android-version and OEM matrix

| Coverage | Result |
|---|---|
| Android 10 / API 29 | Not run |
| Android 11 / API 30 | Not run |
| Android 12 / API 31 or 32 | Not run |
| Android 13 / API 33 | Not run |
| Android 14 / API 34 | Not run |
| Android 15 / API 35 | Not run |
| Android 16 / API 36 | Limited pass on official emulator |
| Xiaomi / Redmi HyperOS | Not run |
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
They must be resolved before the Android 10-15 emulator portion can run.

Physical OEM coverage additionally requires each listed family to be connected
with USB debugging enabled. Synthetic media should be used; no private user
media is required.

## 9. Defect gate

- P0 found in tested API 36 environment: 0
- P1 found in tested API 36 environment: 0
- P2 found in tested API 36 environment: 0
- P3 found in tested API 36 environment: 0
- Global P0/P1 clearance: not determinable because required coverage is incomplete
- Application behavior changed by R2 work: no
- Production Manifest or permissions changed: no
- Production scanner or cleanup logic changed: no

## 10. R2 completion requirements

Before R2 may be accepted:

1. Restore SDK repository access and execute Android 10-15 coverage.
2. Connect and execute the full scenario set on all four P0 OEM families.
3. Execute the P1 Samsung and physical Pixel coverage, or record an approved,
   explicit release-risk decision where the execution plan permits it.
4. Attach per-device metadata, media counts, scenario evidence, and OEM-specific
   deviations to the device matrix.
5. Record and resolve every P0/P1 application defect; explicitly decide every P2.
6. Re-run the complete build, JVM tests, lint, and instrumented regression suite.
7. Only then check R2 in `RELEASE-CHECKLIST.md`.

## 11. Gate decision

`NOT READY FOR R3`
