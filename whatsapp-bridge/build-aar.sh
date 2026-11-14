#!/usr/bin/env bash
# Builds the WhatsApp linked-device library (whatsmeow) into an Android .aar with gomobile.
# Needs Go and an Android NDK (ANDROID_NDK_HOME, or the newest one under $ANDROID_HOME/ndk).
set -euo pipefail
out="${1:?usage: build-aar.sh <output.aar>}"
cd "$(dirname "$0")"
export GOBIN="$PWD/.bin"
export PATH="$GOBIN:$PATH"
go install golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  ndk="$(ls -d "$sdk"/ndk/*/ 2>/dev/null | sort -V | tail -n1 || true)"
  [ -n "$ndk" ] || { echo "No Android NDK found. Install one with: sdkmanager 'ndk;27.2.12479018'" >&2; exit 1; }
  export ANDROID_NDK_HOME="${ndk%/}"
fi
mkdir -p "$(dirname "$out")"
gomobile bind -target=android/arm64,android/amd64 -androidapi 30 -trimpath -ldflags="-s -w" -o "$out" .
