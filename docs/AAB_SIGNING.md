# AAB upload-key signing

WizeFiles signs and verifies Android App Bundles with the JAR/CMS upload-signature model used by
Android publishing tools. AAB signing is deliberately separate from APK Signature Schemes v1-v4:
Google Play or another distributor generates the installable APKs and applies their APK signatures.

## Supported credentials

- PKCS#12, JKS, and BKS key stores with an explicit or automatically selected private-key alias.
- Unencrypted or password-encrypted PKCS#8 private keys paired with an X.509 certificate or chain.

For raw material, WizeFiles performs a sign/verify challenge before touching the bundle so a private
key that does not match the selected leaf certificate is rejected. Passwords are copied only for the
active attempt, zeroed after use, excluded from saved state and autofill, and never written to the
operation store. Interrupted jobs move to `WAITING_FOR_USER` until the password is entered again.

## Signing and verification

Before signing, the common package-container inspector validates the complete AAB ZIP inventory and
requires `BundleConfig.pb` plus `base/manifest/AndroidManifest.xml`. Existing JAR signature metadata
is removed only from the new output. Every remaining non-directory entry receives a SHA-256 digest
in `META-INF/MANIFEST.MF`; the manifest digest is stored in `META-INF/WIZEFILE.SF`; and a detached CMS
signer block is generated from the chosen upload key.

WizeFiles then verifies the output before provider commit. Verification checks every entry digest,
the signature-file manifest digest, the CMS signature and certificate, and the platform JAR verifier.
The report includes signed-entry count, certificate SHA-256 fingerprints, subjects, expiry, errors,
and warnings. The original AAB is never overwritten, and provider outputs use the same temporary-file
plus atomic-move path as APK signing.

## Deliberate boundary

This checkpoint does not generate APKS from an AAB on-device. Official bundletool generation can
invoke host `aapt2`; WizeFiles therefore keeps AAB signing independent and handles existing APKS in
the next checkpoint without embedding an unaudited native build toolchain.
