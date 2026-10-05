---
title: Show toggle source in sleep timer description (DND vs user)
status: open
parent: app-next-release-1855c1b5
priority: 3
issue-type: task
created-at: "2026-10-05T15:15:19.456508+00:00"
---

Update sleep timer toggle description to indicate when it was turned on or off by Do Not Disturb vs manually by the user.

### Context & Implementation Details
- Auto Sleep Timer DND feature toggles sleep timer on/off upon system DND state broadcast (`ACTION_INTERRUPTION_FILTER_CHANGED`).
- Track toggle source in SharedPreferences (`timer_toggle_source`: `USER` vs `DND`).
- Update `switch_enable_timer` description label in `MainActivityUiStateUpdater` to append short indicator (e.g., " (via DND)" or " (manual)").
- Keep description text concise and localized.

### Acceptance Criteria
- Toggling timer manually sets source to `USER`.
- Toggling timer automatically via DND broadcast sets source to `DND`.
- Description label dynamically updates state reflecting the toggle origin.
- Tests verify preference state and UI label formatting.
