---
title: Fix spurious toggle logs when opening the main UI
status: open
priority: 3
issue-type: task
---

Report: opening the main UI can log "Auto sleep timer (DND) enabled" and "Health Connect sync enabled" at LEVEL_HIGH with no user interaction.

## Root cause

- SettingRowView already suppresses programmatic isChecked sets (isProgrammaticChange guard since ceb99e4).
- The remaining bypass is view-state restoration: on activity recreation, CompoundButton.onRestoreInstanceState sets the inner switch directly, which is observably identical to a user tap. The restore pass runs between onStart and onResume, while the toggle listeners are attached from onCreate.

## Fix

- Move the four toggle listeners (rowEnableTimer, rowAutoTimer, rowEnableGoal, rowHealthConnect) out of setupConfigControls into MainActivityControlsHandler.bindToggleListeners(activity), called from onResume (after the restore pass), and unbindToggleListeners(activity) called from onPause, mirroring registerPreferenceListeners/unregisterPreferenceListeners.

## Verification

- Robolectric: recreate the activity with saved view state and assert no new LEVEL_HIGH toggle logs and no Health Connect permission request.
- Full CI: ./gradlew test lintDebug via GitHub Actions.
