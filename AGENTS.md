# AGENTS.md

Auto Sleep Droid puts your phone to sleep when you fall asleep, and wakes you up when you're ready. It is a low-friction Android sleep timer and wake-up safeguard for media playback.

- **Task Tracking:** Use `dots` (see `dot --help`). Run `dot ready` before starting work, use `dot show` / `dot tree` for context, create tasks in `dots` with dependencies, and mark tasks complete only after implementation and tests pass.
- **Commands:** Use `./gradlew` for testing and building (e.g. `./gradlew test`).
- **User Experience:** Provide clear user feedback via toast notifications and immediate reactive UI updates on every state change.
- **User Manual & Permissions:** Keep the bundled manual (`app/src/main/assets/manual.html`) synchronized whenever user-visible features change. Document any newly required permissions in `README.md`.
- **Commit Messages:** Use concise, imperative titles without prefixes (e.g., `Add feature` instead of `Added feature` or `feat: add feature`).
