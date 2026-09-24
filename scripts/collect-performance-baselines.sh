#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="${1:-$repo_root/performance-baselines/results}"
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"

export WIZEFILES_BENCHMARK_OUTPUT="$output_dir/baselines.csv"
timeout 20m "$repo_root/gradlew" -p "$repo_root" -PcoreOnly :performance-baselines:run --quiet

python3 - "$WIZEFILES_BENCHMARK_OUTPUT" <<'PY'
import csv
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
rows = list(csv.DictReader(path.open(encoding="utf-8")))
required = {"scenario", "items", "median_ms", "tail_ms", "median_allocated_bytes"}
if not rows or set(rows[0]) != required:
    raise SystemExit(f"invalid benchmark schema in {path}")
if len(rows) < 18:
    raise SystemExit(f"expected at least 18 benchmark scenarios, found {len(rows)}")
for row in rows:
    if float(row["median_ms"]) < 0 or float(row["tail_ms"]) < float(row["median_ms"]):
        raise SystemExit(f"invalid latency result: {row}")
    if int(row["items"]) <= 0 or int(row["median_allocated_bytes"]) < 0:
        raise SystemExit(f"invalid size/allocation result: {row}")
print(f"Validated {len(rows)} benchmark scenarios in {path}")
PY

cat >"$output_dir/environment.txt" <<EOF
commit=$(git -C "$repo_root" rev-parse HEAD)
timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
os=$(uname -srmo)
java=$({ java -version; } 2>&1 | head -n 1)
processors=$(getconf _NPROCESSORS_ONLN)
EOF
