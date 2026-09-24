# Advanced Format Foundation

This foundation prepares WizeFiles for focused follow-up support for extended archives and disk
images, EPUB/MOBI, saved web documents, advanced image formats, and LibVLC fallback playback. The
focused follow-up PRs now activate these backends while preserving this detection and safety boundary.

## Detection boundary

- `FileFormatDetector` reads at most 64 KiB from only the file the user selected.
- Detection order is bounded signature, normalized compound/split extension, provider MIME hint,
  then `UNKNOWN`.
- Generic `application/octet-stream` is never treated as proof of a format.
- Multi-volume aliases include `.7z.001`, `.zip.001`, `.partN.rar`, and traditional `.rNN` names.
- The registry declares the planned image, media, e-book, web-document, archive, filesystem, and
  disk-image families without routing them into unfinished backends.
- Focused viewers may activate a registered extension after their backend lands; LibVLC media
  still starts with Media3 and falls back only for parser/decoder capability errors.

The detector is intentionally not run across sibling queues or complete directories. Future viewer
PRs may probe the selected item after a user opens it; sibling filtering must remain extension/MIME
based until the user navigates to a sibling.

## Isolated parser service

`FormatSandboxService` is non-exported and uses `android:isolatedProcess="true"`. The process has no
WizeFiles storage identity or network permission. Its AIDL accepts only:

- Bounded primitive limits and an operation identifier.
- A caller-opened read-only input `ParcelFileDescriptor`.
- A caller-created output `ParcelFileDescriptor`.
- A Binder callback for completion, sanitized failure, or cancellation.

No path, URI, provider credential, Vault key, archive password, or publication password crosses the
boundary. The initial bounded-copy operation validates the lifecycle and is not a user-facing
decoder. Focused PRs will add pinned native operations behind the same descriptor contract.

The client observes Binder death, reports disconnection, and reconnects with bounded backoff. A
parser crash therefore ends only the isolated process and active sandbox request, not the browser.

## Source access and staging

`FormatSourcePlanner` chooses among:

1. `DIRECT_DESCRIPTOR` for a seekable single-file source.
2. `STAGE_SINGLE_FILE` for a non-seekable source.
3. `STAGE_RESOURCE_BUNDLE` when a reader requires controlled sibling resources.

Staging is capped at 4 GiB, reserves 10 percent working space within a 64–512 MiB safety band, and
uses a 256 MiB reservation when a provider cannot report size. A rejected staging plan must show a
controlled error and retain **Open with another app**.

`FormatStagingStore` creates one cache session per viewer, rejects absolute/traversal paths, enforces
byte limits while copying, supports cancellation and progress, syncs output, renames partial output
only after success, and removes the session when closed. Future UI integrations must enqueue
provider staging through `FileOperationService`/Transfer Center rather than copying on the main
thread. Archive entries continue to use the existing guarded single-entry extraction job.

## Native provenance and 16 KiB devices

`app/src/main/cpp/native-dependencies.json` records the version, source, hash, license, license file,
and linked targets for native components. Each follow-up native dependency must update that file and
the in-app open-source inventory in the same PR.

WizeFiles pins NDK `29.0.14206865`. CI runs `scripts/verify-native-page-size.sh` against the debug APK,
rejects any packaged `arm64-v8a` or `x86_64` ELF whose `PT_LOAD` alignment is below 16 KiB, and runs
`zipalign -P 16` over the APK. Android's 16 KiB device and Google Play requirements apply to 64-bit
devices; 32-bit libraries remain inventoried but are not incorrectly evaluated as 64-bit binaries.
This foundation adds no native dependency, ABI, or APK-size increase beyond small Kotlin/AIDL code.

Per-ABI release packaging is deferred until a native workstream materially increases download size.
The stop conditions remain an arm64 APK increase above about 45 MiB or a universal APK increase
above about 140 MiB.

## Validation and activation rule

Automated gates are:

```bash
./gradlew assembleDebug lintVitalRelease --warning-mode all
./gradlew :app:testDebugUnitTest --warning-mode all
scripts/verify-native-page-size.sh app/build/outputs/apk/debug/app-debug.apk
```

No planned destination may become active from this foundation PR. Each later focused PR must add
its own viewer/backend, format corpus, provider and archive regression coverage, physical-device
matrix, license updates, and APK-size comparison before changing the router.

