# Update Notification Feature: GitHub Releases Integration

## Overview

This document describes the technical architecture, user experience (UX) design, and implementation specifications for notifying users when a new version of Auto Sleep Droid is available on GitHub Releases.

Because Auto Sleep Droid can be installed directly via GitHub Releases or side-loaded outside of traditional app stores (such as Google Play or F-Droid), the application cannot rely on centralized app store background updates. This feature provides a privacy-preserving, non-intrusive update checker that alerts users when an update is published.

---

## 1. Background & Motivation

### The Update Problem for Non-Store Installs
When users install Auto Sleep Droid from GitHub Releases or directly download the APK:
- **No Background App Store Auto-Updates**: Unlike Google Play or F-Droid, direct APK installations do not receive automatic background updates.
- **Outdated Bug Fixes & Features**: Users may remain on older versions missing critical bug fixes, performance improvements, or new features.
- **Manual Checking Burden**: Expecting users to regularly visit the GitHub Releases page to manually compare version numbers is high-friction and inefficient.

### Product Goals
1. **Low Friction**: Automatically detect new versions without requiring manual checks.
2. **Bedtime Non-Intrusiveness**: Never disrupt the user with pop-ups or alerts while the sleep timer is active or during bedtime hours.
3. **Privacy-Preserving**: Query GitHub Releases using standard, unauthenticated REST API calls without sending any telemetry or user identification.
4. **Bandwidth Efficient**: Use conditional HTTP requests (`ETag` / `If-None-Match`) to minimize network bandwidth and respect GitHub API rate limits.

---

## 2. User Experience (UX) Specification

### Core UX Principles

1. **Zero Bedtime Light / Zero Interruption**:
   - Update checks and notifications must **never** fire while the sleep timer is in `ACTIVE` or `FADING` states.
   - Update alerts must never display intrusive modal dialogs or heads-up banner popups that interrupt media playback or disturb a darkened room.
2. **Notification Shade Integration**:
   - Update notifications appear silently in the notification shade using low priority (`IMPORTANCE_LOW` / `PRIORITY_LOW`).
   - Notification text is clear and concise:
     - **Collapsed**: `"New update available: v1.2.0"`
     - **Expanded**: `"Auto Sleep Droid v1.2.0 is available. Tap to view release notes and download."`
   - **Action Buttons**:
     - **"View Release" / "Update"**: Opens the release page or begins the download.
     - **"Ignore Version"**: Suppresses notifications for this specific version tag.
3. **`MainActivity` Integration**:
   - When `MainActivity` is opened, a compact update banner appears at the top of the event log list if a new release is available.
   - The event log displays a low-importance entry: `New version available: v1.2.0 (current: v1.1.0)`.
4. **Version Dismissal & Preference**:
   - If the user taps "Ignore Version", the app records the ignored version tag in `SharedPreferences` and hides all notifications for that specific release until a newer version is published.

### User Workflows

```text
+-------------------------------------------------------------------------------+
| 1. Periodic Background Check (WorkManager)                                     |
| Executes every 24-72 hours when connected to Wi-Fi / unmetered network.        |
| Skips check if sleep timer is ACTIVE or FADING.                               |
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
| 2. Version Comparison                                                         |
| Compares remote tag_name (e.g., "v1.2.0") against BuildConfig.VERSION_NAME.  |
| Checks if release tag matches ignored_version_tag.                            |
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
| 3. Presentation (If New Unignored Version Found)                               |
| - Notification Shade: Posts silent low-priority update notification.          |
| - MainActivity: Displays update banner at top of UI & logs to EventLogger.    |
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
| 4. User Action                                                                |
| - Tap "View Release" -> Opens GitHub Release URL in default browser.           |
| - Tap "Ignore Version" -> Saves version to ignored_version_tag preference.    |
+-------------------------------------------------------------------------------+
```

---

## 3. Technical Implementation Details

### 1. GitHub Releases API Query

Auto Sleep Droid queries the GitHub REST API for the latest published release:

- **Endpoint**: `GET https://api.github.com/repos/bas080/auto-sleep-droid/releases/latest`
- **Method**: `GET`
- **Headers**:
  - `User-Agent`: `AutoSleepDroid/<VERSION_NAME>` (e.g., `AutoSleepDroid/1.1.0`)
  - `Accept`: `application/vnd.github+json`
  - `If-None-Match`: `<stored_etag>` (Conditional GET for bandwidth optimization)

#### Handling GitHub API Responses
- **HTTP 200 OK**: Parse JSON response body. Update stored `ETag` header value.
- **HTTP 304 Not Modified**: No new releases since last check. Reuse stored release data without consuming API quota.
- **HTTP 403 / 429 (Rate Limit Exceeded)**: Unauthenticated API limit is 60 requests/hour per IP. Catch exception gracefully, log event, and retry after 24 hours.

