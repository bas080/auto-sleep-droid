---
title: Modularize MainService to remove file-level LargeClass and TooManyFunctions suppressions
status: open
priority: 5
issue-type: task
created-at: "2026-10-05T17:35:34.172660+00:00"
---

Extract helper objects/controllers from MainService.kt to reduce class size and method count below Detekt static analysis limits.
