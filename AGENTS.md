# AGENTS.md

Auto Sleep Droid puts your phone to sleep when you fall asleep, and wakes you up when you're ready. It is a low-friction Android sleep timer and wake-up safeguard for media playback.

- **Task Tracking**: Use `did` (see `did help`). User prompts should result in issues tracked by `did` being created or mutated. Only start implementing `did` issues when the user explicitly requests implementation. Use `did status` / `did show` for context, create task nodes in `.did/implement` or `.did/refine`, and mark tasks complete using `did done` only after implementation, tests pass, and explicit user confirmation (for issues requiring user verification). Issues requiring user input to confirm a feature or fix must remain open until confirmed. Any open work remaining after implementing should be registered using `did`.
- **Commands:** Use `./gradlew` for testing and building (e.g. `./gradlew test`).
- **User Experience:** Provide clear user feedback via toast notifications and immediate reactive UI updates on every state change.
- **User Manual & Permissions:** Keep the bundled manual (`app/src/main/assets/manual.html`) synchronized whenever user-visible features change. Document any newly required permissions in `README.md`.
- **Commit Messages:** Use concise, imperative titles without prefixes (e.g., `Add feature` instead of `Added feature` or `feat: add feature`).
