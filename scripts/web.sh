#!/usr/bin/env bash
# iPhone version (docs/, served by GitHub Pages): copy the questions over, run the engine tests.
# After changing any file in docs/, bump VERSION in docs/sw.js so installed phones pick up the new copy.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
python3 content/check.py
cp content/questions.json docs/questions.json
(cd docs && node --test tests/*.test.js)
hits=$(grep -n $'—' docs/*.js docs/*.css docs/index.html || true)
[ -z "$hits" ] || { echo "Found an em dash:"; echo "$hits"; exit 1; }
echo "web OK"
