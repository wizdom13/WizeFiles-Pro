#!/usr/bin/env bash
set -euo pipefail

readonly MAX_SOURCE_TEST_FILES=90
readonly TEST_ROOT="app/src/test"

mapfile -d '' source_tests < <(
  find "$TEST_ROOT" -type f -name '*SourceTest.kt' -print0 | sort -z
)
count=${#source_tests[@]}

if (( count > MAX_SOURCE_TEST_FILES )); then
  echo "Source-contract test budget exceeded: $count > $MAX_SOURCE_TEST_FILES" >&2
  echo "Add outcome-based tests or migrate an existing source-spelling test first." >&2
  printf '%s\n' "${source_tests[@]}" >&2
  exit 1
fi

echo "Source-contract test budget: $count / $MAX_SOURCE_TEST_FILES"
