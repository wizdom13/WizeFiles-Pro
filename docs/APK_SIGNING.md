# APK Sign & Verify

## Foundation status

The `apk-signing-foundation` checkpoint is intentionally **not user-facing**. It adds the signing
and verification boundary, scheme validation, pinned dependency, golden corpus, and release-build
gates. Provider-aware staging, key loading and generation, Transfer Center integration, and the
signing UI belong to the two sequential follow-up checkpoints.

## Backend boundary

`ApkSigningBackend` accepts only private app-local `File` inputs and outputs. The workflow layer must
stage SAF, root, network, cloud, and archive-provider sources before calling it. The backend:

- never overwrites the input APK;
- refuses pre-existing outputs;
- removes incomplete APK and idsig outputs after a signing or verification failure;
- replaces prior APK signatures rather than preserving unknown signers;
- verifies every requested scheme and the selected certificate before returning success;
- keeps private-key material behind a redacted model and never accepts passwords.

v1, v2, and v3 signatures are embedded in the APK. v4 is a detached
`<signed-apk-name>.apk.idsig` file and is accepted only with v2 or v3. The v4 APK bytes are otherwise
identical to the equivalent v2/v3-signed APK.

## Dependency provenance

The implementation uses the Apache-2.0 Android port of AOSP apksig:

| Field | Value |
|---|---|
| Coordinate | `com.github.MuntashirAkon:apksig-android:4.4.0` |
| Upstream tag | `4.4.0` (`1bd3a0c000c56e752b41d6f65c3a3d3d8ad7f049`) |
| AAR SHA-256 | `948321f77e13368aa0c5f4defe73971578a5f18fdf1b49c11756ef6a15c95586` |
| AAR size | 431,752 bytes |
| Native code | None |
| License | Apache License 2.0 |

`scripts/verify-apksig-dependency.sh` checks the resolved Gradle artifact byte-for-byte and rejects
an unexpected native payload. The dependency remains behind WizeFiles-owned interfaces because the
port tracks an older AOSP apksig snapshot and can be replaced without changing provider jobs or UI.

## Golden corpus

`app/src/test/resources/apk-signing/` contains one bounded unsigned APK-like ZIP plus signed v1, v2,
v2+v3, and v2+v3+v4 fixtures. The signing certificate is test-only; its private key was not retained.
The fixture APKs contain no executable application code. Unit tests check scheme detection,
certificate consistency, detached-v4 behavior, tamper rejection, output safety, and end-to-end
signing with an ephemeral test key.

Android Build Tools 36.0.0 independently verifies every golden fixture through
`scripts/verify-apk-signing-fixtures.sh`. This cross-check prevents WizeFiles from trusting only the
same library that created the signatures.

## Validation gates

```bash
./gradlew assembleDebug assembleRelease lintVitalRelease --warning-mode all
scripts/verify-apksig-dependency.sh
scripts/verify-apk-signing-fixtures.sh
scripts/verify-native-page-size.sh app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest --warning-mode all
```

`assembleRelease` is mandatory here because R8 can otherwise expose release-only failures. No broad
apksig keep or warning-suppression rule is allowed. CI reports the minified unsigned release APK size
so the dependency cost is visible before the workflow and UI checkpoints proceed.

## Workflow checkpoint

The `apk-signing-workflow` checkpoint stages APKs and key stores from WizeFiles provider paths into a
private cache directory, calls the file-only backend, then commits verified outputs through the
destination provider. Local, SAF, root, archive, SMB, SFTP, and rclone-backed paths therefore share
one signing boundary without passing provider credentials to apksig.

Signing is represented by `TransferOperationType.APK_SIGN` and uses the existing two-operation
long-running limiter. The durable operation store contains only provider URIs, alias, signature
schemes, minimum SDK, and conflict policy. Key-store and key passwords are copied into a one-use
in-memory registry, redacted from diagnostics, removed before execution, and zeroed afterward. They
are never written to preferences, databases, saved state, logs, or the operation JSON.

If the process terminates, interrupted signing becomes `WAITING_FOR_USER` with
`SIGNING_SECRET_REQUIRED`; it never retries with a missing or remembered password. Re-entering the
password explicitly resumes the existing Transfer Center operation. Output conflicts default to
keep-both naming and never overwrite the input, a prior output, or an existing detached idsig.

`ApkSigningKeyStoreService` imports PKCS#12, JKS, and BKS private-key entries and can generate a new
password-protected PKCS#12 RSA key. `ProviderApkVerifier` provides the same private staging boundary
for read-only verification. The following UI checkpoint owns all screens, pickers, and user-facing
validation; this checkpoint intentionally exposes services and durable jobs only.

## UI checkpoint

The `apk-signing-ui` checkpoint exposes Sign APK and Verify APK only for a single selected APK
outside the recycle bin. The shared screen uses WizeFiles provider pickers for APK, output, key-store,
and optional detached v4 files. It can inspect PKCS#12, JKS, and BKS aliases and generate a new
password-protected PKCS#12 RSA key without bypassing provider streams.

Signing defaults to v1, v2, and v3 with deterministic keep-both output behavior; v4 remains opt-in
and cannot be selected without v2 or v3. Verification is read-only and reports the verified schemes,
signer-certificate SHA-256 fingerprints, errors, and warnings. Both provider staging operations run
off the main thread.

Password fields disable saved state and autofill. Passwords are copied to character arrays, cleared
after ownership transfer, and never persisted. Transfer Center opens the same screen in a restricted
password-reentry mode for paused, interrupted, recoverable, or failed APK signing operations.
