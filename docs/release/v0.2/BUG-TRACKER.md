# CleanGuard V0.2 Release Bug Tracker

Status: **No P0/P1 issue observed in the tested API 36 environment; R2 coverage incomplete**

## Severity policy

- P0: data loss, wrong deletion, crash loop, DANGER/UNKNOWN deletion, permission bypass, or database corruption.
- P1: scan failure, trash/restore failure, severe ANR, or an unusable primary screen.
- P2: reproducible OEM incompatibility, significant performance regression, or major UI error.
- P3: minor UI, copy, or non-core experience issue.

## Active bugs

| ID | Severity | Environment | Reproduction | Expected / actual | Owner | Status | Regression evidence |
|---|---|---|---|---|---|---|---|
| - | - | API 36 official AVD | - | 39/39 instrumented tests passed; no application defect recorded | - | No active application bug | Limited to the tested environment |

## R2 coverage blockers

These are validation-environment blockers, not confirmed CleanGuard defects.

| ID | Scope | Blocker | Impact | Status |
|---|---|---|---|---|
| R2-ENV-01 | Android 10-15 | Required SDK system images are unavailable locally; both SDK managers failed to retrieve valid packages | Cannot execute the remaining official-emulator Android-version matrix | Open |
| R2-ENV-02 | OEM matrix | No Xiaomi/Redmi, OPPO/OnePlus, vivo/iQOO, Honor, Samsung, or physical Pixel is connected | Cannot determine OEM permission, MediaStore, SAF, trash, lifecycle, or background-policy compatibility | Open |

P0 and P1 release gates cannot be declared globally clear until the required OEM
and Android-version matrix is executed.

R8 may fix only release-blocking bugs. Every fix requires reproduction, root-cause evidence, regression tests, OEM verification where relevant, and a new immutable RC tag.
