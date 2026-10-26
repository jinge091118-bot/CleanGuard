# CleanGuard V0.2 Play Compliance

Status: **Approved by the project owner on 2026-10-26 — ready for Play Console submission**

## Published compliance details

| Item | Value |
|---|---|
| Privacy policy URL | <https://jinge091118-bot.github.io/CleanGuard/privacy-policy.html> |
| Support email | jinge091118@gmail.com |
| Privacy policy source | `docs/release/v0.2/PRIVACY-POLICY.md` |
| Page generation | `tools/privacy-page/render.py`, published to the `gh-pages` branch |

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
- [x] Privacy Policy owner-approved public URL — published 2026-10-26
- [x] Google Play Data Safety draft — `DATA-SAFETY.md`
- [x] Photo / Video Permission Declaration draft — `PHOTO-VIDEO-PERMISSION-DECLARATION.md`
- [x] In-app permission rationale review — `IN-APP-PERMISSION-RATIONALE-REVIEW.md`
- [x] Reviewer notes — `REVIEWER-NOTES.md`
- [x] Store listing statements verified against actual behavior — `STORE-LISTING.md`
- [x] Compliance fact audit with code evidence — `COMPLIANCE-AUDIT.md`
- [x] Support contact address — jinge091118@gmail.com

## R4 remaining before submission

These require the Play Console itself rather than further code or documentation work:

1. Complete the `PACKAGE_USAGE_STATS` sensitive-permission application. The usage rationale is
   pre-drafted in `DATA-SAFETY.md`.
2. Fill the content rating questionnaire. Every answer is pre-drafted in
   `CONTENT-RATING-QUESTIONNAIRE.md`, with the code evidence for each "no" answer.
3. Export the 512x512 icon, the 1024x500 feature graphic, and at least two phone screenshots.
   The screenshot list is specified in `STORE-LISTING.md`; capturing them needs a physical
   device with a non-private demo dataset.
4. Fill the content rating questionnaire and set the target audience so the two are consistent.
   The recommended target audience and its rationale are in `CONTENT-RATING-QUESTIONNAIRE.md` §4.

The end-to-end path from here to a live listing, including the Google-mandated closed testing
window, is documented in `PUBLISHING-PATH.md`.

## Privacy policy hosting

The published page is generated from the policy source in this repository, so the public text
cannot drift from the reviewed source. To update the policy, edit
`docs/release/v0.2/PRIVACY-POLICY.md`, then regenerate and publish:

```bash
python3 tools/privacy-page/render.py \
  docs/release/v0.2/PRIVACY-POLICY.md \
  .pages-build/privacy-policy.html
```

The generated HTML is committed to the `gh-pages` branch of the project repository. The public
URL is `https://jinge091118-bot.github.io/CleanGuard/privacy-policy.html`.

Note that the application itself has no network permission and therefore cannot open this URL
in-app. This does not affect the Play requirement, which asks for a public URL on the store
listing.

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
