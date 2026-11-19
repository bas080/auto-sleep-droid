---
title: Cap stored event logs to a practical amount
status: open
priority: 3
issue-type: task
created-at: "2026-11-19T22:06:13.009453+00:00"
---

Keep only a practical amount of log events around instead of letting the log file grow to 1 MB before slashing it to 50 rows.

## Decisions

- Retention is capped at the 200 most recent events, always. 200 rows is a readable scrollback for the Logs screen and keeps the file around 40 KB, which is also a practical size for the attach-event-logs feedback email.
- The byte-based threshold (`MAX_LOG_FILE_BYTES = 1_000_000`, prune to `TRIM_TO_LOGS = 50`) is removed; trimming now happens in one place after each append.

## Changes

- `EventLogStorage` now keeps at most 200 lines: `writeLogToFile` appends and calls a single `trim` helper, and the byte-based `pruneFile` is gone.
- Legacy oversized log files (pre-cap) are trimmed to the newest 200 lines on the next write.
- Tests: count-cap test replaces the byte-threshold test; an additional test covers trimming a legacy >1 MB file on next write.

## Verification

- `./gradlew test` and `./gradlew detekt` pass.
- Retention amount is visible behavior on device; pending user verification.
