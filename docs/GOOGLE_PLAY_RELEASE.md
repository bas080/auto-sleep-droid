# Research: Releasing Auto Sleep Droid to Google Play via GitHub Workflows

## Overview

This document researches the feasibility and technical implementation requirements for automatically building, signing, and releasing **Auto Sleep Droid** to the Google Play Store using GitHub Workflows (GitHub Actions).

Auto Sleep Droid is currently distributed as signed/unsigned Android Package (APK) binaries via GitHub Releases and as source-built releases via F-Droid. Releasing to the Google Play Store expands accessibility for users who rely on standard Play Store updates.

---

## 1. Feasibility Assessment ("If")

### Is Automated Publishing to Google Play Possible?
**Yes.** Google provides the **Google Play Developer API (v3)**, which enables automated creation of releases, uploading of artifacts, assignment to testing or production tracks, and metadata synchronization. Standard open-source tooling and GitHub Actions integrate directly with this API.

### Key Requirements for Google Play Distribution

1. **Android App Bundle (.aab) Format**:
   - Google Play requires app updates and new submissions to be delivered as an Android App Bundle (`.aab`) rather than a standalone APK (`.apk`).
   - The Android Gradle Plugin natively supports `.aab` generation via `./gradlew bundleRelease`.

2. **Google Play App Signing**:
   - Google Play requires Google Play App Signing.
   - The app developer signs the `.aab` bundle locally using an **upload key**. Upon uploading to Google Play, Google verifies the upload key and re-signs the device-specific APKs delivered to users with the app signing key managed in Google Cloud Key Management Service.

3. **One-Time Account & Console Prerequisites**:
   - **Google Play Developer Account**: A registered Google Play Developer account ($25 one-time registration fee).
   - **Google Cloud Project (GCP)**: A GCP project linked to the Google Play Console with the Google Play Developer API enabled.
   - **Service Account**: A GCP Service Account created with the `Service Account User` role and granted `Admin` or `Release Manager` permissions in Google Play Console (Users & Permissions).
   - **Service Account JSON Private Key**: Exported JSON key file stored securely as a repository secret (`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`).
   - **Initial Manual Submission**: Google Play API strictly enforces that the application package (`com.bas080.autosleepdroid`) and at least one initial draft release must be created **manually** in the Google Play Console web UI before automated API uploads are permitted.

---

## 2. Technical Tooling Options ("How")

Three main options exist for integrating Google Play releases into GitHub Workflows.

### Option A: Fastlane Supply (`fastlane supply`)
Fastlane `supply` is a command-line tool that uses the Google Play Developer API to upload Android app bundles, screenshots, listings, and release notes.

- **Integration**:
  - The repository already contains a `fastlane/metadata/android/` folder structure.
  - A GitHub Actions step executes `fastlane supply` using Ruby or a dedicated Fastlane action.
- **Pros**:
  - Direct compatibility with the repository's existing `fastlane` metadata directory.
  - Robust handling of release notes, store listing text, and localized screenshots.
- **Cons**:
  - Requires setting up Ruby and gem dependencies in the GitHub runner.

---

### Option B: Dedicated GitHub Action (e.g. `r3ndyd/upload-google-play`)
Community GitHub Actions wrap the Google Play Developer API into a declarative step within `.github/workflows/google-play-release.yml`.

- **Integration**:
  - Step configuration references the `.aab` file path, track (`internal`, `alpha`, `beta`, `production`), service account JSON, and rollout percentage.
- **Pros**:
  - Zero Ruby environment overhead and zero Gradle plugin modifications.
  - Fast execution and simple YAML configuration.
- **Cons**:
  - Relies on third-party action maintainers for API v3 updates.

---

### Option C: Gradle Play Publisher (GPP) Plugin (`com.github.triplet.play`)
Gradle Play Publisher is an Android Gradle Plugin (`com.github.triplet.play`) added directly to `app/build.gradle`.

- **Integration**:
  - Configured in Gradle syntax inside `app/build.gradle`.
  - Workflow executes `./gradlew publishReleaseBundle`.
- **Pros**:
  - Integrated directly into the Gradle build lifecycle.
  - Automatically handles fastlane metadata directories.
- **Cons**:
  - Modifies build scripts and plugin dependencies.
  - May require plugin version maintenance alongside Android Gradle Plugin updates.

---

### Tooling Comparison

| Feature / Aspect | Option A: Fastlane Supply | Option B: Dedicated GitHub Action | Option C: Gradle Play Publisher |
|---|---|---|---|
| **Build File Changes** | None | None | Requires adding Gradle plugin to `app/build.gradle` |
| **Ruby Dependency** | Required | Not Required | Not Required |
| **Metadata Support** | Full (Native) | Basic / Release Notes | Full (Native) |
| **CI Execution Speed** | Medium (Ruby setup overhead) | Fast | Fast |
| **Maintenance Burden** | Low | Low | Medium (Plugin updates) |
| **Recommendation** | **Recommended for full metadata sync** | **Recommended for lightweight deployment** | Secondary |

---

## 3. Codebase & Build Configuration Adjustments

To enable automated Google Play releases without breaking existing GitHub Release or F-Droid workflows, the following project configurations must be maintained.

