# Publishing WizeFiles Beta

The `Publish WizeFiles Beta` workflow builds a release-optimized APK from the private
WizeFiles repository and publishes it as a prerelease in the public, binary-only
`wizdom13/WizeFiles-Beta` repository.

## One-time setup

Create a dedicated beta signing key. Do not reuse the stable production key. Keep an
offline backup because future beta builds must use the same key to update installed
beta versions.

Add these Actions secrets to the private `wizdom13/WizeFiles` repository:

| Secret                         | Purpose                                                             |
| ------------------------------ | ------------------------------------------------------------------- |
| `BETA_KEYSTORE_BASE64`         | Base64-encoded beta keystore bytes                                  |
| `BETA_STORE_PASSWORD`          | Beta keystore password                                              |
| `BETA_KEY_ALIAS`               | Alias of the beta signing key                                       |
| `BETA_KEY_PASSWORD`            | Beta key password                                                   |
| `WIZEFILES_BETA_RELEASE_TOKEN` | Fine-grained token used only to create releases in `WizeFiles-Beta` |

Configure `WIZEFILES_BETA_RELEASE_TOKEN` as a fine-grained personal access token with:

- Repository access limited to `wizdom13/WizeFiles-Beta`
- Repository permission `Contents: Read and write`
- An expiration date and a reminder to rotate it before expiry

The private repository's automatic `GITHUB_TOKEN` cannot publish to a different
repository, which is why this narrowly scoped token is required.

## Publish a beta

The Android `versionName` and `versionCode` are hardcoded in `app/build.gradle`.
Update both values in source and review that change before publishing. Workflows never
derive or override Android version metadata from a tag, run number, or manual input.

1. Open **Actions → Publish WizeFiles Beta → Run workflow** in the private repository.
2. Enter the release tag used for public naming, such as `v0.6.0`.
3. Enter public release notes, including what changed, what should be tested, and any
   known limitations.
4. Run the workflow.

For tag `v0.6.0`, the public APK is named `WizeFiles_v0.6.0_beta.apk`. The private
Actions artifact downloads as `WizeFiles_v0.6.0.zip` and contains that APK plus its
SHA-256 checksum. The tag controls only release and file naming; it does not change the
version embedded in the APK.

The workflow tests the app, runs release lint, builds and signs the beta APK, verifies
its signature, generates a SHA-256 checksum, retains a private Actions artifact for 30
days, and creates a prerelease in `WizeFiles-Beta`. It refuses to overwrite an existing
release tag.

## Beta identity

The beta build uses application ID `com.wisso.wizefiles.beta` and the visible name
`WizeFiles Beta`. It can be installed beside the stable app, and Android keeps the two
apps' data separate. Provider OAuth registrations that bind to an Android package and
certificate may also need the beta application ID and beta signing-certificate digest.

Never upload the keystore, signing passwords, release token, OAuth credentials, or
private source files to `WizeFiles-Beta`.
