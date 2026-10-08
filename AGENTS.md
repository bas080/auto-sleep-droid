# AGENTS.md

Auto Sleep Droid puts your phone to sleep when you fall asleep, and wakes you up when you're ready. It is a low-friction Android sleep timer and wake-up safeguard for media playback.

- **Task Tracking & Way of Working**:
  - Use `did` for issue and task tracking (see `did help`). The legacy `.dots/` directory is removed.
  - User prompts should result in task nodes being created or updated in `.did/`.
  - Directory structure:
    - `.did/refine/`: Issues requiring scoping, clarification, or design decisions. Any issue requiring refinement must contain questions aimed at `@bas080`.
    - `.did/implement/`: Actionable implementation issues ready for development. Issues that do not require further refinement are moved here.
  - Development lifecycle: Only start implementing `did` issues when the user explicitly requests implementation. Use `did status` / `did show` for context.
  - Completion: Mark tasks complete using `did done <path>` only after code implementation, unit test suite pass, and explicit user confirmation (for issues requiring physical device/user verification).
- **Commands:** Use `./gradlew` for testing and building (e.g. `./gradlew test`).
- **User Experience:** Provide clear user feedback via toast notifications and immediate reactive UI updates on every state change.
- **User Manual & Permissions:** Keep the bundled manual (`app/src/main/assets/manual.html`) synchronized whenever user-visible features change. Document any newly required permissions in `README.md`.
- **Commit Messages:** Use concise, imperative titles without prefixes (e.g., `Add feature` instead of `Added feature` or `feat: add feature`).
