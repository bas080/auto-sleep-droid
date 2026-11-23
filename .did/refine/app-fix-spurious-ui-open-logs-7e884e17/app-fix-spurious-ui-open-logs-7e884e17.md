---
title: Spurious "sync enabled" / "DND enabled" logs when opening the main UI
status: open
priority: 3
issue-type: task
created-at: "2026-11-23T14:26:17.778439Z"
---

Report: opening the main UI logs "Health Connect sync enabled" and "Auto sleep timer (DND) enabled" at LEVEL_HIGH with no user interaction.

## Root cause

- onCreate attaches LOGGING changed-listeners to rowAutoTimer / rowHealthConnect (MainActivityControlsHandler).
- onResume reruns MainActivityUiStateUpdater.updateTimerUi/updateHealthConnectUi, which programmatically set row.isChecked from prefs. Freshly inflated rows start unchecked, so any enabled pref produces a real check-change event; the listeners treat it as a user toggle.
- Beyond misleading LEVEL_HIGH logs, rowHealthConnect routes to handleHealthConnectToggle(isUserInitiated = true) -> enableHealthConnect -> requestHealthConnectPermission: opening the UI can kick off a Health Connect permission request orRp even mark isRequestingHealthConnectPermission and reopen permission settings on next resume.
- The same rows are also explicitly set in checkHealthConnectOnResume permission flows.

## Candidate fix (for @bas080 to approve)

Add an "isUpdatingRowState" guard flag in MainActivity set around programmatic isChecked assignments (UiStateUpdater + permission handlers); listeners skip toggle logic + logging for non-user-driven changes (or pass isUserInitiated=false instead). Keep real user toggles logged at LEVEL_HIGH.
