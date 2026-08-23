# AGENTS.md

Instructions and guidelines for AI coding agents and human developers working in this repository.

## Project Overview

Auto Sleep Droid is an Android sleep timer app controlled entirely from the notification shade with a live event log UI in `MainActivity`.

## Project Documentation

- [SPEC.md](SPEC.md): product requirements and acceptance criteria.
- [IMPLEMENTATION.md](IMPLEMENTATION.md): architecture, runtime flows, persistence, permissions, build/release details, and guidance for future developers and AI agents.

## Build & Test Instructions

### Common Commands

- Run unit tests: `./gradlew test`
- Build debug APK: `./gradlew assembleDebug`
- Clean build outputs: `./gradlew clean`

## Key Codebase Conventions

- **UI & Notification Strings:** Do not include trailing punctuation, colons, or ellipses in UI and notification string resource values (`strings.xml`).
- **Localization:** Maintain default English resources in `app/src/main/res/values/strings.xml` and Spanish translations in `app/src/main/res/values-es/strings.xml`.
- **Unit Tests:** Do not add unit tests or test dependencies unless explicitly instructed by the user.
- **Reproducible Builds:** Keep `dependenciesInfo` (`includeInApk = false`, `includeInBundle = false`) disabled in `app/build.gradle` for F-Droid compliance.
- **Releases:** Follow `scripts/release.sh <version>` for bumping versions and tagging. GitHub Actions (`.github/workflows/android-release.yml`) builds and publishes releases automatically. Point to GitHub Releases in Fastlane description metadata rather than per-version changelogs.
- **Commit Messages:** Do not use prefixes such as `ci:`, `feat:`, `fix:`, or `chore:`. Write plain, clear titles written for normal human readers (e.g. `Add dark mode support` instead of `feat: add dark mode support`).
