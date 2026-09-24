#!/usr/bin/env bash
set -euo pipefail

python3 scripts/setup_env.py

source .codex/env.sh

if [[ "${SMOKE_BUILD:-0}" == "1" ]]; then
  chmod +x ./gradlew
  ./gradlew --no-daemon :app:assembleDebug
fi
