# CleanGuard V0.2 Release Checklist

This checklist is the release-control source for CleanGuard V0.2. Feature work is frozen. A gate may be marked complete only when its own report and evidence are present.

## Release identity

- Application ID: `com.cleanguard.app`
- Version name: `0.2.0`
- Current release-candidate version code: `2`
- Release branch: `feature/v0.2`
- RC tag: `v0.2.0-rc1`
- RC commit: `2694f256eeb490fb9f26ad696fb5546c30109611`

## Gate status

- [x] R0 - Code Freeze & Release Baseline
- [x] R1 - Production signing system
- [ ] R2 - OEM / Android device matrix
- [ ] R3 - Physical-device performance validation
- [x] R4 - Privacy Policy, Data Safety, and media-permission declarations
- [ ] R5 - Google Play Internal Test
- [ ] R6 - Closed Test / Beta
- [ ] R7 - Beta feedback review
- [ ] R8 - Release-blocking bug stabilization
- [ ] R9 - V0.2 production release
- [ ] R10 - V0.3 decision gate

R2 is intentionally left unchecked. On 2026-10-19 the project owner approved
conditional continuation to R3 because additional OEM devices were unavailable.
The scope, safeguards, and release limitation are recorded in
`R2-RISK-ACCEPTANCE.md`; no untested OEM row is treated as passed.

R3 remains unchecked. On 2026-10-23 the only device reachable for the R3
session reported itself as a Google Pixel 5 but proved to be a virtualized
Android 12 platform, so it cannot satisfy the physical-device requirement.
The 10k-tier dataset, the direct scanner measurement path, and the
100 add / 100 modify / 100 delete sequence were completed on that device and
are recorded in `PERFORMANCE-REPORT.md` as functional correctness evidence
only. No R3 performance row is satisfied, and the 50k and 100k tiers plus
both required physical device classes remain outstanding.

R4 is checked. On 2026-10-26 the project owner approved the privacy policy
text, the Data Safety answers, the photo/video permission declaration, the
reviewer notes, and the store listing. The privacy policy is published at
<https://jinge091118-bot.github.io/CleanGuard/privacy-policy.html> and the
support address is jinge091118@gmail.com. The remaining R4 items are Play
Console actions rather than documentation work: the `PACKAGE_USAGE_STATS`
sensitive-permission application, the store graphics, and the content rating
questionnaire.

## Permanent release safety checks

- [x] No new scanning or deletion capability was added during R0.
- [x] No source Manifest permission changed from the V0.1 baseline.
- [x] No `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `WRITE_EXTERNAL_STORAGE`, or `MANAGE_MEDIA` permission exists.
- [x] No Root, Shizuku, accessibility service, analytics, advertising SDK, upload path, or automatic permanent deletion was introduced.
- [x] V0.1 cleanup safety regression tests remain green.
- [x] Release APK and AAB are signed with the protected upload key.
- [ ] OEM and Android 10-16 validation is complete. Required in R2.
- [ ] Physical-device performance gates are complete. Required in R3.
- [x] Play compliance materials are approved and the privacy policy is published. Required in R4.

## Version-code rule

Never reuse a version code that has been uploaded to Google Play. If RC1 with version code `2` is uploaded, every later candidate must use a value greater than `2`.
