---
title: Disable view-state save on SettingRowView switches
status: open
priority: 2
issue-type: task
created-at: "2026-11-24T13:06:15Z"
---

Belt-and-braces follow-up to the spurious-toggle fix (v0.0.42, PR #242).

SettingRowView.isChecked has a programmatic-change guard, and toggle listeners now bind in onResume (after the view-state restore pass). One residual write path remains: CompoundButton.onRestoreInstanceState sets the inner Switch directly, bypassing SettingRowView.isChecked. Today that cannot fire any listener (they are still unbound during restore), but if toggle listeners ever move back to onCreate, the old spurious-toggle bug returns silently.

Fix: one line in SettingRowView.initView(): switchView.isSaveEnabled = false. The prefs stay the single source of truth; rows are re-synced by update*Ui on every resume.

Verification: re-run MainActivityTest.testRecreationDoesNotLogSpuriousToggleEvents with listeners temporarily bound in onCreate to prove restore no longer writes the switch.
