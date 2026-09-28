#!/usr/bin/env bash
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail

SYNCTHING_VERSION=v2.1.5
SYNCTHING_COMMIT=2ca95cf1498104113fdfde46df4107f2450a0f71
NDK_VERSION=29.0.14206865
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:-$root/app/build/generated/syncthing/jniLibs}"
mkdir -p "$output"
output="$(cd "$output" && pwd)"
build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT
for command in go git; do
    command -v "$command" >/dev/null || { echo "Required tool missing: $command" >&2; exit 1; }
done
[[ "$(go env GOVERSION)" == go1.26.5 ]] || { echo 'Syncthing requires Go 1.26.5' >&2; exit 1; }
git -C "$build_dir" init -q
git -C "$build_dir" remote add origin https://github.com/syncthing/syncthing.git
git -C "$build_dir" fetch --depth=1 origin "$SYNCTHING_COMMIT"
git -C "$build_dir" checkout -q --detach FETCH_HEAD
[[ "$(git -C "$build_dir" rev-parse HEAD)" == "$SYNCTHING_COMMIT" ]]
cd "$build_dir"
export BUILD_USER=WizeFiles BUILD_HOST=reproducible

# Host mode exercises the exact pinned upstream source in the protocol integration test.
if [[ "${SYNCTHING_HOST_BUILD:-0}" == 1 ]]; then
    go run build.go -no-upgrade -version "$SYNCTHING_VERSION" -build-out "$output/syncthing" build syncthing
    cc -std=c11 -D_POSIX_C_SOURCE=200809L -Wall -Wextra -Werror -O2 \
        "$root/app/src/main/jni/syncthing_launcher.c" -o "$output/syncthing-launcher"
    exit
fi
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$sdk" ]] || { echo 'Set ANDROID_SDK_ROOT to the Android SDK directory' >&2; exit 1; }
ndk="$sdk/ndk/$NDK_VERSION"
if [[ ! -d "$ndk" ]]; then
    "$sdk/cmdline-tools/latest/bin/sdkmanager" "ndk;$NDK_VERSION"
fi
case "$(uname -s)" in
    Linux) host=linux-x86_64 ;;
    Darwin) host=darwin-x86_64 ;;
    *) echo 'Build Syncthing from Linux or macOS' >&2; exit 1 ;;
esac
toolchain="$ndk/toolchains/llvm/prebuilt/$host/bin"
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
    case "$abi" in
        arm64-v8a) arch=arm64; target=aarch64-linux-android ;;
        armeabi-v7a) arch=arm; target=armv7a-linux-androideabi ;;
        x86_64) arch=amd64; target=x86_64-linux-android ;;
        x86) arch=386; target=i686-linux-android ;;
    esac
    compiler="$toolchain/${target}30-clang"
    mkdir -p "$output/$abi"
    # Upstream's Android interface adapter requires this linker flag on Go >=1.23.
    # https://github.com/wlynxg/anet#compile
    CGO_ENABLED=1 GOARM=7 GOFLAGS=-buildmode=pie \
        EXTRA_LDFLAGS='-checklinkname=0 -linkmode=external -extldflags=-Wl,-z,max-page-size=16384' \
        go run build.go -goos android -goarch "$arch" -cc "$compiler" -no-upgrade \
        -version "$SYNCTHING_VERSION" -build-out "$output/$abi/libsyncthing.so" build syncthing
    "$compiler" -std=c11 -D_POSIX_C_SOURCE=200809L -Wall -Wextra -Werror -O2 -fPIE -pie \
        -Wl,-z,max-page-size=16384 "$root/app/src/main/jni/syncthing_launcher.c" \
        -o "$output/$abi/libsyncthing-launcher.so"
    for executable in "$output/$abi/"*.so; do
        "$toolchain/llvm-readelf" -lW "$executable" > "$build_dir/headers"
        awk '$1 == "LOAD" && $NF != "0x4000" { exit 1 }' "$build_dir/headers"
    done
done
