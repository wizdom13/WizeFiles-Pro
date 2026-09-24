# WizeFiles

WizeFiles is an Android file manager focused on practical day-to-day storage management: browsing local and remote filesystems, viewing images, video, audio, PDFs, e-books, documents, and fonts, installing verified Android packages, opening files through Android apps, performing background file operations, and managing app behavior through extensive settings. The project includes archive support, security features such as recoverable deletion and secure shredding, media metadata, and native integrations used for low-level file operations.

## Overview

WizeFiles targets Android (single `:app` module) and is implemented primarily in Kotlin with AndroidX and selected JNI/C components. The app combines:

- File browsing and navigation across multiple storage providers.
- Focused built-in image/video preview, service-backed audio playback, read-only PDF and e-book/document reading, TTF/OTF/TTC font preview, Android Open/Edit integration, media thumbnails, and file metadata.
- Background file jobs (copy/move/delete/archive/extract style operations) with user-facing handling.
- Configurable settings for appearance, behavior, security, and backup/restore.

At a high level, code is organized around feature packages, provider/storage abstractions, and shared core utilities.

## Features

### File Browser / File Manager

- Main launcher activity opens the file browser and supports directory-view intents.
- Navigation supports local and configured storage roots.
- Includes open-file, edit-file, and “open as” flows.
- Supports shortcuts/entry points (including downloads and launcher shortcuts).

### Storage Providers and Mounts

Configured storage flows include activities for:

- Device storage and external storage shortcuts.
- SAF document trees and SAF provider additions.
- FTP server configuration.
- SFTP server configuration.
- SMB server configuration.
- Cloud-app mediated storage entries (via dispatch/config activities).
- Direct cloud accounts through embedded rclone, with a generated provider wizard,
  simple WebDAV/S3 setup, configuration import, and an opt-in power-user mode.

### Media and PDF Files

- Opens images and videos in a focused internal preview across local, SAF, network, and cloud paths.
- Supports tiled large-image zoom, EXIF orientation, GIF/SVG/WebP playback, rotation, and sibling swiping.
- Uses Media3 first for video play/pause, seeking, buffering, fullscreen, rotation, and playback speed, with a pinned LibVLC fallback for uncommon containers and codecs.
- Plays folder audio through a dedicated MediaSession service with background, notification, lock-screen, headset, Bluetooth, metadata, artwork, seek, and queue controls; specialist formats retry through the same LibVLC fallback without replacing the session.
- Opens PDFs in a focused read-only reader with progressive pages, zoom, search, selection, password prompts, safe hyperlinks, fullscreen, and adaptive two-page tablet layouts.
- Previews TTF, OTF, and TTC fonts from local or provider-backed storage with bounded private staging, metadata, editable specimen text, multilingual samples, and a 12–84 sp size control.
- Preserves **Open with another app** and Android’s external edit integration.
- Generates image, audio, and video thumbnails and shows media metadata in File Properties.
- Keeps music libraries, playlists, PDF editing/annotation, and image/video editing out of scope.

### Text Viewing/Editing and Save-As

- `TextEditorActivity` supports a broad set of text and script MIME types plus `text/*`.
- `SaveAsActivity` supports exported `SEND` intents for saving shared content.

### File Operations and Background Jobs

- Foreground `FileOperationService` (data sync type) for file operations.
- Dedicated conflict and error dialog host activities.
- File operation receiver present.
- Archive password dialog activity indicates protected archive flows.

### Search, Sorting, Filtering, and View Behavior

- Source layout includes browser-oriented tests and components for sort persistence and sort dialog behavior.
- File-list UI behavior is configurable via settings (for example filename ellipsize and list animation).

### Settings / Preferences

`SettingsActivity` and related fragments/preferences include:

- Locale and night mode configuration.
- Black night mode toggle.
- File list animation and file-name ellipsize settings.
- Default directory, storages, standard directories, and bookmark directory management.
- Trash Bin toggle.
- Root strategy setting.
- Archive filename encoding setting.
- APK open-default action preference.
- Security settings (app password, protect browser, biometric toggle, relock interval).
- Settings backup and restore entry points.

### Themes / Appearance and UI Polish

- App themes include normal, translucent, and immersive variants.
- Leanback launcher category support and TV-related resources are present.
- UI animation preference and list-item animation resources are included.

### Notifications

- Foreground file-operation service for long-running transfer/job operations.

### Additional Implemented Feature Areas

