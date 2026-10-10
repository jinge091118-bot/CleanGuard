# CleanGuard V0.2 Release Bug Tracker

Status: **No P0/P1 issue observed on the tested API 33 OPPO, API 35 Redmi, or API 36 AVD; R2 coverage incomplete**

## Severity policy

- P0: data loss, wrong deletion, crash loop, DANGER/UNKNOWN deletion, permission bypass, or database corruption.
- P1: scan failure, trash/restore failure, severe ANR, or an unusable primary screen.
- P2: reproducible OEM incompatibility, significant performance regression, or major UI error.
- P3: minor UI, copy, or non-core experience issue.

## Active bugs

| ID | Severity | Environment | Reproduction | Expected / actual | Owner | Status | Regression evidence |
|---|---|---|---|---|---|---|---|
| - | - | API 33 OPPO / ColorOS 13, API 35 Redmi / HyperOS 3.0, and API 36 official AVD | - | OPPO and Redmi expanded suites passed 41/41; API 36 pre-expansion suite passed 39/39; no application defect recorded | - | No active application bug | Limited to the three tested environments; API 36 still needs the two added R2 tests rerun |

## Resolved R2 test issue

| ID | Type | Environment | Cause | Resolution | Regression evidence |
|---|---|---|---|---|---|
| R2-TEST-01 | Test portability | Redmi 23049RAD8C / HyperOS 3.0 | LazyColumn test expected an off-screen item to already exist in the semantics tree | Select the LazyColumn by its scroll-to-index action, scroll to the same required text, then assert it is displayed | Targeted test passed; complete 39/39 passed on Redmi and API 36 AVD |
| R2-TEST-02 | Test portability | Redmi 23049RAD8C / HyperOS 3.0 | MediaStore insertion returned the generic `external` URI while volume-aware incremental queries returned `external_primary`; string equality did not represent item identity | Match the same synthetic row by its stable MediaStore numeric ID while retaining URI checks in the direct scanner test | Targeted 3/3 platform tests and complete 41/41 Redmi suite passed |

This issue changed test navigation only. No production UI, scanner, cleanup,
permission, or safety behavior changed.

## R2 coverage blockers

These are validation-environment blockers, not confirmed CleanGuard defects.

| ID | Scope | Blocker | Impact | Status |
|---|---|---|---|---|
| R2-ENV-01 | Android 10-12 and 14 | Required SDK system images are unavailable locally; both SDK managers failed to retrieve valid packages | Cannot execute the remaining Android-version matrix | Open |
| R2-ENV-02 | Remaining OEM matrix | No vivo/iQOO, Honor, Samsung, or physical Pixel has been validated | Cannot determine compatibility for the remaining required OEM families | Open |
| R2-COV-01 | Redmi and OPPO manual scenarios | Expanded automated baselines passed, but partial-photo selection, actual SAF grant/revoke, large/special datasets, trash confirmation, undo, cancellation, OEM reclaim, and OPPO manual rotation remain | Redmi and OPPO P0 rows cannot be declared complete | Open |

P0 and P1 release gates cannot be declared globally clear until the required OEM
and Android-version matrix is executed.

R8 may fix only release-blocking bugs. Every fix requires reproduction, root-cause evidence, regression tests, OEM verification where relevant, and a new immutable RC tag.
