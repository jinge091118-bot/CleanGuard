# CleanGuard V0.2 Play Compliance

Status: **Pending R4 project-owner approval**

## Current privacy and permission facts

- CleanGuard performs storage analysis locally.
- CleanGuard does not upload user files.
- CleanGuard has no `INTERNET` permission.
- CleanGuard has no analytics or advertising SDK.
- Media access is used for the core storage-analysis and cleanup function.
- Photo Picker alone cannot provide a complete device-wide storage analysis.
- Cleanup remains user-selected and follows Android confirmation/trash behavior.

## Declared permissions

- `READ_EXTERNAL_STORAGE` with `maxSdkVersion=32`
- `READ_MEDIA_IMAGES`
- `READ_MEDIA_VIDEO`
- `READ_MEDIA_AUDIO`
- `READ_MEDIA_VISUAL_USER_SELECTED`
- `PACKAGE_USAGE_STATS` protected access for app-storage insights

## R4 deliverables

- [ ] Privacy Policy draft and owner-approved public URL
- [ ] Google Play Data Safety draft
- [ ] Photo / Video Permission Declaration draft
- [ ] In-app permission rationale review
- [ ] Reviewer notes
- [ ] Store listing statements verified against actual behavior

No Play Console submission is authorized by this file. R4 must stop for project-owner approval before external submission.