- **Vault feature**: Dedicated vault activities/managers/dialogs for vault lifecycle and sessions.
- **Storage Cleaner**: Dedicated `StorageCleanerActivity` and related analyzer/test coverage in source tree.
- **Font viewer**: Internal TTF, OTF, and TTC preview with bounded private staging, font metadata, editable specimen text, multilingual samples, and discrete preview sizing.
- **App Manager / APK backup**: Current-profile installed-app inventory, user-confirmed uninstall, secure APK sharing, and durable `.apk`/`.apks` backup through Transfer Center. APK backups never include application data.
- **Android package installer**: Verified APK, APKS, APKM, and XAPK review and installation with compatible split selection, version/signing comparison, Android confirmation, root-gated downgrades and an explicit Core Patch–compatible signature-mismatch path, and validated XAPK OBB placement with partial-completion recovery. AAB installation remains unsupported until a bundletool build pipeline exists.
- **Secure shredder**: Irreversibly overwrites writable local file contents with one zero-filled pass, syncs the write, and then deletes the file. Directory selections apply the same treatment to contained local files. Remote, SAF, archive, and other provider paths are excluded.
- **File details/properties**: Dedicated feature package includes checksum/media/apk/permissions tabs.
- **Native/JNI support**: C/C++ sources for fast operations, syscall/SELinux bridge, hidden API bridge, and archive JNI components.

## Screens / Main Flows

### 1) Browsing files and folders
1. Launch app into file browser.
2. Navigate storage roots/folders.
3. Open file, open as, or trigger contextual file actions.

### 2) Opening files
1. Open an image or video in WizeFiles’ focused internal preview.
2. Swipe/zoom or play the file, inspect Properties, Share it, or choose **Open with another app**.
3. Other file types keep the existing Android-compatible app handoff.

### 3) Managing files
1. Select files/folders from browser.
2. Run operation (copy/move/delete/archive, etc.).
3. Monitor through foreground file operation service and conflict/error handlers.

### 4) Managing installed apps
1. Open App Manager from the navigation drawer.
2. Search, filter, sort, and select packages visible to the current Android profile.
3. Open, inspect, uninstall with Android confirmation, share APK backups, or queue durable backups through Transfer Center.

### 5) Reviewing and installing Android packages
1. Open an APK, APKS, APKM, or XAPK and choose **Install**.
2. Review the selected splits, version change, signing compatibility, permissions, features, components, and any OBB expansion files.
3. Use Android confirmation for ordinary installation, or choose the root installer for an explicitly approved downgrade. Rooted devices with Core Patch or a compatible system modification can separately opt into replacing a differently signed installation after a second risk warning.
4. If XAPK OBB placement is blocked after APK success, keep the installed app or retry only the expansion files.

### 6) Previewing fonts
1. Open a TTF, OTF, or TTC file from local, removable, network, cloud, or SAF-backed storage.
2. Review its format and available name metadata.
3. Enter specimen text and adjust the preview from 12 to 84 sp.

### 7) Securely shredding local files
1. Select writable local files or folders and choose delete.
2. Select **Securely shred (Irreversible)** and confirm.
3. WizeFiles overwrites each supported local file before permanent deletion; the option is unavailable for provider-backed paths.

### 8) Searching and sorting
1. Use browser listing/search/sort UI behaviors (implemented in browser feature area).
2. Persist sort/view behavior through app preferences.

### 9) Changing settings
1. Open settings from app.
2. Adjust interface, behavior, security, and backup/restore preferences.

## Permissions

From `AndroidManifest.xml`, the app requests (not exhaustive rationale shown below):

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` for remote storage/providers and network-aware behavior.
- `MANAGE_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` (maxSdk 32) for broad file-management behavior on supported Android versions.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, and `WAKE_LOCK` for long-running file jobs.
- `POST_NOTIFICATIONS` for user-visible notifications.
- `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, and `QUERY_ALL_PACKAGES` for verified package installation, installed-app inventory, package details, and Android-confirmed uninstall.
- `PACKAGE_USAGE_STATS` appears for usage/statistics related features.
- Shortcut install permission for launcher shortcut support.

## Build Requirements

Based on Gradle and workflow configuration:

- Android Gradle Plugin: `9.1.0`
- Gradle wrapper present in repo (`./gradlew`)
- Kotlin plugin: `2.2.0`
- JDK: **21** (toolchain and CI setup)
- Compile SDK: **36**
- Build Tools: **36.0.0**
- Min SDK: **30**
- Target SDK: **36**
- NDK: **29.0.14206865**
- CMake: required (`externalNativeBuild` + `app/CMakeLists.txt`)

