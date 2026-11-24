---
title: Document flip gesture and log retention in manual.html
status: open
priority: 3
issue-type: task
created-at: "2026-11-24T13:06:15Z"
---

manual.html is kept in sync with user-visible changes. Two recent features are not documented there:
- The phone flip gesture: a flip is the device transitioning between resting positions (any direction, screen up or down). During alarm ringing it snoozes; during volume fade it resets/extends the timer. Requires a 400 ms hold and a stable sensor reading.
- Event log retention: logs are capped at the last 200 entries.

Consider adding a short section describing the flip gesture next to the alarm and fade explanations, and one line about the log cap in the logs overlay description.
