# CleanGuard V0.2 Signing Report

Generated: 2026-10-07 (Asia/Shanghai)

## 1. Outcome

R1 - Production Signing System is complete. CleanGuard now uses a fail-closed Release signing flow backed by a project-owner-managed Upload Key stored outside the Git repository. Signed Release APK and AAB artifacts were generated, independently verified, and shown to use the same public certificate.

## 2. Release identity

- Application ID: `com.cleanguard.app`
- Version code: `2`
- Version name: `0.2.0`
- minSdk: `29`
- targetSdk: `36`
- compileSdk: `36`
- Branch: `feature/v0.2`
- R1 signing implementation commits: `1d3567b`, `41a3257`
- RC tag: `v0.2.0-rc1`
- RC tag target: `2694f256eeb490fb9f26ad696fb5546c30109611`

Version code `2` must never be reused after an artifact with this version code is uploaded to Google Play. A later RC must use a version code greater than `2`.

## 3. Signing architecture

- Recommended distribution model: Google Play App Signing plus an independent Upload Key.
- Gradle accepts signing values from protected `CLEANGUARD_UPLOAD_*` environment variables or an ignored local `key.properties` file.
- Environment variables override local properties when both are present.
- All four signing values are mandatory; partial configuration fails during Gradle configuration.
- `assembleRelease` and `bundleRelease` fail closed when signing is absent.
- The keystore must resolve outside the Git repository.
- No password, private key, signing secret, keystore path, or recoverable key material is recorded in this report.

## 4. Signed artifacts

| Artifact | Bytes | SHA-256 | Verification |
|---|---:|---|---|
| `app/build/outputs/apk/release/app-release.apk` | 25,277,457 | `32FF203E9057B5C024306A7E5CFCD905243338E6BCE70B2D7178DA74F1E617EA` | `apksigner` exit 0; APK Signature Scheme v2 |
| `app/build/outputs/bundle/release/app-release.aab` | 8,729,184 | `606A5478E68F8F120C6F3DA5FCD947649B2B158DAFF2B3D3B224E5EE2845B341` | `jarsigner` exit 0; JAR signature verified |

Upload Certificate SHA-256:

```text
C1:5D:CE:5C:84:0E:6C:73:83:D4:70:34:0D:51:DA:1C:9D:1D:F9:DD:B3:FD:EF:3C:56:42:1B:9F:52:3B:5D:35
```

The APK certificate digest and AAB certificate digest match exactly.

## 5. Build and test results

Clean command:

```text
gradlew clean test lint assembleDebug assembleRelease bundleRelease assembleDebugAndroidTest --offline --stacktrace
```

- Clean build: successful in 2 minutes 41 seconds
- Debug JVM tests: 109/109 passed; 0 failed, 0 errors, 0 skipped
- Release JVM tests: 109/109 passed; 0 failed, 0 errors, 0 skipped
- Lint: `No issues found.`
- API 36 / Android 16 instrumented tests: 38/38 passed; 0 failed, 0 errors, 0 skipped
- Signed Release APK installation on API 36: successful
- Signed Release cold start: successful, `LaunchState: COLD`, 1381 ms
- Runtime fatal markers after launch: none

The first instrumented-test invocation found no connected device and did not run tests. The existing `CleanGuard_API_36` AVD was then started and the complete 38-test suite passed; the successful XML suite is the acceptance evidence.

## 6. Manifest and regression checks

- Source Manifest has no diff from the R0 baseline.
- No production source file changed during R1.
- No permission was added or expanded.
- No cleanup, scanner, database, recommendation, duplicate-detection, history, or recovery behavior changed.
- V0.1 safety and cleanup regression coverage remains green in the full test suite.

## 7. Secret audit

- `key.properties` is ignored and untracked.
- Keystore extensions `.jks`, `.keystore`, `.p12`, and `.pfx` are ignored.
- `key.properties`, `keystore.properties`, and `signing.properties` are ignored.
- Android Studio export directory `/app/release/` is ignored.
- Tracked secret/key files: none.
- Git contains no password, private key, keystore, signing secret, or CI secret.
- At least two protected keystore backups remain an operational responsibility of the project owner.

## 8. Warnings

- `android.overridePathCheck=true` remains experimental.
- Android Studio and command-line tools emit an SDK XML v4/v3 compatibility warning.
- `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` could not be stripped and were packaged as-is.
- `jarsigner` reports standard JarFile/JarInputStream entry notices while returning exit code 0 and verifying the AAB signature.

No warning blocks R1 acceptance.

## 9. R1 gate

- Signing Secret not committed: passed
- Release AAB signed: passed
- Release APK signed: passed
- Independent signature validation: passed
- Release build: passed
- Existing tests: passed
- Manifest permission stability: passed
- Git secret audit: passed
- Release-blocking issue: none

`READY FOR R2`
