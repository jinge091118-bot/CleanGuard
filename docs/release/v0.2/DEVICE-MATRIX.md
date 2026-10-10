# CleanGuard V0.2 Device Matrix

Status: **R2 in progress — not accepted**

The API 36 official emulator plus Redmi / HyperOS and OPPO / ColorOS physical-
device baselines are green. Android 10-12 and 14, the remaining required OEM
families, and the manual Redmi and OPPO scenario sets remain incomplete, so
this matrix does not satisfy the R2 release gate.

## Required Android coverage

| Android | API | Device / AVD | OEM / ROM | Result | Evidence |
|---|---:|---|---|---|---|
| Android 10 | 29 | Pending | Pending | Not run | SDK image unavailable locally; repository download failed |
| Android 11 | 30 | Pending | Pending | Not run | SDK image unavailable locally; repository download failed |
| Android 12 | 31/32 | Pending | Pending | Not run | SDK image unavailable locally; repository download failed |
| Android 13 | 33 | OPPO A58 5G (`PHJ110`) | ColorOS 13 | Pass (expanded automated baseline) | 41/41 on 2026-10-10; targeted MediaStore 3/3; signed Release smoke passed |
| Android 14 | 34 | Pending | Pending | Not run | SDK image unavailable locally; repository download failed |
| Android 15 | 35 | Redmi 23049RAD8C (`marble`) | HyperOS 3.0 | Pass (expanded automated baseline) | 41/41 on 2026-10-10; real incremental mutation and duplicate-hash flows passed; signed Release smoke passed |
| Android 16 | 36 | CleanGuard_API_36 AVD | Google APIs / stock-like | Pass (limited) | Pre-expansion 39/39 suite passed on 2026-10-08; ADB lifecycle and permission smoke passed; two new R2 platform tests still require an API 36 rerun |

## Required OEM coverage

| Priority | OEM family | Target ROM | Device | Result | Blocking issue |
|---|---|---|---|---|---|
| P0 | Xiaomi / Redmi | HyperOS | Redmi 23049RAD8C | Partial pass | Expanded automated suite and system smoke passed; permission-dialog, large/special datasets, trash, undo, and long-running manual scenarios remain |
| P0 | OPPO / OnePlus | ColorOS | OPPO A58 5G (`PHJ110`) | Partial pass | Expanded automated suite and system smoke passed; manual permission, dataset, trash, undo, rotation, and long-running scenarios remain |
| P0 | vivo / iQOO | OriginOS | Pending | Not run | No physical device connected |
| P0 | Honor | MagicOS | Pending | Not run | No physical device connected |
| P1 | Samsung | One UI | Pending | Not run | No physical device connected |
| P1 | Google Pixel | Stock Android | Pending | Not run | No physical device connected; API 36 AVD is not a substitute for physical-device behavior |

## Executed device records

### API 36 official AVD

| Field | Value |
|---|---|
| Manufacturer / model | Google / `sdk_gphone64_x86_64` |
| Device type | Official Android Virtual Device, `CleanGuard_API_36` |
| Android / API | Android 16 / API 36 |
| Display | 1080 x 2400 |
| RAM | 2,531,836 kB reported by guest |
| Data partition | 10,167,132 kB total; 8,542,536 kB available at capture |
| Test time | 2026-10-08, Asia/Shanghai |
| App identity | `com.cleanguard.app`, version code 2, version name 0.2.0 |
| Automated result | Current code: 39 passed, 0 failed, 0 errors, 0 skipped; 48.514 seconds |
| Media fixture count | 3 synthetic app-owned rows: image, video, and audio |

### Redmi / HyperOS physical device

| Field | Value |
|---|---|
| Serial | `be250aeb` |
| Manufacturer / model | Xiaomi Redmi / `23049RAD8C` (`marble`) |
| System | Android 15 / API 35; HyperOS 3.0; build `OS3.0.5.0.VMRCNXM` |
| Security patch | 2026-05-01 |
| Display | 1080 x 2400 |
| RAM | 11,427,864 kB reported by guest |
| Shared storage | 485,010,412 kB total; 324,316,820 kB available at capture |
| Battery at inventory | 76%; charging; 36.4 C |
| Test time | 2026-10-08, Asia/Shanghai; expanded rerun 2026-10-10 |
| App identity | `com.cleanguard.app`, version code 2, version name 0.2.0 |
| Automated result | 41 passed, 0 failed, 0 errors, 0 skipped; 27.997 seconds |
| Media fixture count | 6 synthetic app-owned rows created across isolated tests (maximum 3 concurrent); private media inventory intentionally not collected |
| Added platform evidence | Full -> unchanged incremental -> modify -> delete reconciliation; exact duplicate quick/full hash; cache reuse and stale-version invalidation |
| Release state after test | Test packages and empty fixture directories removed; signed Release APK restored; final cold start 549 ms; media permissions denied |
| OEM prerequisite | HyperOS per-app “后台弹出界面” was required only for the instrumentation host Activity |

### OPPO / ColorOS physical device

| Field | Value |
|---|---|
| Serial | `RO8HFIPZUO7HOVCU` |
| Manufacturer / model | OPPO / OPPO A58 5G (`PHJ110`, `OP526D`) |
| System | Android 13 / API 33; ColorOS 13; build `PHJ110_11_C.20` |
| Security patch | 2026-01-01 |
| Display | 720 x 1612 |
| RAM | 5,753,468 kB reported by guest |
| Shared storage | 111,892,448 kB total; 80,390,040 kB available at inventory |
| Battery at inventory | 41%; USB charging; approximately 32.0 C |
| Test time | 2026-10-10, Asia/Shanghai |
| App identity | `com.cleanguard.app`, version code 2, version name 0.2.0 |
| Automated result | 41 passed, 0 failed, 0 errors, 0 skipped; 53.463 seconds; targeted MediaStore 3/3 in 3.540 seconds |
| Media fixture count | 6 synthetic app-owned rows created across isolated runs (maximum 3 concurrent); private media inventory intentionally not collected |
| Added platform evidence | Full -> unchanged incremental -> modify -> delete reconciliation; exact duplicate quick/full hash; cache reuse and stale-version invalidation |
| Release state after test | Test package, synthetic rows, fixture directories, and diagnostic files removed; signed Release restored; final cold start 535 ms; media permissions denied |
| OEM deviation | First main-APK installs required ColorOS “安装增强防护” deep scan; ADB rotation was blocked by shell `WRITE_SETTINGS` policy and remains a manual scenario |

## Per-device evidence template

Record brand, model, ROM, Android API, RAM, storage capacity/free space, media count, test time, version code, installation and upgrade behavior, permission states, full/incremental scans, duplicate verification, cleanup/trash/undo, lifecycle recovery, crashes, ANRs, and OEM-specific deviations.

Detailed results and remaining blockers are recorded in
`OEM-DEVICE-VALIDATION-REPORT.md`.
