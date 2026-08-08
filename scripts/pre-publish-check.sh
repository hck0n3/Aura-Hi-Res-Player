#!/usr/bin/env bash
# Pre-publish gate for Aura Hi-Res Player (Linux/macOS/CI).
# Usage:
#   ./scripts/pre-publish-check.sh
#   ./scripts/pre-publish-check.sh --build
#   ./scripts/pre-publish-check.sh --apk path/to/app.apk

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="${REPO:-hck0n3/Aura-Hi-Res-Player}"
DO_BUILD=0
APK_PATH=""
SKIP_GH=0
SKIP_COMPARE=0
FAIL=0
WARN=0

pass() { echo "[PASS] $1"; [[ -n "${2:-}" ]] && echo "      $2"; }
warn() { echo "[WARN] $1"; WARN=$((WARN + 1)); [[ -n "${2:-}" ]] && echo "      $2"; }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); [[ -n "${2:-}" ]] && echo "      $2"; }
info() { echo "[INFO] $1"; [[ -n "${2:-}" ]] && echo "      $2"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build) DO_BUILD=1; shift ;;
    --apk) APK_PATH="$2"; shift 2 ;;
    --skip-gh) SKIP_GH=1; shift ;;
    --skip-compare) SKIP_COMPARE=1; shift ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

read_local_prop() {
  local key="$1"
  if [[ -f "$ROOT/local.properties" ]]; then
    grep -E "^${key}=" "$ROOT/local.properties" | head -n1 | cut -d= -f2- | sed 's/\\:/:/g'
  fi
}

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$(read_local_prop sdk.dir)}}"
if [[ -z "$SDK_DIR" && -d "$HOME/Android/Sdk" ]]; then SDK_DIR="$HOME/Android/Sdk"; fi
APKSIGNER="$(find "$SDK_DIR/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n1 || true)"
if [[ -z "$APKSIGNER" || ! -x "$APKSIGNER" ]]; then
  fail "Android SDK / apksigner" "Set sdk.dir or ANDROID_SDK_ROOT."
  exit 1
fi
pass "Android SDK / apksigner" "$APKSIGNER"

