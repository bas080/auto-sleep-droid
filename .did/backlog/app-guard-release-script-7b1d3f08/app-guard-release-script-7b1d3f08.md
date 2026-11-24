---
title: Add safety guards to scripts/release.sh
status: open
priority: 2
issue-type: task
created-at: "2026-11-24T13:06:15Z"
---

Cosmetic/operational guard discussed while preparing the v0.0.41 release.

scripts/release.sh accepts a version name and commits+tags immediately. Guards it could include:
- refuse when git tree is dirty,
- refuse when not on master or master is not up to date with origin,
- refuse when the vX.Y.Z tag already exists (currently the perl edit runs first and can half-apply before the tag fails).

Upstream releases are done straight from master, so a stale or dirty checkout currently produces a bump commit that does not match what is actually released.
