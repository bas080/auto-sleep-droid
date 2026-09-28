# AGENTS.md

Instructions and guidelines for AI coding agents and human developers working in this repository.

## Project Overview

Auto Sleep Droid is an Android sleep timer app controlled entirely from the notification shade with a live event log UI in `MainActivity`.

## Build & Test Instructions

### Common Commands

- Run unit tests and code complexity checks: `./gradlew test`
- Run code complexity static analysis: `./gradlew detekt`
- Build debug APK: `./gradlew assembleDebug`
- Build release APK (unsigned): `./gradlew assembleRelease`
- Lint F-Droid metadata: `fdroid lint com.bas080.autosleepdroid`
- Test F-Droid build: `fdroid build --stop --test com.bas080.autosleepdroid`
- Clean build outputs: `./gradlew clean`

## Key Codebase Conventions

- **User Experience & Feedback:** Every user-initiated state change (toggling timer, adjusting duration, snoozing/dismissing alarms, marking awake) must provide clear feedback via toast notifications and immediate reactive UI updates.
- **UI & Notification Formatting:** Keep UI and notification text clean and consistent. Avoid trailing colons, punctuation, or ellipses in `strings.xml`. Maintain English (`values/strings.xml`) and Spanish (`values-es/strings.xml`) translations.
- **User Manual & Permissions:** Keep the bundled user manual (`app/src/main/assets/manual.html`) synchronized whenever user-visible features change. Any newly required system permission must be declared in `AndroidManifest.xml` and explained in `README.md`.
- **System & Task Navigation:** Launch external system settings screens (Do Not Disturb, Alarms, Health Connect) as separate tasks so users can seamlessly switch back to the app.
- **Reliability & Error Handling:** Ensure background services and alarms execute reliably without crashing. Let unexpected failures surface to the global error handler for diagnostic reporting rather than swallowing exceptions.
- **Code Quality & Static Analysis:** Maintain low code complexity and high maintainability. Detekt (`io.gitlab.arturbosch.detekt`) enforces complexity best practices via `config/detekt/detekt.yml` during tests and CI.
- **F-Droid & Build Compatibility:** Ensure builds remain reproducible and F-Droid compliant (`fdroid lint`). Unsigned release builds (`./gradlew assembleRelease`) must assemble cleanly without requiring local keystore configuration.
- **Clear Commit Messages:** Write concise, plain commit titles for human readers without conventional commit prefixes (e.g., `Add dark mode support` instead of `feat: add dark mode support`).
