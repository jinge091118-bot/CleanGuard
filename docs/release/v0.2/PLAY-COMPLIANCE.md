# CleanGuard V0.2 Play Compliance

Status: **Drafts complete — pending project-owner approval**

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

- [x] Privacy Policy draft — `PRIVACY-POLICY.md`
- [ ] Privacy Policy owner-approved public URL — **blocking for submission**
- [x] Google Play Data Safety draft — `DATA-SAFETY.md`
- [x] Photo / Video Permission Declaration draft — `PHOTO-VIDEO-PERMISSION-DECLARATION.md`
- [x] In-app permission rationale review — `IN-APP-PERMISSION-RATIONALE-REVIEW.md`
- [x] Reviewer notes — `REVIEWER-NOTES.md`
- [x] Store listing statements verified against actual behavior — `STORE-LISTING.md`
- [x] Compliance fact audit with code evidence — `COMPLIANCE-AUDIT.md`

## R4 remaining before submission

These require the project owner or a hosted location, not further code work:

1. Approve the privacy policy text and host it at a public URL.
2. Provide a support contact address.
3. Complete the `PACKAGE_USAGE_STATS` sensitive-permission application in Play Console. The
   usage rationale is pre-drafted in `DATA-SAFETY.md`.
4. Export the 512x512 icon, the 1024x500 feature graphic, and at least two phone screenshots.
   The screenshot list is specified in `STORE-LISTING.md`; capturing them needs a physical
   device with a non-private demo dataset.
5. Fill the content rating questionnaire in Play Console.

## In-application corrections made during R4

The compliance audit found two in-application statements that were incomplete or
over-broad. Both were corrected in this session:

- The settings privacy note said only that CleanGuard does not upload files. It now also
  discloses that the Android system trash and the user-triggered system share and preview
  actions are the only paths through which data can leave the device.
- The home screen said the app "does not modify any files". The statement is now scoped to
  the scan step, which is accurate, because cleanup does move items into the system trash.

No scanning, cleanup, permission-request, or safety-rule behavior was changed. The new
in-application privacy note also states which fields a routine scan reads and that the
duplicate check computes SHA-256 in a streaming fashion.

The `Phase9UiTest` privacy-text assertion was updated to the new wording, and two new
assertions were added so the privacy disclosure content stays under automated regression
coverage.

## Consistency verification

The privacy policy, Data Safety draft, permission declaration, reviewer notes, and store
listing were written against a single code audit and cross-checked against each other.
Statements that the code does not support are collected in the "must not claim" section of
`COMPLIANCE-AUDIT.md` and are excluded from every published document.

No Play Console submission is authorized by this file. R4 must stop for project-owner
approval before external submission.
