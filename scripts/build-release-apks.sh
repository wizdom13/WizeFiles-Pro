#!/usr/bin/env bash
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

VERSION=$(sed -n "s/^def appVersionName = '\([^']*\)'$/\1/p" app/build.gradle)
FILE_TAG=${FILE_TAG:-v$VERSION}
if [[ -z "$VERSION" || ! "$FILE_TAG" =~ ^v[0-9][0-9A-Za-z._-]*$ ]]; then
  echo 'Invalid app version or FILE_TAG' >&2
  exit 1
fi

# A separate staging directory prevents signing stale APKs from earlier builds.
OUTPUT=app/build/outputs/release-apks/unsigned
rm -rf -- "$OUTPUT"
mkdir -p -- "$OUTPUT"
for ABI in universal arm64-v8a armeabi-v7a; do
  ./gradlew :app:assembleRelease --warning-mode all "-PwizefilesReleaseAbi=$ABI"
  SUFFIX="_$ABI"
  [[ "$ABI" != universal ]] || SUFFIX=''
  cp app/build/outputs/apk/release/app-release-unsigned.apk \
    "$OUTPUT/WizeFiles_${FILE_TAG}${SUFFIX}.apk"
done

python3 scripts/verify-release-apks.py "$OUTPUT" --file-tag "$FILE_TAG"
for APK in "$OUTPUT"/*.apk; do
  python3 scripts/verify-no-google-mobile-sdks.py "$APK"
done
bash scripts/verify-native-page-size.sh "$OUTPUT/WizeFiles_${FILE_TAG}.apk"
bash scripts/verify-native-page-size.sh "$OUTPUT/WizeFiles_${FILE_TAG}_arm64-v8a.apk"
