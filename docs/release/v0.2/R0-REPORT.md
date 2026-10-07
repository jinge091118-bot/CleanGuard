# CleanGuard V0.2 R0 Report

Generated: 2026-10-07 (Asia/Shanghai)

## 1. Outcome

R0 - Code Freeze & Release Baseline is complete. CleanGuard V0.2 is feature-frozen and has an immutable release-candidate code baseline. No production feature, permission, dependency, database schema, or cleanup behavior changed during R0.

## 2. Git baseline

- Branch: `feature/v0.2`
- Audited current commit / RC target: `2694f256eeb490fb9f26ad696fb5546c30109611`
- Commit subject: `docs: complete v0.2 release acceptance`
- Initial status: clean
- Annotated tag: `v0.2.0-rc1`
- Tag object SHA: `775a9bf509f6dc03925874cf1d66fcacdeeda4d7`
- Tag target commit: `2694f256eeb490fb9f26ad696fb5546c30109611`
- Tag time: `2026-10-07 14:47:39 +0800`
- Tag message: `CleanGuard V0.2 Release Candidate 1 - engineering acceptance passed`

The R0 documentation is a documentation-only follow-up on `feature/v0.2`; the RC tag intentionally identifies the already accepted Phase 11 code commit.

## 3. Complete regression results

Command:

```text
gradlew clean test lint assembleDebug assembleRelease bundleRelease assembleDebugAndroidTest --offline --stacktrace
```

- Build: successful in 1 minute 27 seconds
- Debug JVM tests: 109/109 passed; 0 failed, 0 errors, 0 skipped
- Release JVM tests: 109/109 passed; 0 failed, 0 errors, 0 skipped
- Lint: `No issues found.`
- API 36 / Android 16 instrumented tests: 38/38 passed; 0 failed, 0 errors, 0 skipped
- V0.1 safety behavior remains covered: DANGER and UNKNOWN cannot be cleaned, REVIEW is not selected by default, MediaStore trash/undo and cancellation behavior remain unchanged.

## 4. Build and release artifacts

| Artifact | Bytes | SHA-256 | Status |
|---|---:|---|---|
| `app-debug.apk` | 33,796,505 | `2BD3EA447CAD10220BAB06C7EABAD606A8044DCB1856AC979F28398392FAD6E1` | Debug-signed; generated |
| `app-release-unsigned.apk` | 25,265,169 | `F8482BD5E167A0C51DA902DC922760392DD8B8FACE341C0FBB361746876C5DF9` | Generated; intentionally unsigned |
| `app-release.aab` | 8,719,349 | `5B29675385A0712FA888F8A232E5F3E7350451DD4AA01C0D25FFB85C1EE178CB` | Generated; production signing pending R1 |
| `app-debug-androidTest.apk` | 2,795,272 | `01F7F849C58192AF0DFAF87A73632C8005DBBD33D3716BAF161F08026AF1B40A` | Generated |

Artifact metadata confirms application ID `com.cleanguard.app`, version code `2`, version name `0.2.0`, minSdk `29`, targetSdk `36`, and compileSdk `36`.

## 5. Manifest permission check

The source Manifest has no diff from tag `v0.1.0-rc1`.

Current permissions:

- `READ_EXTERNAL_STORAGE` with `maxSdkVersion=32`
- `READ_MEDIA_IMAGES`
- `READ_MEDIA_VIDEO`
- `READ_MEDIA_AUDIO`
- `READ_MEDIA_VISUAL_USER_SELECTED`
- `PACKAGE_USAGE_STATS`

Confirmed absent: `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_MEDIA`, accessibility service, Root/Shizuku configuration, analytics, ads, upload behavior, and automatic permanent deletion.

## 6. Git diff and behavior change

- R0 adds only the controlled release documentation under `docs/release/v0.2/`.
- Production source, tests, Gradle dependencies, Room schemas, and Manifest are unchanged.
- No business behavior changed.
- No permission expanded.

## 7. Warnings and release blockers

Non-blocking build warnings:

- `android.overridePathCheck=true` is experimental.
- `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` could not be stripped and were packaged as-is.

R0 release-blocking issues: none.

The unsigned Release APK/AAB cannot be publicly distributed. This is not an R0 failure; establishing and validating the protected signing system is the explicit scope of R1.

`READY FOR R1`
