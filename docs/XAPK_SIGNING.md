# XAPK signing

WizeFiles can re-sign every APK in an existing XAPK while preserving the XAPK manifest,
expansion files, icons, and other non-APK payloads.

## Safety contract

- The original XAPK is never overwritten.
- Multi-APK archives must list every APK exactly once in `manifest.json` `split_apks`.
- Every APK is signed with the same key and certificate using selected embedded v1-v3 schemes.
- Detached v4 signatures are unavailable because XAPK has no standard sidecar mapping.
- The signed set must contain one package and version, one certificate, and exactly one base APK.
- `manifest.json`, OBB expansion files, and every other non-APK payload are preserved byte-for-byte.
- The final XAPK is inspected and every internal APK is verified before publication.
- Passwords remain in memory only and must be re-entered after process recovery.
