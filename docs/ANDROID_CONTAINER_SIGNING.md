# Android package-container signing foundation

This checkpoint adds the non-UI safety boundary shared by AAB, APKS, XAPK, and APKM workflows.

## Accepted container dialects

- Android App Bundles with `BundleConfig.pb` and `base/manifest/AndroidManifest.xml`.
- Official bundletool APK Sets with `toc.pb`.
- WizeFiles split backups with checksum-authenticated `metadata.json`.
- XAPK archives with a recognized `manifest.json` and referenced APK/expansion entries.
- Current plain-ZIP APKM archives with `base.apk`; optional `info.json` is treated as metadata only.

The inspector does not decrypt legacy encrypted APKM files and does not manufacture APKMirror
metadata. APKM export remains a conversion to an open APKS representation.

## Security boundary

Before any APK is extracted or signed, the complete central directory and expanded byte stream are
validated. The boundary rejects absolute paths, traversal, backslash aliases, Unicode/case path
collisions, duplicate entries, unknown sizes, oversized metadata, excessive entries, expansion
overflow, and unsafe compression ratios. Every file is drained and SHA-256 hashed so encrypted or
truncated entries fail before a workflow starts.

Split-set verification stages APKs only inside a caller-provided private directory and deletes that
directory on every exit. It requires every APK to pass the existing apksig verifier, use the same
package name and version, use the exact same signer-certificate list, and contain exactly one base
APK. WizeFiles backup checksums and available container package/version hints are also enforced.

Provider staging, durable Transfer Center operations, credentials, output reconstruction, and UI are
owned by the later format-specific checkpoints.
