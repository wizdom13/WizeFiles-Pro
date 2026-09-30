#!/usr/bin/env bash
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
: "${FILE_TAG:?FILE_TAG is required}"
: "${SIGNED_RELEASE_FILES:?SIGNED_RELEASE_FILES is required}"
[[ "$FILE_TAG" =~ ^v[0-9][0-9A-Za-z._-]*$ ]] || exit 1

IFS=: read -r -a SIGNED_FILES <<< "$SIGNED_RELEASE_FILES"
if [[ ${#SIGNED_FILES[@]} -ne 3 ]]; then
  echo 'Expected exactly three signed release APKs' >&2
  exit 1
fi
OUTPUT=app/build/outputs/release-apks/signed
rm -rf -- "$OUTPUT"
mkdir -p -- "$OUTPUT"
for APK in "${SIGNED_FILES[@]}"; do
  NAME=$(basename -- "$APK")
  case "$NAME" in
    "WizeFiles_${FILE_TAG}-signed.apk"|\
    "WizeFiles_${FILE_TAG}_arm64-v8a-signed.apk"|\
    "WizeFiles_${FILE_TAG}_armeabi-v7a-signed.apk") ;;
    *) echo "Unexpected signed APK: $NAME" >&2; exit 1 ;;
  esac
  DESTINATION="$OUTPUT/${NAME%-signed.apk}.apk"
  [[ ! -e "$DESTINATION" ]] || { echo "Duplicate signed APK: $NAME" >&2; exit 1; }
  cp -- "$APK" "$DESTINATION"
done
python3 scripts/verify-release-apks.py "$OUTPUT" --file-tag "$FILE_TAG" --signed
