---
title: Remind user of bedtime wake goal to encourage timely sleep
status: resolved
priority: 3
issue-type: task
created-at: "2026-10-17T10:45:00.000000+00:00"
---

Notify or remind the user when their target bedtime is approaching based on their configured wake-up goal and minimum sleep duration, making them aware of their goal and encouraging them to sleep.

### Fully Scoped Specifications (Refined with @bas080)

1. **Setting Control & Permission Request:**
   - Dedicated toggle switch in settings (`row_bedtime_reminder`) inside the Smart Wake-Up Goal section (`header_alarm`).
   - When toggled ON, checks and prompts the user to grant any necessary permissions required for exact background scheduling and display activation (e.g. exact alarms / display permissions if needed).

2. **Trigger Mechanism & Screen Activation:**
   - Triggers when bedtime arrives (`targetWake - minSleep`) while the sleep goal is active.
   - Uses a screen `WakeLock` (`PowerManager.FULL_WAKE_LOCK` with `ACQUIRE_CAUSES_WAKEUP` and `ON_AFTER_RELEASE` flags) to turn on the display screen directly.
   - Also displays if the user unlocks/opens their screen while inside the sleep window (`targetWake - minSleep` to `targetWake`).

3. **User Note Box Behavior & Placeholder:**
   - No pre-filled note text.
   - Input field in settings features a prompt placeholder asking: *"Why is this sleep goal important to you? Write a note to your future self..."*
   - On the bedtime screen overlay, the note card expands dynamically up to 50% screen height. For longer text exceeding 50% screen height, the card becomes internally scrollable.

4. **Daily Plain-Language Sleep Science Factoid & Tip Generator:**
   - Rotates daily to show a new plain-spoken, easy-to-understand sleep science tip or factoid explaining why sleep matters and how to reach sleep goals.
   - Powered by a dataset of 100 plain-language sleep tips across 5 core health categories.
   - **Like / Dislike Controls (`[ 👍 ] [ 👎 ]`):** Simple thumbs icons without extra text. Disliking a factoid flags it in `SharedPreferences` so it is never shown again in future daily rotations.

5. **Close Action:**
   - Features a clean "Close" button to dismiss the reminder overlay view. (The wake-up goal itself remains active and cannot be turned off from here).

### Final UI Wireframe

#### Bedtime Preparation Screen (Overlay View)
```text
+-------------------------------------------------------------+
|                                                             |
|                       🌙 Time for Bed                       |
|          "Rest is not a luxury; it is a necessity."         |
|                                                             |
|  +-------------------------------------------------------+  |
|  | 💌 Note to Future Self                                |  |
|  |                                                       |  |
|  | "Why is this sleep goal important to you?"            |  |
|  | (User custom text displayed here...)                  |  |
|  |                                                       |  |
|  | [ Scrollable inside card if text exceeds 50% screen ] |  |
|  +-------------------------------------------------------+  |
|                                                             |
|  +-------------------------------------------------------+  |
|  | 💡 Today's Sleep Tip                                  |  |
|  |                                                       |  |
|  | "Deep sleep acts like a dishwasher for your brain,    |  |
|  |  washing away toxins and clutter built up during     |  |
|  |  the day so you wake up refreshed and clear-headed."  |  |
|  |                                                       |  |
|  |                                      [ 👍 ] [ 👎 ]   |  |
|  +-------------------------------------------------------+  |
|                                                             |
|                          [ Close ]                          |
|                                                             |
+-------------------------------------------------------------+
```
