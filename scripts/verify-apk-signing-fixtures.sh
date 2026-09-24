#!/usr/bin/env bash
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail

SCRIPT_DIRECTORY=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PROJECT_DIRECTORY=$(cd -- "$SCRIPT_DIRECTORY/.." && pwd)
FIXTURE_DIRECTORY="$PROJECT_DIRECTORY/app/src/test/resources/apk-signing"
BUILD_TOOLS_VERSION=$(sed -n "s/^[[:space:]]*buildToolsVersion = '\([^']*\)'/\1/p" \
  "$PROJECT_DIRECTORY/app/build.gradle")
SDK_ROOT=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
APKSIGNER="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/apksigner"

if [[ -z "$SDK_ROOT" || -z "$BUILD_TOOLS_VERSION" || ! -x "$APKSIGNER" ]]; then
  echo "apksigner from the pinned Android Build Tools is required" >&2
  exit 1
fi

verify_fixture() {
  local apk=$1
  local min_sdk=$2
  local max_sdk=$3
  local expected_v1=$4
  local expected_v2=$5
  local expected_v3=$6
  local expected_v4=$7
  local idsig=${8:-}
  local -a command=(
    "$APKSIGNER" verify --verbose --print-certs
    --min-sdk-version "$min_sdk" --max-sdk-version "$max_sdk"
  )
  if [[ -n "$idsig" ]]; then
    command+=(--v4-signature-file "$FIXTURE_DIRECTORY/$idsig")
  fi
  command+=("$FIXTURE_DIRECTORY/$apk")

  local output
  output=$("${command[@]}")
  grep -Fq "Verified using v1 scheme (JAR signing): $expected_v1" <<<"$output"
  grep -Fq "Verified using v2 scheme (APK Signature Scheme v2): $expected_v2" <<<"$output"
  grep -Fq "Verified using v3 scheme (APK Signature Scheme v3): $expected_v3" <<<"$output"
  grep -Fq "Verified using v4 scheme (APK Signature Scheme v4): $expected_v4" <<<"$output"
  grep -Fq 'Signer #1 certificate SHA-256 digest:' <<<"$output"
  echo "Verified $apk with Build Tools $BUILD_TOOLS_VERSION"
}

verify_fixture v1.apk 21 23 true false false false
verify_fixture v2.apk 24 27 false true false false
verify_fixture v3.apk 24 35 false true true false
verify_fixture v4.apk 24 35 false true true true v4.apk.idsig

if unzip -Z1 "$FIXTURE_DIRECTORY/v4.apk" | grep -Fq '.idsig'; then
  echo "v4 idsig must remain detached from the APK" >&2
  exit 1
fi

echo "Verified all APK signing fixtures with the host Android apksigner"
