---
title: Submit Auto Sleep Droid to IzzyOnDroid and F-Droid
status: open
priority: 2
issue-type: task
created-at: "2026-10-05T14:59:23.320484+00:00"
---

Submit application package and fastlane metadata to IzzyOnDroid / F-Droid repository listings.

### Context & Implementation Details
- Fastlane metadata and localized store descriptions (`fastlane/metadata/android/<locale>/`) are prepared (`en-US` and `es-ES`).
- Robolectric fastlane screenshot generator (`ScreenshotGeneratorTest.kt`) produces 10 standard screenshots under `fastlane/metadata/android/en-US/images/`.
- Ensure release APK compilation passes `./gradlew assembleRelease` with ProGuard rules (`app/proguard-rules.pro`).
- Submit merge request / issue to IzzyOnDroid and F-Droid official package index repositories.

### Acceptance Criteria
- Release build `./gradlew assembleRelease` succeeds without ProGuard / shrinker errors.
- Fastlane metadata and screenshots conform to F-Droid / IzzyOnDroid submission guidelines.
- Repository submission tracking links documented.
