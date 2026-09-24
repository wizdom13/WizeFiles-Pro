# APKM import to open APKS

WizeFiles can import an APKM archive into its documented APKS layout without changing or
re-signing any APK.

## Safety contract

- The APKM source is never overwritten.
- Every source APK signature must verify.
- All APKs must share one package, version, and signer certificate, with exactly one base APK.
- APK bytes are copied unchanged and compared by SHA-256 after reconstruction.
- A new `metadata.json` records the package, version, signer, source format, APK paths, and hashes.
- Existing APKM metadata and other safe non-APK payloads are preserved.
- The resulting WizeFiles APKS is inspected and every APK is verified again before publication.
- Imports run through Transfer Center and recover after process loss without storing credentials.