if [[ "$SKIP_GH" -eq 0 ]]; then
  if ! command -v gh >/dev/null 2>&1; then
    fail "1/4 GitHub secrets" "gh CLI not found."
  else
    missing=()
    for s in RELEASE_KEYSTORE_BASE64 SUPERPOWERED_LICENSE_KEY RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
      gh secret list --repo "$REPO" | awk '{print $1}' | grep -qx "$s" || missing+=("$s")
    done
    if ((${#missing[@]} > 0)); then
      fail "1/4 GitHub secrets" "Missing in $REPO: ${missing[*]}"
    else
      pass "1/4 GitHub secrets" "All required secrets present in $REPO."
    fi
  fi
else
  info "1/4 GitHub secrets" "Skipped (--skip-gh)."
fi

if [[ ! -f "$ROOT/RELEASE_INFO.md" ]]; then
  fail "Release metadata" "RELEASE_INFO.md not found."
else
  title="$(head -n1 "$ROOT/RELEASE_INFO.md" | tr -d '\r')"
  version_name="$(grep -m1 'versionName' "$ROOT/app/build.gradle.kts" | sed -n 's/.*"\([^"]*\)".*/\1/p')"
  version_code="$(grep -m1 'versionCode' "$ROOT/app/build.gradle.kts" | sed -n 's/.*=\s*\([0-9][0-9]*\).*/\1/p')"
  if [[ "$version_name" == *"-beta"* || "$version_name" == *"-test"* ]]; then
    warn "Release metadata" "versionName=$version_name looks like a prerelease."
  else
    pass "Release metadata" "versionName=$version_name, versionCode=$version_code, title OK."
  fi
fi

keystore="$ROOT/app/keystore/release.keystore"
if [[ -f "$keystore" && -n "${STORE_PASSWORD:-$(read_local_prop STORE_PASSWORD)}" ]]; then
  pass "Local signing readiness" "Release keystore + credentials available."
else
  warn "Local signing readiness" "Local build may ship an UNBOUND Superpowered key."
fi

cd "$ROOT"
task="projects"
[[ "$DO_BUILD" -eq 1 ]] && task="assembleUniversalGmsRelease"
info "2/4 Gradle Superpowered gate" "Running ./gradlew $task ..."
gradle_out="$(./gradlew "$task" --no-daemon 2>&1)" || {
  fail "2/4 Gradle Superpowered gate" "Gradle failed."
  echo "$gradle_out" | tail -n 12
}
if echo "$gradle_out" | grep -q 'SUPERPOWERED:.*no licence key configured'; then
  fail "2/4 Gradle Superpowered gate" "Build would ship WITHOUT the Superpowered engine."
elif echo "$gradle_out" | grep -q 'SUPERPOWERED:.*embedded UNBOUND'; then
  warn "2/4 Gradle Superpowered gate" "Key is UNBOUND (engine works, clone protection weaker)."
elif echo "$gradle_out" | grep -q 'Generated fallback CI keystore'; then
  fail "2/4 Gradle Superpowered gate" "Emergency keystore detected."
else
  pass "2/4 Gradle Superpowered gate" "No blocking SUPERPOWERED warnings."
fi

build_config="$(find "$ROOT/app/build/generated/source/buildConfig/universalGms/release" -name BuildConfig.java 2>/dev/null | head -n1 || true)"
if [[ -z "$build_config" ]]; then
  info "Superpowered binding (BuildConfig)" "Run with --build after a release build."
elif grep -q 'SUPERPOWERED_LICENSE_BOUND = true' "$build_config"; then
  pass "Superpowered binding (BuildConfig)" "SUPERPOWERED_LICENSE_BOUND=true."
elif grep -q 'SUPERPOWERED_LICENSE = ""' "$build_config"; then
  fail "Superpowered binding (BuildConfig)" "SUPERPOWERED_LICENSE is empty."
else
  warn "Superpowered binding (BuildConfig)" "SUPERPOWERED_LICENSE_BOUND is false."
fi

if [[ -z "$APK_PATH" ]]; then
  APK_PATH="$(find "$ROOT/app/build/outputs/apk/universalGms/release" -name '*.apk' 2>/dev/null | head -n1 || true)"
fi

if [[ -z "$APK_PATH" || ! -f "$APK_PATH" ]]; then
  info "3/4 APK signing certificate" "No release APK found. Use --build or --apk."
else
  cert_out="$("$APKSIGNER" verify --print-certs "$APK_PATH" 2>&1)"
  owner="$(echo "$cert_out" | sed -n 's/^Owner: //p' | head -n1)"
  sha256="$(echo "$cert_out" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr -d ':' | tr 'A-F' 'a-f' | head -n1)"
  if [[ "$owner" == *"CN=JR-MUSIC-PRO"* ]]; then
    fail "3/4 APK signing certificate" "Emergency CI keystore (CN=JR-MUSIC-PRO)."
  elif [[ "$owner" != *"CN=JR MUSIC PRO"* ]]; then
    fail "3/4 APK signing certificate" "Unexpected signer: $owner"
  else
    pass "3/4 APK signing certificate" "Signer OK. SHA-256=$sha256"
  fi

  if [[ "$SKIP_COMPARE" -eq 0 && "$SKIP_GH" -eq 0 ]] && command -v gh >/dev/null 2>&1; then
    tmp="$(mktemp -d)"
    if gh release download --repo "$REPO" --pattern "*.apk" --dir "$tmp" >/dev/null 2>&1; then
      pub_apk="$(find "$tmp" -name '*.apk' | head -n1 || true)"
      if [[ -n "$pub_apk" ]]; then
        pub_out="$("$APKSIGNER" verify --print-certs "$pub_apk" 2>&1)"
        pub_sha="$(echo "$pub_out" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr -d ':' | tr 'A-F' 'a-f' | head -n1)"
        if [[ "$pub_sha" == "$sha256" ]]; then
          pass "Compare with latest release" "Certificate matches published APK."
        else
          fail "Compare with latest release" "Certificate differs from latest published APK."
        fi
      else
        warn "Compare with latest release" "Latest release has no APK asset."
      fi
    else
      warn "Compare with latest release" "Could not download latest release APK."
    fi
    rm -rf "$tmp"
  fi
fi

info "4/4 On-device Superpowered log" "Install APK, play one track, open Ajustes > Registros, search: SUPERPOWERED licence=ok binding=certificate"

echo
if [[ "$FAIL" -gt 0 ]]; then
  echo "BLOCKED: $FAIL failure(s), $WARN warning(s). Do NOT publish."
  exit 1
fi
if [[ "$WARN" -gt 0 ]]; then
  echo "CAUTION: $WARN warning(s). Review before publishing."
  exit 2
fi
echo "READY: all automated checks passed."
exit 0
