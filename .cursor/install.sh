#!/usr/bin/env bash
# Idempotent Cloud Agent setup for Aura Hi-Res Player (Android / Gradle).
# Installs the Android SDK components the build needs, wires up local.properties,
# ensures a debug keystore, and warms the Gradle dependency cache.
set -euo pipefail

# --- Config -----------------------------------------------------------------
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"  # commandlinetools-linux-<ver>_latest.zip
COMPILE_SDK="36"
BUILD_TOOLS_VERSION="36.0.0"
NDK_VERSION="27.0.12077973"
CMAKE_VERSION="3.22.1"

export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"

echo "==> Repo:        $REPO_DIR"
echo "==> SDK root:    $ANDROID_SDK_ROOT"
java -version

# --- 1. Android command-line tools ------------------------------------------
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  echo "==> Installing Android command-line tools..."
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  tmp_zip="$(mktemp --suffix=.zip)"
  curl -fsSL -o "$tmp_zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
  unzip -q "$tmp_zip" -d "$ANDROID_SDK_ROOT/cmdline-tools/tmp"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$ANDROID_SDK_ROOT/cmdline-tools/tmp/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/tmp" "$tmp_zip"
else
  echo "==> Android command-line tools already present."
fi

# --- 2. Accept licenses + install required packages -------------------------
echo "==> Accepting SDK licenses..."
yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true

echo "==> Installing SDK packages (idempotent)..."
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" \
  "platforms;android-${COMPILE_SDK}" \
  "build-tools;${BUILD_TOOLS_VERSION}" \
  "ndk;${NDK_VERSION}" \
  "cmake;${CMAKE_VERSION}"

# --- 3. local.properties (sdk.dir) ------------------------------------------
# The build reads sdk.dir from local.properties (gitignored). This overrides the
# macOS placeholder committed in gradle.properties so the build works on Linux.
echo "==> Writing local.properties..."
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > "$REPO_DIR/local.properties"

# --- 4. Debug keystore ------------------------------------------------------
# The debug signing config points at ~/.android/debug.keystore. Generate a
# standard one if the runner has none (matches the CI test-build workflow).
DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
if [[ ! -f "$DEBUG_KEYSTORE" ]]; then
  echo "==> Generating Android debug keystore..."
  mkdir -p "$HOME/.android"
  keytool -genkeypair -v -keystore "$DEBUG_KEYSTORE" \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
else
  echo "==> Debug keystore already present."
fi

# --- 5. Warm Gradle dependency cache ----------------------------------------
# Downloads the Gradle distribution + build dependencies so the first build for
# an agent is fast. Non-fatal: a network hiccup here should not fail setup.
echo "==> Warming Gradle (downloading dependencies)..."
cd "$REPO_DIR"
./gradlew --no-daemon help >/dev/null 2>&1 || echo "   (gradle warm-up skipped/failed; non-fatal)"

echo "==> Setup complete. ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
