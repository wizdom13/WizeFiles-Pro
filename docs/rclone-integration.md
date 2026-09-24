# rclone integration

WizeFiles embeds rclone through its `gomobile` library. It calls rclone's in-process
RPC entry point directly; it does not start the HTTP RC server and does not mount a
FUSE filesystem.

## Pinned toolchain

- rclone: `v1.74.4`
- Go: `1.26.5`
- `golang.org/x/mobile`: `v0.0.0-20260709172247-6129f5bee9d5`
- Android API: 30

The Android library can be reproduced with:

```sh
go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20260709172247-6129f5bee9d5
gomobile init
go mod init wizefiles-rclone-build
go get github.com/rclone/rclone@v1.74.4
go get golang.org/x/mobile@v0.0.0-20260709172247-6129f5bee9d5
cp -R /path/to/WizeFiles/rclone-mobile ./gomobile
export CGO_LDFLAGS='-Wl,-z,max-page-size=16384'
gomobile bind -androidapi 30 -target android/arm,android/arm64 \
  -ldflags='-linkmode=external -extldflags=-Wl,-z,max-page-size=16384' \
  -javapkg=org.rclone \
  -o rclone-gomobile.aar \
  ./gomobile
```

`./gomobile` is the small WizeFiles bridge in `rclone-mobile/`. It preserves the
official in-process RPC API and replaces rclone's desktop browser launcher with an
Android callback. No rclone source is modified.

The generated AAR targets `armeabi-v7a` and `arm64-v8a`. It is ignored by Git
and cached by CI rather than checked into the repository. Its native libraries
must have 16 KB-aligned ELF load segments; CI verifies every `PT_LOAD` entry has
an alignment of at least `0x4000`.

## User experience

The connection wizard starts in regular mode. Google Drive, OneDrive, Dropbox,
Box, and pCloud use branded browser sign-in flows. WizeFiles opens rclone's local
OAuth URL in the system browser; rclone's loopback server receives the provider
callback and stores the resulting token in the encrypted configuration. MEGA has
a minimal email-and-password form. WebDAV and S3-compatible storage keep their
focused credential forms. Existing `rclone.conf` files can also be imported.

Power user mode is off by default. When enabled, it exposes the backend type and
the complete option schema reported by rclone. This keeps technical fields out of
the normal path while allowing any supported rclone backend to be configured or
imported.

The provider list and its fields are generated at runtime from `config/providers`.
Regular mode omits rclone's advanced options and technical integration fields such
as custom client IDs, config paths, token locations, and service-account files.
Power user mode displays those fields. Exclusive example values are rendered as
choices, password values use masked inputs, and required fields are validated.

If `config/create` needs post-configuration input, WizeFiles follows rclone's
non-interactive continuation protocol. It renders the returned question, sends the
answer back with the returned state, and repeats until rclone reports an empty
state. Regular OAuth profiles automatically select the local-browser flow and open
the returned loopback URL. Provider-specific choices such as OneDrive account type
remain visible when rclone requires them. URLs in question help are clickable.

## Deletion behavior

Deleting an rclone path uses the backend's normal delete operation. WizeFiles never
downloads a cloud item into the phone's local recycle bin. The configured backend
and account policy remain authoritative: supported providers may move the item to
their native Trash, while backends without Trash may delete it immediately.

Because rclone cannot guarantee a per-item hard delete consistently across cloud
providers, WizeFiles does not offer **Permanently delete (Skip Trash)** or secure
shredding for rclone paths. Users manage provider Trash retention and permanent
removal in the provider's app or website.

## Credential handling

The storage list contains only a generated remote identifier, provider type,
display name, and optional root path. The rclone configuration is encrypted by the
existing Android Keystore-backed `SecretStore` and excluded from settings backups.
A plaintext config is materialized in `noBackupFilesDir` only while rclone is
executing, then captured back into the encrypted store and deleted.

Transfers use app-cache staging files. They are deleted when streams or channels
close and are never included in backups.
