#!/usr/bin/env bash
# Builds and installs the debug app, then saves every screenshot state in light and dark:
#   scripts/capture.sh <outdir> [serial]
set -euo pipefail
OUT=${1:?usage: scripts/capture.sh <outdir> [serial]}
SERIAL=${2:-${ANDROID_SERIAL:-}}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}
ADB=("$ANDROID_HOME/platform-tools/adb")
[ -n "$SERIAL" ] && ADB+=(-s "$SERIAL")
PKG=dev.hamper.uli.debug
ACTIVITY="$PKG/dev.hamper.uli.MainActivity"
SHOTS=(home q-single q-wrong q-right q-multi round-end exam-result browse)

mkdir -p "$OUT"
(cd "$ROOT" && ./gradlew -q :app:assembleDebug)
"${ADB[@]}" install -r -t "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null

original_night=$("${ADB[@]}" shell cmd uimode night | awk '{print $NF}' | tr -d '\r')
cleanup() {
  "${ADB[@]}" shell cmd uimode night "${original_night:-no}" >/dev/null 2>&1 || true
  "${ADB[@]}" shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Wake up, unlock and freeze the status bar (12:00, full battery, no notifications).
"${ADB[@]}" shell input keyevent KEYCODE_WAKEUP
"${ADB[@]}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
"${ADB[@]}" shell settings put global sysui_demo_allowed 1
demo() { "${ADB[@]}" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide
demo notifications -e visible false

"${ADB[@]}" logcat -c
for mode in light dark; do
  "${ADB[@]}" shell cmd uimode night "$([ "$mode" = dark ] && echo yes || echo no)" >/dev/null
  sleep 1
  for shot in "${SHOTS[@]}"; do
    "${ADB[@]}" shell am force-stop "$PKG"
    "${ADB[@]}" shell am start -W -n "$ACTIVITY" --es shot "$shot" >/dev/null
    sleep 2
    if [ -z "$("${ADB[@]}" shell pidof "$PKG" | tr -d '\r')" ]; then
      echo "App died on shot $shot ($mode)" >&2
      "${ADB[@]}" logcat -d | grep -A 20 "FATAL" >&2 || true
      exit 1
    fi
    "${ADB[@]}" exec-out screencap -p > "$OUT/$shot-$mode.png"
    echo "saved $OUT/$shot-$mode.png"
  done
done
"${ADB[@]}" shell am force-stop "$PKG"

if "${ADB[@]}" logcat -d | grep -q "FATAL EXCEPTION"; then
  echo "A FATAL exception was logged:" >&2
  "${ADB[@]}" logcat -d | grep -A 20 "FATAL EXCEPTION" >&2
  exit 1
fi
echo "done: ${#SHOTS[@]} shots x 2 themes in $OUT"
