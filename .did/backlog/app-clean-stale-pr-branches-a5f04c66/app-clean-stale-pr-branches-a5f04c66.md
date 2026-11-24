---
title: Clean up merged PR branches on origin
status: open
priority: 3
issue-type: task
created-at: "2026-11-24T13:06:15Z"
---

Two task branches remain on origin after their PRs were merged:
- vibe/improve-flip-detection-f96666 (PR #240, merged via rebase to master as 098595b/22c3a28/af2ea29)
- vibe/fix-logs-and-media-focus-f96666 (PR #241, merged via fast-forward 35d8f04..988e11f)

They can be deleted with: git push origin --delete <branch> (or via the GitHub UI) once nothing references them. Also a pr-242 pre-release tag exists from the branch build of PR #242 and can be removed if it clutters the releases page.

While listing releases, an old release named "master" (tag "latest", dated 2026-08-23, before v0.0.4 series) was observed. Worth removing or renaming so the versioned releases read cleanly.
