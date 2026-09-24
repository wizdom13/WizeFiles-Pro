#!/usr/bin/env bash
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail

EXPECTED_SHA256=948321f77e13368aa0c5f4defe73971578a5f18fdf1b49c11756ef6a15c95586
EXPECTED_SIZE=431752
CACHE_ROOT=${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/com.github.MuntashirAkon/apksig-android/4.4.0

mapfile -t AARS < <(find "$CACHE_ROOT" -type f -name 'apksig-android-4.4.0.aar' -print)
if [[ ${#AARS[@]} -ne 1 ]]; then
  echo "Expected one resolved apksig-android 4.4.0 AAR, found ${#AARS[@]}" >&2
  exit 1
fi

AAR=${AARS[0]}
ACTUAL_SHA256=$(sha256sum "$AAR" | awk '{print $1}')
ACTUAL_SIZE=$(stat -c '%s' "$AAR")
if [[ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]]; then
  echo "apksig-android digest mismatch: $ACTUAL_SHA256" >&2
  exit 1
fi
if [[ "$ACTUAL_SIZE" != "$EXPECTED_SIZE" ]]; then
  echo "apksig-android size mismatch: $ACTUAL_SIZE bytes" >&2
  exit 1
fi
if unzip -Z1 "$AAR" | grep -Eq '^jni/|^lib/.*\.so$'; then
  echo "apksig-android unexpectedly contains native libraries" >&2
  exit 1
fi

echo "Verified apksig-android 4.4.0: $ACTUAL_SIZE bytes, SHA-256 $ACTUAL_SHA256, no native libraries"
