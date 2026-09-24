#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

required_lockfiles=(
  "core-files-api/gradle.lockfile"
  "feature-browser-domain/gradle.lockfile"
  "feature-transfer-domain/gradle.lockfile"
  "feature-vault-domain/gradle.lockfile"
  "performance-baselines/gradle.lockfile"
  "app/gradle.lockfile"
)

for lockfile in "${required_lockfiles[@]}"; do
  if [[ ! -s "$lockfile" ]]; then
    echo "Missing or empty dependency lockfile: $lockfile" >&2
    exit 1
  fi
done

verification_file="gradle/verification-metadata.xml"
if [[ ! -s "$verification_file" ]]; then
  echo "Missing Gradle dependency verification metadata: $verification_file" >&2
  exit 1
fi

if ! grep -q '<verification-metadata' "$verification_file"; then
  echo "Invalid Gradle dependency verification metadata" >&2
  exit 1
fi

echo "Dependency lockfiles and verification metadata are present."
