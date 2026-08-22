#!/usr/bin/env bash
set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <version-name>"
  echo "Example: $0 0.2"
  exit 1
fi

VERSION_NAME="$1"
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

# Git operations
git add "$BUILD_GRADLE"
git commit -m "Bump version to ${VERSION_NAME}"
git tag "v${VERSION_NAME}"

echo ""
echo "Version ${VERSION_NAME} (code ${NEW_VERSION_CODE}) prepared and tagged."
echo "To trigger the release workflow, push the commit and tag:"
echo "  git push origin master"
echo "  git push origin v${VERSION_NAME}"
