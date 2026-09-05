# AGENTS.md

Instructions and guidelines for AI coding agents and human developers working in this repository.

## Project Overview

Auto Sleep Droid is an Android sleep timer app controlled entirely from the notification shade with a live event log UI in `MainActivity`.

## Project Documentation

- [SPEC.md](docs/SPEC.md): product requirements and acceptance criteria.
- [IMPLEMENTATION.md](docs/IMPLEMENTATION.md): architecture, runtime flows, persistence, permissions, build/release details, and guidance for future developers and AI agents.
- [USER_PERSONA_AND_NEEDS.md](docs/USER_PERSONA_AND_NEEDS.md): user personas, target audience needs, mental models, reasoning, and product workflows. Consult this document whenever UX design decisions have to be made.
- [EVENTS_AND_STATES.md](docs/EVENTS_AND_STATES.md): comprehensive reference of system states, input/sensor events, state transitions, transition matrix, state diagram, and event log formats.
- [EVENT_STATE_REDRAW_ARCHITECTURE.md](docs/EVENT_STATE_REDRAW_ARCHITECTURE.md): design specification for the unidirectional Event-State-Redraw execution loop, pure models, listener synchronization, and migration roadmap.
- [PERFORMANCE.md](docs/PERFORMANCE.md): performance analysis, optimizations implemented, and recommendations for future increases.
- [NOTIFICATION_INPUT_OPTIONS.md](docs/NOTIFICATION_INPUT_OPTIONS.md): analysis of notification duration input options, framework constraints, and string parsing.
- [NOTIFICATION_GOAL_INPUT_OPTIONS.md](docs/NOTIFICATION_GOAL_INPUT_OPTIONS.md): options, constraints, parsing specifications, and architectural design for setting target wake-up goal alarms from notifications.
- [UPDATE_NOTIFICATIONS.md](docs/UPDATE_NOTIFICATIONS.md): technical architecture, UX design, GitHub REST API integration, WorkManager check scheduling, and version comparison for non-store update notifications.
- [IMPORT_EXPORT.md](docs/IMPORT_EXPORT.md): specification, data schema format (JSON Schema v1), UI layout placement, clipboard/dialog workflows, and architecture for the Import/Export feature.

## Build & Test Instructions

### Common Commands

- Run unit tests: `./gradlew test`
- Build debug APK: `./gradlew assembleDebug`
- Build release APK (unsigned): `./gradlew assembleRelease`
- Lint F-Droid metadata: `fdroid lint com.bas080.autosleepdroid`
- Test F-Droid build: `fdroid build --stop --test com.bas080.autosleepdroid`
- Clean build outputs: `./gradlew clean`

## Key Codebase Conventions

- **UX Design Decisions:** Consult `docs/USER_PERSONA_AND_NEEDS.md` whenever making UX design decisions to ensure alignment with target user personas, mental models, zero-gaze nighttime interaction principles, and user needs.
- **Documentation Boundaries:** `docs/SPEC.md` is central to designing the app and any changes to the spec or product behavior require updating `docs/SPEC.md`. Always update `docs/SPEC.md` whenever user requirements, specifications, or product behaviors are described or changed. `docs/SPEC.md` must focus purely on product requirements, acceptance criteria, and user-visible behavior without technical implementation details (such as Android API names, classes, or code constructs). Technical implementation details and things implicit in the code should be documented in `docs/IMPLEMENTATION.md` so future agents can clearly understand how the code works; favor writing in `docs/IMPLEMENTATION.md` over writing code docs or inline comments.
- **No Text Codeblock Diagrams:** Do not render ASCII or text-art codeblock diagrams in documentation files. Text diagrams are not computer parseable and are less desired.
- **UI & Notification Strings:** Do not include trailing punctuation, colons, or ellipses in UI and notification string resource values (`strings.xml`).
- **Localization:** Maintain default English resources in `app/src/main/res/values/strings.xml` and Spanish translations in `app/src/main/res/values-es/strings.xml`.
- **Test-Driven Development (TDD):** When attempting a fix, follow a TDD approach where possible: write a test that fails first, and then implement the fix to make that test pass.
- **Code Testability over Reflection:** Prefer refactoring production code for testability (e.g. extracting testable logic into utility classes or methods, or increasing visibility) over using reflection in unit tests. Reflection should only be used when refactoring does not solve the problem.
- **Unit Tests:** Do not add unit tests or test dependencies unless explicitly instructed by the user.
- **Reproducible & F-Droid Builds:** Keep `dependenciesInfo` (`includeInApk = false`, `includeInBundle = false`) disabled in `app/build.gradle` for F-Droid compliance. Whenever making changes affecting build configurations, Gradle plugins, or metadata, verify that unsigned release builds (`./gradlew assembleRelease` or `fdroid build --stop --test com.bas080.autosleepdroid`) assemble cleanly without keystore environment variables and run `fdroid lint com.bas080.autosleepdroid` to ensure F-Droid build compatibility.
- **Releases:** Follow `scripts/release.sh <version>` for bumping versions and tagging. GitHub Actions (`.github/workflows/android-release.yml`) builds and publishes releases automatically. Point to GitHub Releases in Fastlane description metadata rather than per-version changelogs.
- **Commit Messages:** Do not use prefixes such as `ci:`, `feat:`, `fix:`, or `chore:`. Write plain, clear titles written for normal human readers (e.g. `Add dark mode support` instead of `feat: add dark mode support`). Always check `git diff` before writing human readable, spec-focused commit messages and submission titles/descriptions to ensure accuracy.
- **User Manual Asset:** The user manual is bundled in `app/src/main/assets/manual.html` and must be kept in sync whenever changes affecting user-visible behavior or features occur or whenever `docs/SPEC.md` is updated.
