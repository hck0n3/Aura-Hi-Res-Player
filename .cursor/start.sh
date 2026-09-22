#!/usr/bin/env bash
# Per-boot reconciliation for Aura Hi-Res Player. Fast and idempotent: it does
# NOT install anything (the SDK lives in the snapshot from install.sh). It only
# re-asserts the two local, gitignored files the build needs, in case a fresh
# checkout removed them. No long-running process is started.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"

# sdk.dir for the Android Gradle Plugin (overrides the macOS placeholder in the
# committed gradle.properties so the build resolves the SDK on this Linux VM).
if [[ -d "$ANDROID_SDK_ROOT" ]]; then
  printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > "$REPO_DIR/local.properties"
fi

# Debug signing config expects ~/.android/debug.keystore.
DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
if [[ ! -f "$DEBUG_KEYSTORE" ]]; then
  mkdir -p "$HOME/.android"
  keytool -genkeypair -v -keystore "$DEBUG_KEYSTORE" \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1 || true
fi

echo "start.sh: local.properties and debug keystore ready (ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT)"
