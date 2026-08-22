#!/usr/bin/env bash
set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <version-name> [changelog-message]"
  echo "Example: $0 0.2"
  exit 1
fi

VERSION_NAME="$1"
DEFAULT_CHANGELOG="See release notes on GitHub: https://github.com/bas080/auto-sleep-droid/releases/tag/v${VERSION_NAME}"
CHANGELOG_MSG="${2:-$DEFAULT_CHANGELOG}"

# Ensure we are at repo root
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

BUILD_GRADLE="app/build.gradle"

if [ ! -f "$BUILD_GRADLE" ]; then
  echo "Error: app/build.gradle not found."
  exit 1
fi

# Find current versionCode
CURRENT_VERSION_CODE=$(grep -E 'versionCode\s+[0-9]+' "$BUILD_GRADLE" | tr -dc '0-9')

if [ -z "$CURRENT_VERSION_CODE" ]; then
  echo "Error: Could not determine current versionCode from $BUILD_GRADLE"
  exit 1
fi

NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

echo "Updating $BUILD_GRADLE:"
echo "  versionCode: $CURRENT_VERSION_CODE -> $NEW_VERSION_CODE"
echo "  versionName: -> \"$VERSION_NAME\""

# Update versionCode and versionName in app/build.gradle
perl -pi -e "s/(versionCode\s+)[0-9]+/\${1}${NEW_VERSION_CODE}/" "$BUILD_GRADLE"
perl -pi -e "s/(versionName\s+)\"[^\"]+\"/\${1}\"${VERSION_NAME}\"/" "$BUILD_GRADLE"

# Update fastlane changelogs automatically pointing to GitHub Release notes
EN_CHANGELOG="fastlane/metadata/android/en-US/changelogs/${NEW_VERSION_CODE}.txt"
ES_CHANGELOG="fastlane/metadata/android/es-ES/changelogs/${NEW_VERSION_CODE}.txt"

mkdir -p "$(dirname "$EN_CHANGELOG")"
mkdir -p "$(dirname "$ES_CHANGELOG")"

echo "$CHANGELOG_MSG" > "$EN_CHANGELOG"
echo "$CHANGELOG_MSG" > "$ES_CHANGELOG"

echo "Created changelogs pointing to GitHub release notes:"
echo "  $EN_CHANGELOG"
echo "  $ES_CHANGELOG"

# Git operations
git add "$BUILD_GRADLE" "$EN_CHANGELOG" "$ES_CHANGELOG"
git commit -m "Bump version to ${VERSION_NAME}"
git tag "v${VERSION_NAME}"

echo ""
echo "Version ${VERSION_NAME} (code ${NEW_VERSION_CODE}) prepared and tagged."
echo "To trigger the release workflow, push the commit and tag:"
echo "  git push origin master"
echo "  git push origin v${VERSION_NAME}"