### 1. App Bundle (`.aab`) Generation
The `signingConfigs.release` and `buildTypes.release` blocks in `app/build.gradle` already support environment variables (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

Running `./gradlew bundleRelease` generates the release bundle at:
`app/build/outputs/bundle/release/app-release.aab`

### 2. Versioning Strategy (`versionCode` and `versionName`)
Google Play Console requires a strictly monotonically increasing integer `versionCode` for every uploaded bundle.

- **Current Implementation**: `versionCode` (e.g. `31`) and `versionName` (e.g. `"0.0.18"`) are defined in `app/build.gradle`.
- **Release Workflow**: Maintainers increment `versionCode` in `app/build.gradle` prior to creating release tags (e.g. using `scripts/release.sh`).

### 3. F-Droid Compatibility Safeguards
F-Droid requires reproducible builds and prohibits embedding dependency info metadata.

- Keep `dependenciesInfo` disabled in `app/build.gradle`:
  ```groovy
  dependenciesInfo {
      includeInApk = false
      includeInBundle = false
  }
  ```
- Ensure `./gradlew bundleRelease` builds cleanly offline when no keystore environment variables are present.

### 4. Store Listing Metadata Structure
Maintain store listing resources under `fastlane/metadata/android/<locale>/`:

- `title.txt`: Concise app name (e.g., `Auto Sleep Droid`)
- `short_description.txt`: Short overview (max 80 chars)
- `full_description.txt`: Comprehensive features and usage guide (max 4000 chars)
- `changelogs/<versionCode>.txt`: What's new in this version (max 500 chars)
- `images/phoneScreenshots/`: Phone screenshots (JPEG or 24-bit PNG)

---

## 4. Sample GitHub Actions Workflow Definition

Below is a proposed workflow (`.github/workflows/google-play-release.yml`) for building, signing, and publishing an App Bundle to Google Play Console.

```yaml
name: Google Play Release

on:
  push:
    tags:
      - "v*"
  workflow_dispatch:
    inputs:
      track:
        description: "Google Play release track (internal, alpha, beta, production)"
        required: true
        default: "internal"
        type: choice
        options:
          - internal
          - alpha
          - beta
          - production

permissions:
  contents: read

jobs:
  play-release:
    name: Build & Publish to Google Play
    runs-on: ubuntu-latest

    steps:
      - name: Check out source
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: "gradle"

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Install Android SDK packages
        run: sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

      - name: Run Unit Tests
        run: ./gradlew test --parallel

      - name: Build Signed App Bundle (.aab)
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS || vars.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 --decode > /tmp/release.jks
          KEYSTORE_FILE=/tmp/release.jks ./gradlew bundleRelease

      - name: Upload to Google Play
        uses: r3ndyd/upload-google-play@v2
        with:
          serviceAccountJsonPlainText: ${{ secrets.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: com.bas080.autosleepdroid
          releaseFiles: app/build/outputs/bundle/release/app-release.aab
          track: ${{ github.event.inputs.track || 'internal' }}
          status: 'completed'
```

---

## 5. Google Play Policy & Permission Considerations

When publishing Auto Sleep Droid to Google Play, specific permission declarations and store policies must be addressed during the Google Play Console initial setup:

1. **Exact Alarms (`SCHEDULE_EXACT_ALARM`)**:
   - Google Play restricts `SCHEDULE_EXACT_ALARM` usage.
   - **Declaration Form**: In Google Play Console under App Content -> Special App Access, submit the declaration form specifying that Auto Sleep Droid uses exact alarms for user-configured sleep timers and wake-up goal alarms.
2. **Foreground Services (`FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`)**:
   - Google Play enforces strict policy guidelines for foreground services.
   - Auto Sleep Droid specifies `FOREGROUND_SERVICE_MEDIA_PLAYBACK` to manage active sleep timer countdowns and media playback pausing. Select "Media Playback" in the Play Console Foreground Service Declaration form.
3. **Notification Permission (`POST_NOTIFICATIONS`)**:
   - Target SDK 35 requires requesting runtime notification permissions on Android 13+ (API 33+), which `MainActivity` handles on startup.
4. **Data Safety Declaration**:
   - Auto Sleep Droid does not collect, track, or transmit any personal data or telemetry. Select "No user data collected or shared" in the Play Console Data Safety questionnaire.

---

## 6. Multi-Channel Distribution Strategy

Auto Sleep Droid can seamlessly maintain three parallel release channels:

| Distribution Channel | Artifact Format | Build Trigger | Target Audience |
|---|---|---|---|
| **GitHub Releases** | `.apk` (Signed/Unsigned) | Tag Push (`v*`) / Master / Dispatch | Power users & direct APK installations |
| **F-Droid** | `.apk` (Source-built) | Tag Push (`v*`) / F-Droid Bot Poll | F-Droid / Open-source users |
| **Google Play** | `.aab` (Signed Upload Key) | Tag Push (`v*`) / Dispatch | General Android user base |

---

## 7. Setup Checklist for Maintainers

When ready to publish to Google Play, follow this sequence:

1. **Google Play Console Setup**:
   - Register Developer Account and pay registration fee.
   - Create new app entry for `com.bas080.autosleepdroid`.
   - Complete Store Listing, Privacy Policy, Data Safety, and Content Rating questionnaires.
2. **GCP Service Account Setup**:
   - Create GCP project, enable Google Play Developer API.
   - Create Service Account, generate JSON key.
   - Grant Service Account Admin or Release Manager access in Play Console.
3. **Initial Manual Release**:
   - Build unsigned/signed `.aab` locally via `./gradlew bundleRelease`.
   - Upload `.aab` manually to the Play Console Internal Testing track once.
4. **GitHub Repository Secrets**:
   - Store `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` secret in GitHub Repository Settings.
   - Verify existing `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` secrets.
5. **Workflow Deployment**:
   - Commit `.github/workflows/google-play-release.yml` and test via `workflow_dispatch` to Internal Testing.
