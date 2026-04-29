#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-/Users/apple/Library/Android/sdk/platform-tools/adb}"
APK="${1:-app/build/outputs/apk/debug/BhadaAccepter_Latest.apk}"
PKG="com.autoride.accepter"
ACTIVITY="com.autoride.accepter/.LoginActivity"

if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 1
fi

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No adb device connected. Connect a device, then rerun." >&2
  exit 2
fi

echo "== Installing APK =="
"$ADB" install -r "$APK"

echo "== Resetting logcat =="
"$ADB" logcat -c

echo "== Launching app =="
"$ADB" shell am start -n "$ACTIVITY" >/dev/null

echo "== App launched =="
echo "1) Enable Accessibility for 'Accessibility Optimizer' in Settings"
echo "2) Open Rapido/Ola/Uber request screen"
echo "3) Watch logs below for detection and click attempts"
echo
echo "== Streaming filtered logs (Ctrl+C to stop) =="
"$ADB" logcat -v time | grep -E "RideAcceptService|FilterEngine|RideAcceptor|AccessibilityManager"
