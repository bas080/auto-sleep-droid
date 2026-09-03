#!/usr/bin/env bash
set -euo pipefail

KEYSTORE_FILE="release.jks"
KEYSTORE_BASE64_FILE="keystore_base64.txt"
KEY_ALIAS="autosleepdroid"

echo "=== Auto Sleep Droid Keystore Generator ==="
echo ""

# Check and install prerequisites on Debian/Ubuntu if needed
if ! command -v keytool &> /dev/null || ! command -v openssl &> /dev/null || ! command -v base64 &> /dev/null; then
    echo "Installing missing prerequisites (openjdk-17-jdk-headless, openssl, coreutils)..."
    if [ "$(id -u)" -ne 0 ]; then
        if command -v sudo &> /dev/null; then
            sudo apt-get update && sudo apt-get install -y openjdk-17-jdk-headless openssl coreutils
        else
            echo "Error: root or sudo required to install prerequisites using apt-get."
            exit 1
        fi
    else
        apt-get update && apt-get install -y openjdk-17-jdk-headless openssl coreutils
    fi
fi

if [ -f "$KEYSTORE_FILE" ]; then
    echo "Warning: $KEYSTORE_FILE already exists in current directory."
    read -p "Overwrite existing keystore? (y/N): " -r CHOICE
    if [[ ! "$CHOICE" =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
    rm -f "$KEYSTORE_FILE" "$KEYSTORE_BASE64_FILE"
fi

# Generate secure random password (PKCS12 keystores require storepass and keypass to match)
KEYSTORE_PASSWORD=$(openssl rand -base64 18 | tr -dc 'a-zA-Z0-9')
KEY_PASSWORD="$KEYSTORE_PASSWORD"

echo "Generating Android release keystore ($KEYSTORE_FILE)..."
keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=AutoSleepDroid, OU=Mobile, O=AutoSleepDroid, L=Unknown, ST=Unknown, C=US"

# Encode keystore to base64
echo "Encoding keystore to base64 ($KEYSTORE_BASE64_FILE)..."
base64 "$KEYSTORE_FILE" | tr -d '\r\n' > "$KEYSTORE_BASE64_FILE"

echo ""
echo "============================================================"
echo " Keystore Generation Complete!"
echo "============================================================"
echo ""
echo "Files created:"
echo "  - $KEYSTORE_FILE (Keep this secure and backed up!)"
echo "  - $KEYSTORE_BASE64_FILE (Contains Base64 string for GitHub)"
echo ""
echo "Add the following GitHub Secrets under Settings -> Secrets and variables -> Actions:"
echo ""
echo "1. KEYSTORE_BASE64:"
echo "   (Copy contents of $KEYSTORE_BASE64_FILE or run: cat $KEYSTORE_BASE64_FILE)"
echo ""
echo "2. KEYSTORE_PASSWORD:"
echo "   $KEYSTORE_PASSWORD"
echo ""
echo "3. KEY_ALIAS:"
echo "   $KEY_ALIAS"
echo ""
echo "4. KEY_PASSWORD:"
echo "   $KEY_PASSWORD"
echo ""
echo "============================================================"
