#!/usr/bin/env bash
# Generate a release upload keystore for Telegram TV Cast.
# Keep the resulting file and passwords private — they are required to update
# the app on Google Play. The keystore is git-ignored.
set -euo pipefail

KEYSTORE="${1:-release.keystore}"
ALIAS="${2:-telegramtvcast}"

if [ -f "$KEYSTORE" ]; then
  echo "Refusing to overwrite existing $KEYSTORE" >&2
  exit 1
fi

read -r -s -p "Store password: " STOREPASS; echo
read -r -s -p "Key password (Enter = same): " KEYPASS; echo
KEYPASS="${KEYPASS:-$STOREPASS}"

keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$STOREPASS" -keypass "$KEYPASS"

cat <<MSG

Done. Put this in keystore.properties (git-ignored):

  storeFile=$KEYSTORE
  storePassword=<your store password>
  keyAlias=$ALIAS
  keyPassword=<your key password>

Back up $KEYSTORE and the passwords somewhere safe.
MSG
