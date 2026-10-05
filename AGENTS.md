# AGENTS.md

Instructions and guidelines for AI coding agents and human developers working in this repository.

- **Task Tracking:** Use `dots`. Run `dot ready` before starting work, use `dot show` / `dot tree` for context, create tasks in `dots` with dependencies, and mark tasks complete only after implementation and tests pass.
- **Common Commands:**
  - `./gradlew test` — Run unit tests, Detekt static analysis, Android Lint, and JaCoCo coverage verification.
  - `./gradlew assembleRelease` — Build unsigned release APK.
  - `fdroid lint com.bas080.autosleepdroid` — Lint F-Droid metadata.
- **User Experience:** Provide clear user feedback via toast notifications and immediate reactive UI updates on every state change.
- **Formatting & Locales:** Avoid trailing punctuation or colons in `strings.xml`. Keep English (`values/strings.xml`) and Spanish (`values-es/strings.xml`) translations synchronized.
- **User Manual & Permissions:** Keep the bundled manual (`app/src/main/assets/manual.html`) synchronized whenever user-visible features change. Document any newly required permissions in `README.md`.
- **System Settings Navigation:** Launch external settings screens (Do Not Disturb, Alarms, Health Connect) as separate tasks (`FLAG_ACTIVITY_NEW_TASK`) so users can seamlessly switch back to the app.
- **Error Handling:** Fail fast and let unexpected background failures surface to `AutoSleepApplication` for crash reporting rather than swallowing exceptions.
- **Commit Messages:** Write concise, plain commit titles without conventional commit prefixes (e.g., `Add dark mode support` instead of `feat: add dark mode support`).