## Build and Run

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintVitalRelease
```

CI also runs build, lint, JVM tests, and API 30 instrumented tests (see workflow section).

## Testing

Available test types in the repository:

- JVM unit tests under `app/src/test/**` (includes Robolectric tests and extensive source/regression tests).
- Instrumented tests under `app/src/androidTest/**`.
- Browser-critical instrumented tests run on an API 30 emulator for relevant pull requests.

Notes:

- CI explicitly caches Robolectric artifacts, which indicates test stability/performance concerns around dependency fetches are considered.
- See [`docs/TESTING.md`](docs/TESTING.md) for the local commands, CI split, and device smoke-test expectations.
- See [`docs/ANDROID_PACKAGE_INSTALLER.md`](docs/ANDROID_PACKAGE_INSTALLER.md) for supported package formats, split/signature validation, downgrade gates, and XAPK OBB recovery.
- See [`docs/FONT_VIEWER.md`](docs/FONT_VIEWER.md) for supported fonts, preview behavior, and staging limits.
- See [`docs/SECURE_SHREDDER.md`](docs/SECURE_SHREDDER.md) for eligibility, overwrite behavior, and storage limitations.
- See [`docs/SECURITY_SURFACE.md`](docs/SECURITY_SURFACE.md) for exported components and privileged permissions.
- See [`docs/TODO_RISK_REGISTER.md`](docs/TODO_RISK_REGISTER.md) for prioritized correctness and destructive-operation debt.
- See [`docs/DEPENDENCIES.md`](docs/DEPENDENCIES.md) for dependency ownership, locking, and update policy.
- See [`docs/ARCHITECTURE_ROADMAP.md`](docs/ARCHITECTURE_ROADMAP.md) for staged modularization, fault-injection, fuzzing, and performance exit criteria.
- This README does **not** claim complete feature coverage by tests; coverage is broad but should be validated per change.

## Project Structure

Practical layout:

- `app/src/main/java/com/wisso/wizefiles/core` - app/core initialization, theme/security integrations, shared infrastructure.
- `app/src/main/java/com/wisso/wizefiles/feature` - user-facing features (browser, App Manager, settings, file jobs, vault, storage cleaner, and text viewing/editing).
- `app/src/main/java/com/wisso/wizefiles/data/providers` - filesystem/provider implementations (local/remote abstractions).
- `app/src/main/java/com/wisso/wizefiles/storage` - storage routing and provider-level storage wiring.
- `app/src/main/java/com/wisso/wizefiles/provider` - additional provider integrations.
- `app/src/main/jni` and `app/src/main/cpp` - native code.
- `app/src/main/res` - layouts, drawables, themes, preferences, widgets, localization resources.
- `.github/workflows/android.yml` - CI pipeline.
- `.github/workflows/android-ui-tests.yml` - API 30 emulator instrumentation pipeline.
- `scripts/` - setup/helper scripts.

## Architecture / Technical Notes

- Language stack: Kotlin + AndroidX, with JNI/C components.
- Uses `ViewBinding`, `LiveData`/lifecycle components, coroutine dependencies, and a Media3-first playback surface with a guarded LibVLC fallback.
- Multi-provider storage model includes local and remote providers
  (FTP/SFTP/SMB/rclone) and SAF-based sources.
- Archive handling integrates native/archive components and archive-related operations.
- Security-related areas include biometric preference toggles, app security settings, and vault-focused feature package.

## CI / GitHub Actions

`.github/workflows/android.yml`:

- Triggers on pushes to `main`, pull requests, and manual dispatch.
- Uses Ubuntu runner with JDK 21.
- Sets up Gradle caching.
- Caches Robolectric Maven artifacts.
- Runs:
  - `./gradlew assembleDebug lintVitalRelease --warning-mode all`
  - `./gradlew :app:testDebugUnitTest --warning-mode all`
- Uploads debug APK artifact (`app-debug.apk`).

`.github/workflows/android-ui-tests.yml`:

- Runs for pull requests that change application or instrumented-test code.
- Boots an API 30 Google APIs emulator with KVM acceleration.
- Runs the file-browser instrumented package through `:app:connectedDebugAndroidTest`.
- Uploads instrumented-test reports when a test fails.

## Contributing

1. Fork and create a feature branch.
2. Build locally before opening a PR:
   - `./gradlew :app:assembleDebug`
3. Run tests before PR:
   - `./gradlew :app:testDebugUnitTest`
4. Run lint checks used in CI when possible:
   - `./gradlew :app:lintVitalRelease`
5. Keep changes scoped and include tests for behavioral updates when possible.

## License

This repository includes a `LICENSE` file at the project root. See `LICENSE` for the exact terms.

## Status / Notes

- This README reflects a repository scan of the current codebase and configuration.
- Feature descriptions are limited to behavior evidenced in source, manifest, resources, tests, and build/CI files.
- Some advanced areas (for example certain provider edge cases) are implementation-heavy; validate specific workflows on-device when changing storage or media behavior.
