---
title: Refactor MainService to eliminate TooGenericExceptionCaught suppressions
status: open
priority: 4
issue-type: task
created-at: "2026-10-05T17:35:20.830230+00:00"
---

Replace generic Exception catching in MainService.kt method calls with specific domain exception handling to eliminate @Suppress("TooGenericExceptionCaught").
