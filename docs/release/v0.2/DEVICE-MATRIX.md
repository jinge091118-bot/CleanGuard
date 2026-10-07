# CleanGuard V0.2 Device Matrix

Status: **R2 in progress — not accepted**

The API 36 official emulator baseline is green. Android 10-15 and all required
physical OEM families remain untested, so this matrix does not satisfy the R2
release gate.

## Required Android coverage

| Android | API | Device / AVD | OEM / ROM | Result | Evidence |
|---|---:|---|---|---|---|
| Android 10 | 29 | Pending | Pending | Not run | SDK image unavailable locally; repository download failed |
| Android 11 | 30 | Pending | Pending | Not run | SDK image unavailable locally; repository download failed |
| Android 12 | 31/32 | Pending | Pending | Not run | SDK image unavailable locally; repository download failed |
| Android 13 | 33 | Pending | Pending | Not run | SDK image unavailable locally; repository download failed |
| Android 14 | 34 | Pending | Pending | Not run | SDK image unavailable locally; repository download failed |
| Android 15 | 35 | Pending | Pending | Not run | SDK image unavailable locally; repository download failed |
| Android 16 | 36 | CleanGuard_API_36 AVD | Google APIs / stock-like | Pass (limited) | 39/39 on 2026-10-07; ADB lifecycle and permission smoke passed |

## Required OEM coverage

| Priority | OEM family | Target ROM | Device | Result | Blocking issue |
|---|---|---|---|---|---|
| P0 | Xiaomi / Redmi | HyperOS | Pending | Not run | No physical device connected |
| P0 | OPPO / OnePlus | ColorOS | Pending | Not run | No physical device connected |
| P0 | vivo / iQOO | OriginOS | Pending | Not run | No physical device connected |
| P0 | Honor | MagicOS | Pending | Not run | No physical device connected |
| P1 | Samsung | One UI | Pending | Not run | No physical device connected |
| P1 | Google Pixel | Stock Android | Pending | Not run | No physical device connected; API 36 AVD is not a substitute for physical-device behavior |

## Executed device record

| Field | Value |
|---|---|
| Manufacturer / model | Google / `sdk_gphone64_x86_64` |
| Device type | Official Android Virtual Device, `CleanGuard_API_36` |
| Android / API | Android 16 / API 36 |
| Display | 1080 x 2400 |
| RAM | 2,531,836 kB reported by guest |
| Data partition | 10,167,132 kB total; 8,542,536 kB available at capture |
| Test time | 2026-10-07, Asia/Shanghai |
| App identity | `com.cleanguard.app`, version code 2, version name 0.2.0 |
| Automated result | 39 passed, 0 failed, 0 errors, 0 skipped; 42.735 seconds |
| Media fixture count | 3 synthetic app-owned rows: image, video, and audio |

## Per-device evidence template

Record brand, model, ROM, Android API, RAM, storage capacity/free space, media count, test time, version code, installation and upgrade behavior, permission states, full/incremental scans, duplicate verification, cleanup/trash/undo, lifecycle recovery, crashes, ANRs, and OEM-specific deviations.

Detailed results and remaining blockers are recorded in
`OEM-DEVICE-VALIDATION-REPORT.md`.
