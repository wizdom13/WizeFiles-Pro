#!/usr/bin/env bash
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail

cd "$(dirname "$0")/.."

gate_file="config/p0-release-gates.txt"
mapfile -t open_gates < <(sed -e 's/[[:space:]]*#.*$//' -e '/^[[:space:]]*$/d' "$gate_file")

if (( ${#open_gates[@]} > 0 )); then
    printf 'Release blocked by unresolved P0 correctness risks:\n' >&2
    printf '  - %s\n' "${open_gates[@]}" >&2
    printf 'Resolve each risk with outcome-based tests before removing its gate.\n' >&2
    exit 1
fi

echo "No unresolved P0 release risks."
