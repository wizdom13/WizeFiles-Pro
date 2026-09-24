#!/usr/bin/env bash
set -euo pipefail

APK_PATH=${1:-}
if [[ -z "$APK_PATH" || ! -f "$APK_PATH" ]]; then
  echo "Usage: $0 <apk-path>" >&2
  exit 2
fi

SCRIPT_DIRECTORY=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PROJECT_DIRECTORY=$(cd -- "$SCRIPT_DIRECTORY/.." && pwd)
NDK_VERSION=$(sed -n "s/^[[:space:]]*ndkVersion = '\([^']*\)'/\1/p" \
  "$PROJECT_DIRECTORY/app/build.gradle")
if [[ -z "$NDK_VERSION" ]]; then
  echo "Unable to resolve ndkVersion from app/build.gradle" >&2
  exit 1
fi

SDK_ROOT=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [[ -z "$SDK_ROOT" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME is required" >&2
  exit 1
fi
READELF="$SDK_ROOT/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
if [[ ! -x "$READELF" ]]; then
  echo "llvm-readelf not found for pinned NDK $NDK_VERSION" >&2
  exit 1
fi

mapfile -t ALL_LIBRARIES < <(unzip -Z1 "$APK_PATH" | awk '/^lib\/.*\.so$/')
mapfile -t LIBRARIES < <(
  printf '%s\n' "${ALL_LIBRARIES[@]}" | awk '/^lib\/(arm64-v8a|x86_64)\/.*\.so$/'
)
if [[ ${#LIBRARIES[@]} -eq 0 ]]; then
  echo "No 64-bit native libraries found in $APK_PATH" >&2
  exit 1
fi

TEMP_DIRECTORY=$(mktemp -d)
cleanup() {
  rm -rf -- "$TEMP_DIRECTORY"
}
trap cleanup EXIT
unzip -qq "$APK_PATH" "${LIBRARIES[@]}" -d "$TEMP_DIRECTORY"

FAILED=0
for ENTRY in "${LIBRARIES[@]}"; do
  LIBRARY_PATH="$TEMP_DIRECTORY/$ENTRY"
  mapfile -t ALIGNMENTS < <("$READELF" -lW "$LIBRARY_PATH" | awk '$1 == "LOAD" { print $NF }')
  if [[ ${#ALIGNMENTS[@]} -eq 0 ]]; then
    echo "$ENTRY has no ELF LOAD segments" >&2
    FAILED=1
    continue
  fi
  for ALIGNMENT in "${ALIGNMENTS[@]}"; do
    if (( ALIGNMENT < 0x4000 )); then
      echo "$ENTRY has LOAD alignment $ALIGNMENT; 0x4000 is required" >&2
      FAILED=1
      break
    fi
  done

  mapfile -t DEPENDENCIES < <(
    "$READELF" -dW "$LIBRARY_PATH" |
      awk '$2 == "(NEEDED)" { print substr($NF, 2, length($NF) - 2) }'
  )
  for DEPENDENCY in "${DEPENDENCIES[@]}"; do
    if [[ "$DEPENDENCY" == */* ]]; then
      echo "$ENTRY has a non-portable runtime dependency: $DEPENDENCY" >&2
      FAILED=1
    fi
  done

  if [[ "$ENTRY" == */libmobi-jni.so ]] &&
    ! printf '%s\n' "${DEPENDENCIES[@]}" | grep -Fxq 'libmobi.so'; then
    echo "$ENTRY must depend on libmobi.so by SONAME" >&2
    FAILED=1
  fi
done

if (( FAILED != 0 )); then
  exit 1
fi

BUILD_TOOLS_VERSION=$(sed -n "s/^[[:space:]]*buildToolsVersion = '\([^']*\)'/\1/p" \
  "$PROJECT_DIRECTORY/app/build.gradle")
ZIPALIGN="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION/zipalign"
if [[ -z "$BUILD_TOOLS_VERSION" || ! -x "$ZIPALIGN" ]]; then
  echo "zipalign not found for the pinned Android Build Tools" >&2
  exit 1
fi
"$ZIPALIGN" -c -P 16 -v 4 "$APK_PATH" >/dev/null

echo "Verified 16 KiB alignment and portable dependencies for ${#LIBRARIES[@]} 64-bit libraries"
