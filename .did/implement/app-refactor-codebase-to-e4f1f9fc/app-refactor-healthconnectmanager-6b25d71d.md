---
title: Refactor HealthConnectManager to eliminate TooGenericExceptionCaught suppressions
status: open
priority: 3
issue-type: task
created-at: "2026-10-05T17:35:05.272678+00:00"
---

Replace generic Exception catching in HealthConnectManager.kt with specific HealthConnect exception handling to remove @Suppress("TooGenericExceptionCaught").
