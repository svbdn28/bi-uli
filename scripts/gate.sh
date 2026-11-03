#!/usr/bin/env bash
# Gate: unit tests, debug build, content check, no em dashes in UI copy. Non-zero exit on any failure.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}

echo "== unit tests + assembleDebug"
./gradlew -q :app:testDebugUnitTest :app:assembleDebug

echo "== content check"
python3 content/check.py

echo "== em dash check (res/ and Kotlin sources)"
hits=$(grep -rn $'—' app/src/main/res app/src --include='*.kt' --include='*.xml' || true)
if [ -n "$hits" ]; then
  echo "Found an em dash:"
  echo "$hits"
  exit 1
fi

echo "gate OK"
