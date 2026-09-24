#!/usr/bin/env bash
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail

python3 scripts/setup_env.py

source .codex/env.sh

if [[ "${SMOKE_BUILD:-0}" == "1" ]]; then
  chmod +x ./gradlew
  ./gradlew --no-daemon :app:assembleDebug
fi
