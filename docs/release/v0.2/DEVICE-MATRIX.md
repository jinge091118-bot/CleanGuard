# CleanGuard V0.2 Device Matrix

Status: **Pending R2**

R0 creates this controlled record but does not claim OEM or physical-device coverage.

## Required Android coverage

| Android | API | Device / AVD | OEM / ROM | Result | Evidence |
|---|---:|---|---|---|---|
| Android 10 | 29 | Pending | Pending | Not run | Pending R2 |
| Android 11 | 30 | Pending | Pending | Not run | Pending R2 |
| Android 12 | 31/32 | Pending | Pending | Not run | Pending R2 |
| Android 13 | 33 | Pending | Pending | Not run | Pending R2 |
| Android 14 | 34 | Pending | Pending | Not run | Pending R2 |
| Android 15 | 35 | Pending | Pending | Not run | Pending R2 |
| Android 16 | 36 | CleanGuard_API_36 AVD | AOSP | Instrumented tests passed | 38/38 on 2026-10-07 |

## Required OEM coverage

| Priority | OEM family | Target ROM | Device | Result | Blocking issue |
|---|---|---|---|---|---|
| P0 | Xiaomi / Redmi | HyperOS | Pending | Not run | Pending R2 |
| P0 | OPPO / OnePlus | ColorOS | Pending | Not run | Pending R2 |
| P0 | vivo / iQOO | OriginOS | Pending | Not run | Pending R2 |
| P0 | Honor | MagicOS | Pending | Not run | Pending R2 |
| P1 | Samsung | One UI | Pending | Not run | Pending R2 |
| P1 | Google Pixel | Stock Android | Pending | Not run | Pending R2 |

## Per-device evidence template

Record brand, model, ROM, Android API, RAM, storage capacity/free space, media count, test time, version code, installation and upgrade behavior, permission states, full/incremental scans, duplicate verification, cleanup/trash/undo, lifecycle recovery, crashes, ANRs, and OEM-specific deviations.
