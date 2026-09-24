# Dependency ownership and verification

Every dependency must have a responsible area, a product reason, and an update
policy. Dynamic versions and changing modules are rejected. Gradle dependency
locking is strict for every resolvable configuration. Each project checks in its
`gradle.lockfile`, and a network-enabled maintenance change refreshes all lock state
with the documented multi-project `dependencies --write-locks` command. Gradle
dependency verification is also strict through checked-in SHA-256 metadata under
`gradle/verification-metadata.xml`.

The Gradle wrapper and downloaded native source archives are SHA-256 verified, and
CI separately runs OWASP Dependency-Check plus APK signing-fixture verification.

| Area / owner | Principal dependencies | Reason and update policy |
| --- | --- | --- |
| Android platform | AndroidX activity, appcompat, core, fragment, lifecycle, preference, RecyclerView, WorkManager, Window | Update together after API 30 and current-API smoke lanes pass. Alpha artifacts require an explicit feature owner. |
| Storage providers | SMBJ, jCIFS-NG, SSHJ, Commons Net, rclone AAR, Guava | Provider owners validate authentication, cancellation, metadata, and destructive operations. Compatibility pins and exclusions must retain an explanatory comment. |
| Cryptography and signing | Bouncy Castle, jbcrypt, apksig-android | Security owner reviews release notes and test fixtures. No substitution or major upgrade without vault/signing vectors and malformed-input tests. |
| Media and documents | Media3, LibVLC, Readium, AndroidX PDF, Coil, AndroidSVG, subsampling image view | Viewer owners test fallback behavior, hostile documents, lifecycle, and memory pressure on minimum/current APIs. |
| Device integration | Google Nearby, code scanner, Shizuku, libsu | Feature owners review permission/export changes and test denial, disconnect, and unavailable-service paths. |
| Reliability and UI | ACRA, Material, drawer/layout helper libraries | Crash data remains local. UI dependencies should be removed when platform/AndroidX equivalents cover required behavior. |
| Build and test | AGP, Kotlin, Gradle, OWASP Dependency-Check, JUnit, Robolectric, AndroidX Test, JaCoCo | Build owner pins exact versions, updates wrapper checksums, and keeps pure, Robolectric, instrumented, and vulnerability lanes independently visible. |
| Native formats | libarchive, 7-Zip, libmobi | Native owner pins release URLs and hashes, disables unused features, fuzzes parsers, and verifies 16 KiB page alignment. |

## Change checklist

1. Use an exact version; never `+`, snapshots, or changing modules.
2. Record why exclusions or compatibility pins are necessary.
3. Refresh lock state and dependency-verification metadata in a trusted,
   network-enabled environment, run `scripts/verify-dependency-integrity.sh`,
   and review every artifact diff.
4. Run the owning feature's runtime tests plus both supported API lanes.
5. Update third-party notices and licenses when coordinates or bundled native
   sources change.
