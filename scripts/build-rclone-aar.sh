#!/usr/bin/env bash
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail

RCLONE_VERSION="v1.74.4"
GOMOBILE_VERSION="v0.0.0-20260709172247-6129f5bee9d5"
ANDROID_API="30"

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_path="${1:-${repository_root}/app/libs/rclone-gomobile.aar}"
build_directory="$(mktemp -d)"

cleanup() {
    rm -rf "${build_directory}"
}
trap cleanup EXIT

for command in go unzip readelf; do
    if ! command -v "${command}" >/dev/null 2>&1; then
        echo "Required command is not installed: ${command}" >&2
        exit 1
    fi
done

export PATH="${PATH}:$(go env GOPATH)/bin"
export CGO_LDFLAGS="-Wl,-z,max-page-size=16384"

go install "golang.org/x/mobile/cmd/gomobile@${GOMOBILE_VERSION}"
gomobile init

cd "${build_directory}"
go mod init com.wisso.wizefiles.rclonebuild
go get "github.com/rclone/rclone@${RCLONE_VERSION}"
go get "golang.org/x/mobile@${GOMOBILE_VERSION}"
cp -R "${repository_root}/rclone-mobile" ./gomobile

mkdir -p "$(dirname "${output_path}")"
gomobile bind \
    -trimpath \
    -target=android/arm,android/arm64 \
    -androidapi="${ANDROID_API}" \
    -ldflags="-linkmode=external -extldflags=-Wl,-z,max-page-size=16384" \
    -javapkg=org.rclone \
    -o "${output_path}" \
    ./gomobile

mkdir verify
unzip -q "${output_path}" "jni/*/libgojni.so" -d verify
for library in verify/jni/*/libgojni.so; do
    readelf -lW "${library}" > program-headers.txt
    if awk '$1 == "LOAD" && $NF != "0x4000" { exit 1 }' program-headers.txt; then
        echo "Verified 16 KB ELF alignment: ${library}"
    else
        echo "Native library is not 16 KB aligned: ${library}" >&2
        exit 1
    fi
done

sha256sum "${output_path}"
