# Existing APKS re-signing

WizeFiles can re-sign an existing `.apks` archive. It does not generate an APKS from an AAB.

## Safety contract

- The source archive is never overwritten.
- Bundletool archives must have a bounded, valid `toc.pb` whose APK inventory exactly matches the ZIP.
- Every internal APK is re-signed with one key and certificate using selected embedded v1, v2, and/or v3 schemes.
- Detached v4 signatures are intentionally unavailable because an APKS has no standard sidecar mapping.
- All signed APKs must share package name, version code, and signer certificate, with exactly one base APK.
- Bundletool `toc.pb` bytes and entry paths are preserved. WizeFiles `metadata.json` checksums are rebuilt.
- The reconstructed archive is inspected and every split is verified before it is published.
- Passwords remain in memory only. A recovered operation returns to Transfer Center and asks for them again.

## Supported inputs

- Official bundletool APKS archives containing `toc.pb`.
- WizeFiles APKS archives containing `metadata.json` and `base.apk`.

Ambiguous ZIP layouts, unlisted APKs, unsafe paths, inconsistent packages, mixed versions, mixed certificates, and incomplete split sets are rejected.
