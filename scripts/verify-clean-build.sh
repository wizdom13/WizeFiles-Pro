#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
chmod +x ./gradlew scripts/build-rclone-aar.sh

temporary_gradle_home=""
if [[ "${WIZEFILES_CLEAN_GRADLE_CACHE:-0}" == "1" ]]; then
    temporary_gradle_home="$(mktemp -d)"
    trap 'rm -rf "$temporary_gradle_home"' EXIT
    export GRADLE_USER_HOME="$temporary_gradle_home"
fi

./gradlew --no-daemon clean \
    :core-files-api:test \
    :feature-browser-domain:test \
    :feature-transfer-domain:test \
    :feature-vault-domain:test \
    :performance-baselines:test \
    :app:assembleDebug \
    :app:testPureDebugUnitTest \
    :app:testRobolectricDebugUnitTest \
    --warning-mode all
