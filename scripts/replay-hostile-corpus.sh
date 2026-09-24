#!/usr/bin/env bash
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# Fixed corpora are always replayed by the core and app JVM suites. Native seeds are additionally
# consumed by connected instrumented tests, where the Android native libraries are available.
timeout 10m ./gradlew -PcoreOnly :core-files-api:test --tests '*CheckedInHostileCorpusTest'
timeout 20m ./gradlew :app:testPureDebugUnitTest --tests '*CheckedInAppHostileCorpusTest'