#### Expected JSON Response Fields
```json
{
  "tag_name": "v1.2.0",
  "html_url": "https://github.com/bas080/auto-sleep-droid/releases/tag/v1.2.0",
  "name": "Auto Sleep Droid v1.2.0",
  "body": "Release notes summary...",
  "prerelease": false,
  "draft": false,
  "assets": [
    {
      "name": "app-release.apk",
      "browser_download_url": "https://github.com/bas080/auto-sleep-droid/releases/download/v1.2.0/app-release.apk"
    }
  ]
}
```

---

### 2. Version Parsing & Semantic Comparison Logic

To accurately determine whether a remote release is newer than the currently installed application version:

1. **Tag Normalization**: Strip leading non-numeric characters (e.g., `v1.2.0` -> `1.2.0`).
2. **Semantic Versioning Parsing (`major.minor.patch`)**:
   - Split string by `.` into integer array (e.g., `[1, 2, 0]`).
   - Compare integers sequentially from left to right against `BuildConfig.VERSION_NAME` components.
   - Example: `1.2.0` > `1.1.0` -> New version available.
3. **Filter Drafts & Pre-releases**:
   - The `/releases/latest` API automatically returns the latest stable release (excluding drafts and pre-releases).

---

### 3. Check Scheduling & Background Execution (`WorkManager`)

Update checks should run periodically in the background using Android's `WorkManager` API.

- **Worker Component**: `UpdateCheckWorker extends Worker`
- **Execution Interval**: `PeriodicWorkRequest` configured for 24 to 72 hours (e.g., 48 hours).
- **Constraints**:
  - `NetworkType`: `NetworkType.CONNECTED` (or `UNMETERED` to save cellular data).
  - `BatteryNotLow`: `true`.
- **Timer State Guard**:
  - Before executing the HTTP request, `UpdateCheckWorker` reads `SharedPreferences`.
  - If the sleep timer service state is currently `ACTIVE` or `FADING`, skip the check and reschedule.

---

### 4. Direct Browser Link vs In-App Package Installer Comparison

Two technical approaches exist for delivering the update to the user:

| Feature / Aspect | Option A: External Browser Link (Recommended) | Option B: In-App Download & Package Installer |
|---|---|---|
| **Mechanism** | Fires `Intent.ACTION_VIEW` targeting GitHub release URL. | Downloads APK file via `DownloadManager` and launches `FileProvider` package installer intent. |
| **Permissions Required** | None beyond standard `INTERNET` permission. | Requires `REQUEST_INSTALL_PACKAGES` permission on Android 8.0+ (API 26+) and `FileProvider` manifest setup. |
| **F-Droid / Store Compliance** | 100% compliant with F-Droid and Play Store policies. | May trigger security warnings or violate store self-update policies if published on stores. |
| **User Experience** | Clean, safe redirect to browser where user taps download. | 1-tap seamless installation prompt inside app. |
| **Maintenance Complexity** | Very Low (20 lines of code). | High (requires file storage management, provider XML, and permission handling). |
| **Recommendation** | **PRIMARY / PREFERRED** | Secondary / Optional extension |

**Selected Approach**: **Option A (External Browser Link)** is recommended as the primary implementation due to its zero-permission requirements, minimal maintenance overhead, and 100% policy compliance across all distribution channels.

---

### 5. Persistence & State Management

Update preferences and state are stored in `SharedPreferences` (`update_checker` file):

| Key | Type | Description |
|---|---|---|
| `auto_update_check_enabled` | boolean | Toggle for enabling/disabling periodic update checks (default `true`). |
| `last_check_timestamp` | long | Wall-clock timestamp (millis) of the last update check. |
| `latest_available_version` | string | Remote version tag string (e.g. `"v1.2.0"`). |
| `latest_release_url` | string | Browser URL to the GitHub release page. |
| `ignored_version_tag` | string | Version tag string explicitly dismissed/ignored by the user. |
| `cached_etag` | string | HTTP `ETag` header value for conditional HTTP requests. |

---

## 4. Summary & Recommendation

The proposed **GitHub Releases Update Checker** provides an ideal solution for non-store installations of Auto Sleep Droid:
- **Low Friction & Automatic**: Periodic background checks using `WorkManager` keep users aware of new releases.
- **Zero Bedtime Disturbance**: Strictly avoids notifications during active sleep timer countdowns or bedtime hours, using silent notification shade updates.
- **Privacy & Resource Friendly**: Uses standard GitHub REST APIs with `ETag` conditional GET requests and zero user telemetry.
- **Clean Architecture**: Option A (browser redirect) keeps permissions minimal and ensures complete compliance across all distribution channels.
