#!/usr/bin/env bash
#
# One-time setup for automated release signing.
#
# Generates an Android upload keystore with strong random passwords, uploads
# the keystore and credentials to this repo's GitHub Actions secrets, and saves
# a local backup. After running this once, every `git tag vX.Y.Z && git push
# --tags` produces a signed APK published to GitHub Releases — no manual steps.
#
# Requirements: keytool (JDK), gh (authenticated), openssl.
# Re-running rotates the key; only do that BEFORE you ship the first signed APK,
# otherwise existing users can no longer install updates in place.
#
# Usage:
#   ./scripts/setup-signing.sh
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${REPO_ROOT}/.signing-backup"   # gitignored
KEYSTORE_PATH="${BACKUP_DIR}/release.jks"
CREDS_PATH="${BACKUP_DIR}/signing-credentials.txt"
KEY_ALIAS="${KEY_ALIAS:-airgradient}"

# Certificate identity — override via env if you like.
DNAME="${DNAME:-CN=AirGradient, OU=worxbend, O=worxbend, C=US}"

command -v keytool >/dev/null || { echo "keytool not found (install a JDK)"; exit 1; }
command -v gh >/dev/null      || { echo "gh not found (install GitHub CLI)"; exit 1; }
command -v openssl >/dev/null  || { echo "openssl not found"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh is not authenticated; run: gh auth login"; exit 1; }

if [ -f "${KEYSTORE_PATH}" ]; then
  echo "A keystore already exists at ${KEYSTORE_PATH}."
  echo "Refusing to overwrite. Delete it manually first if you really want to rotate the key."
  exit 1
fi

mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"

# Strong random password (URL-safe, no shell-hostile chars). PKCS12 keystores
# (keytool's default) require the key password to equal the store password, so
# we use a single password for both.
STORE_PASS="$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)"
KEY_PASS="${STORE_PASS}"

echo "==> Generating keystore (alias: ${KEY_ALIAS})"
keytool -genkeypair -v \
  -keystore "${KEYSTORE_PATH}" \
  -alias "${KEY_ALIAS}" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "${STORE_PASS}" \
  -keypass "${KEY_PASS}" \
  -dname "${DNAME}"

KEYSTORE_BASE64="$(base64 -w0 "${KEYSTORE_PATH}" 2>/dev/null || base64 "${KEYSTORE_PATH}" | tr -d '\n')"

echo "==> Uploading secrets to GitHub Actions"
printf '%s' "${KEYSTORE_BASE64}" | gh secret set KEYSTORE_BASE64
printf '%s' "${STORE_PASS}"      | gh secret set KEYSTORE_PASSWORD
printf '%s' "${KEY_ALIAS}"       | gh secret set KEY_ALIAS
printf '%s' "${KEY_PASS}"        | gh secret set KEY_PASSWORD

# Local backup of the credentials (gitignored). Keep this somewhere safe.
umask 077
cat > "${CREDS_PATH}" <<EOF
AirGradient release signing credentials — KEEP SAFE, DO NOT COMMIT
Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

Keystore file:     ${KEYSTORE_PATH}
KEYSTORE_PASSWORD: ${STORE_PASS}
KEY_ALIAS:         ${KEY_ALIAS}
KEY_PASSWORD:      ${KEY_PASS}
EOF
chmod 600 "${CREDS_PATH}" "${KEYSTORE_PATH}"

echo
echo "Done. GitHub secrets set:"
gh secret list
echo
echo "Backup written to ${BACKUP_DIR} (gitignored)."
echo "IMPORTANT: copy ${BACKUP_DIR} to secure offline storage (a password manager"
echo "or encrypted backup). If you lose this key, you cannot ship in-place updates."
