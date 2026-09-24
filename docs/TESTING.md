# Testing WizeFiles

WizeFiles uses three complementary test layers. A change should run the narrowest relevant tests locally, while pull-request CI provides the full JVM and device checks.

## Reproducible environment and clean verification

JDK 21, Go 1.26.5, Android SDK 36, Build Tools 36.0.0, NDK 29.0.14206865,
and CMake 3.22.1 are the pinned build inputs. On a Debian/Ubuntu host, bootstrap
those inputs and write `local.properties` with:

```bash
scripts/setup_env.sh
source .codex/env.sh
```

Then exercise the same clean debug build and JVM-test gate used by CI:

```bash
scripts/verify-clean-build.sh
```

The Gradle wrapper distribution and native source archives are checksum-verified.
The first clean build still requires access to Google Maven, Maven Central,
JitPack, the Go module proxy, and the pinned native-source download locations.

## JVM tests

Run the complete debug unit-test suite:

```bash
./gradlew :app:testDebugUnitTest --warning-mode all
```

This suite includes pure Kotlin tests, Robolectric UI tests, and source-contract regression tests. Source-contract tests protect architectural boundaries, but they do not replace runtime UI coverage.

### Coverage partitions

Coverage is reported separately so Android-shadow execution does not hide gaps in
the faster domain suite:

```bash
./gradlew :app:jacocoPureDebugReport
./gradlew :app:jacocoRobolectricDebugReport
```

The build classifies tests importing Robolectric into the Robolectric partition;
the pure partition excludes those classes. XML and HTML reports are written
under `app/build/reports/jacoco`. Instrumented coverage is enabled on the debug
build and uploaded independently from each emulator lane. Do not add aggregate
percentages across these execution models because their class and platform scopes
differ.

## Instrumented tests

Start an API 30 or newer emulator/device, then run:

```bash
./gradlew :app:connectedDebugAndroidTest --warning-mode all
```

The `Android UI Tests` workflow runs the complete instrumented test suite on an API 30 Google APIs emulator whenever a pull request changes app code, instrumented tests, or relevant build configuration. Failure reports are uploaded as workflow artifacts. This includes browser behavior, settings backup and restore, public intent routing, and cross-package provider grants.

The same workflow runs a browser-focused smoke package on API 36. API 30 remains
the minimum-platform compatibility lane; API 36 catches current permission,
foreground-service, URI grant, and framework behavior changes.

API 36 also runs dedicated `security` and `provider` packages so exported entry points,
FileProvider grants, hostile cross-package document metadata, and revocation behavior are not
covered only on the minimum platform.

Browser instrumentation includes:

- Search Back hides editing without discarding the active query.
- Dual-pane workspace views bind, normalize their active pane, and release cleanly.

Browser JVM coverage also enforces the process-death allowlist: only provider-neutral search UI
state survives, while paths, credentials, URI grants, and selections are actively removed before
the recreated view model observes its `SavedStateHandle`.

## Build and lint

Before merging a behavior or architecture change, also run:

```bash
./gradlew :app:assembleDebug :app:lintVitalRelease --warning-mode all
```

## Manual smoke tests

Device-dependent storage behavior still needs a focused manual check when touched. At minimum, verify the affected flow on the minimum supported API (30) and one current Android release. For file-browser work, cover local storage navigation, search/back behavior, selection, rotation or recreation, and dual-pane behavior when available.

For provider, vault, or sync changes, also exercise interrupted operations and unavailable or read-only storage. Automated tests should cover the state transitions; the smoke test confirms platform and provider integration.

## Scheduled hardening

`Scheduled hardening` replays the bounded host corpus, validates that all production debt markers
remain classified, and uploads schema-checked performance evidence each Tuesday. Pull requests that
touch those inputs run the same lane. Cross-package hostile document-provider tests remain in the
API 30 instrumentation suite so cursor extras, oversized metadata, and grant revocation exercise
real Binder/content-resolver behavior.

The same host lane runs bounded Jazzer coverage-guided targets for vault framing and archive path
normalization. Reproduce them locally with `./gradlew -PcoreOnly :core-files-api:fuzzVaultFrame
:core-files-api:fuzzArchivePath -PfuzzSeconds=60`.
