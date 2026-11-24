---
title: Remove unused legacy switch fields from MainActivity
status: open
priority: 3
issue-type: task
created-at: "2026-11-24T13:06:15Z"
---

MainActivity binds four Switch? fields (switchEnableTimer, switchAutoTimer, switchEnableGoal, switchHealthConnect) via app:switchId. Since the SettingRowView refactor these fields are declared and assigned but never read anywhere in app or tests; the rows are driven through SettingRowView.

Removing the fields (and their findViewById calls) shrinks MainActivity and removes the temptation to write the inner switches directly, bypassing the isProgrammaticChange guard.

Verification: ./gradlew test lintDebug on CI after removal.
