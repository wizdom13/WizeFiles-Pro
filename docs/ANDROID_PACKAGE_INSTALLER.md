# Android package installer

WizeFiles owns the validation and staging flow for APK, APKS, APKM, and XAPK packages. The
implementation is independent and reuses WizeFiles' existing container inspector, apksig verifier,
and bounded provider materialization. App Manager was used only as a behavioral reference; no GPL
source is copied or adapted.

## Supported inputs

- APK: the APK is signature-verified and installed as a single base package.
- APKS: a single installable bundletool/WizeFiles split set is inspected, every APK is verified,
  and incompatible ABI, density, and locale configuration APKs are excluded before one Android
  install session is committed. Multi-variant bundletool archives are rejected until targeting from
  `toc.pb` is selected before split-set verification.
- APKM: the original archive is installed directly after the same split-set verification. Importing
  it as a WizeFiles APKS remains a separate optional operation.
- XAPK: the APK set is installed through the same session workflow. Valid `main` and `patch` OBB
  files are installed after Android reports APK success.
- AAB: not installable. An AAB must first be converted by a real bundletool build pipeline.

## Security boundaries

- The existing archive entry, expanded-size, compression-ratio, path, count, and metadata limits
  remain authoritative.
- Every APK must have the same package, version, and signing identity, with exactly one base APK.
- Selected APK and OBB hashes are checked again after extraction and immediately before use.
- Signing mismatch blocks an update or downgrade by default. **Allow version downgrade** controls only Android's version-code gate. On rooted devices, a separate expert-only signature-mismatch option can pass the verified package set to a compatible Core Patch-style system modification; WizeFiles never automatically re-signs a package.
- XAPK OBB names must be `main.<version>.<package>.obb` or
  `patch.<version>.<package>.obb`; unrelated payloads are never written to shared storage.
- External sources are copied into private no-backup staging before inspection. Partial state is
  persisted without storing credentials or signing material.

## Android confirmation and recovery

The public `PackageInstaller.Session` backend writes every selected APK, fsyncs each stream, and
commits through a mutable, app-private pending intent. Installations using
`REQUEST_INSTALL_PACKAGES` normally receive `STATUS_PENDING_USER_ACTION`; WizeFiles launches the
system confirmation intent and records the session ID so the operation can be reconciled after
activity or process recreation.

APK commit and OBB placement are not one atomic Android transaction. If APK installation succeeds
but OBB placement fails, WizeFiles reports partial completion, retains only the required private
staging, and offers an OBB-only retry. Existing OBB files are backed up until all replacement files
have passed their final integrity checks. When the user selected the root installer, OBB placement
uses a separate root transaction with quoted paths, size and SHA-256 verification, backups, and
rollback; otherwise WizeFiles uses the ordinary storage path and reports OS denial honestly.

## Capability limits

- A root-only backend can create, write, commit, and abandon package-manager sessions. Its UI is
  shown only when root is available; it enables silent installation and an explicitly approved
  downgrade, and its command builder rejects line breaks and shell-quotes every staged path. The
  Downgrade action requires root installation and **Allow version downgrade**. If the installed and
  incoming signatures differ, the action additionally requires **Allow signature mismatch
  (root/Core Patch)** and a second risk confirmation. Selecting the mismatch option automatically
  selects the root backend. WizeFiles does not disable Android signature verification itself; Core
  Patch or a compatible system modification must already be active, so Android may still reject the
  session. Replacing a differently signed app can make existing app data unreadable, break later
  updates, or prevent startup.
- Sui is not presented as a package-install command bridge. Other-user selection is modeled by the
  root backend but is not exposed until WizeFiles can enumerate and label Android users reliably.
- Installer impersonation, package-verification bypass, APK re-signing, DEX optimization, and
  tracker blocking are not implemented.
- Requesting update ownership is shown only on an initial install when the platform and permission
  are available.
- Android 11 and newer block Storage Access Framework access to `Android/obb`. OBB placement is
  attempted only against the validated package directory and reports an actionable partial result
  when the OS or OEM denies access.
