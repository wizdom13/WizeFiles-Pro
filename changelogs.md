# WizeFiles changelog

The release history of WizeFiles, including its earlier beta builds and the public WizeFiles-Pro releases. Entries are newest first. Historical labels such as **0.70** are preserved; they are different from **0.7.0**. Versions 0.6.1, 0.6.2 and 0.7.0 are documented from their source version updates where a retained release page is unavailable.

Changes planned for future versions are listed under **Unreleased** when present. Early notes were reconstructed from the available tags, release notes and source history; gaps are identified rather than filled with guessed features.

## 1.2.0 — 2026-09-30
<!-- release-order: 15 -->

### Added
- Offline Changelog screen in the side menu, immediately above About.
- A first-launch release-notes dialog, with unseen changes shown after later updates and a shortcut to the full history.
- A complete Markdown changelog and a matching HTML copy bundled with the app.

### Changed
- Replaced Google Nearby Connections with Android local-network discovery and encrypted TLS transfers. Both devices need the updated app and the same Wi-Fi network or a phone hotspot; no internet or Google Play services are required for local transfers.
- Replaced the Google/ML Kit QR scanner with an offline Camera2 and ZXing scanner. Camera permission is requested when scanning, and obsolete Nearby Bluetooth/location permissions were removed.
- Extended Syncthing session controls and improved interruption recovery and session history.

### Fixed
- Restore open browser tabs, their locations and pane layouts after restarting the app.
- Keep the last file accessible above the selection action panel.
- Preserve receiver approval, QR verification, conflict choices and durable progress during Nearby transfer recovery.
- Handle rapid pause/resume, stale streams, skipped conflicts and unacknowledged temporary-file bytes without blocking resumed transfers.
- Confirm incoming files only after the stream has ended and the file has been saved successfully.

## 1.1.0 — 2026-09-28
<!-- release-order: 14 -->

### Added
- Native Syncthing integration for pairing devices and synchronizing folders.
- Two-way and send-only Syncthing folder modes, device and folder configuration, and exclusions.
- Manual and scheduled Syncthing sessions, initially limited to eight minutes, with progress, history, pause/cancel and interrupted-session recovery.

### Fixed
- PDF files that remained blank, failed to load or failed to render in the internal viewer.
- Stage remote and provider-backed PDFs before rendering, with reliable source routing and cleanup.
- Replace the stalled PDF rendering backend and correct its dependency verification metadata.

### Maintenance
- Finish removing obsolete beta expiry, purchasing, licensing and paid-feature infrastructure from the development repository.
- Clean up open-source dependency declarations, distribution metadata, screenshots and documentation.

## 1.0.0 — 2026-09-25
<!-- release-order: 13 -->

### Changed
- First public open-source WizeFiles-Pro release, licensed under GPL-3.0-only.
- Make all app features available without purchases, subscriptions, license keys or feature paywalls; remove beta branding and restrictions.
- Publish the complete file-management feature set: local/SAF storage, cloud accounts and network providers, tabs and dual panes, Transfer Center, scheduled sync, Vaults, cleanup tools, built-in viewers, App Manager and Android package installation.

### Added and improved
- Add an Open button after successful package installation or update.
- Open Android packages selected through another app's Open with flow.
- Add ringtone actions and external audio/video playback routing, with clearer handler names.
- Refresh Browser and Vault rows with clearer folder counts, sizes, dates and icon surfaces.
- Rework provider paths and byte channels, remote/root bridges, FTP/SFTP/SMB clients, Android compatibility, shared UI, utilities, settings and server foundations.

### Fixed
- A native byte-string bridge crash.
- Archive navigation and external archive display names.
- Installer completion actions and media Open with routing.
- API 30 language discovery, release dependency locks and stale verification contracts.

## 0.7.0 — 2026-08-30
<!-- release-order: 12 -->

Recorded source version and beta publication milestone.

### Added
- Verified installation of APK, APKS, APKM and XAPK packages, including split-package review and OBB handling.
- Installer version/signature comparison, downgrade review, supported rooted installation options and clearer confirmation controls.
- Internal font viewer with lifecycle-safe loading and user feedback.
- Provider-neutral browser, transfer and Vault domain modules, including shared planning, state and failure policies.

### Improved
- Reorganize the side menu into storage, transfer, tools and settings groups.
- Strengthen local, SAF, cloud and remote mutation capability checks, cancellation and recovery behavior.
- Preserve interrupted Nearby payload checkpoints and require fresh authentication when reconnecting.
- Improve duplicate-file keep recommendations and make destructive cleanup choices more conservative.
- Complete PC Access ZIP downloads and search results; harden request paths, connections and sharing migrations.
- Bound SMB discovery concurrency, document-provider loading waits, and package/backup parser resource use.
- Publish directory-property progress without blocking the main thread.
- Authenticate encrypted-file metadata and stage plaintext safely.

### Fixed
- Nearby authentication recovery, receive-path validation and stream-state inconsistencies.
- Vault relocking and state restoration across interrupted operations.
- Remote connection teardown, SMB resolution/session errors and destructive FTP-operation tracking.
- MOBI native loading, provider/parser edge cases, browser state and sort behavior.
- Translation placeholders and compiler warnings.

### Maintenance
- Split large browser, transfer, Vault, cleanup, settings, signing, provider, editor and JNI implementations into focused components.
- Add provider conformance, operation/state, cancellation and recovery coverage; replace brittle source-spelling checks with behavioral tests.
- Lock the Gradle dependency graph and verify downloaded dependency hashes.
- Expand verification lanes and update feature documentation, website and crash-report privacy disclosures.

## 0.6.7 — 2026-08-23
<!-- release-order: 11 -->

### Storage cleanup and App Manager
- Route cleanup deletion through Transfer Center and provider-supported deletion APIs.
- Start cleanup selections unchecked; keep system apps out of Unused Apps removal.
- Improve junk classification, duplicate grouping and keep-file recommendations.
- Add ignore controls, explicit file-to-keep selection and clearer cleanup details.
- Rename Recycle Bin to Trash Bin, including its browser presentation.
- Improve disabled-action contrast, access guidance and hotspot presentation.
- Show icons for APK, APKS, APKM, XAPK and AAB containers while limiting remote icon downloads.

### Cloud and remote files
- Immediately show staged cloud copies, copy batches and completed cloud deletions.
- Use committed remote state for copy conflicts and prevent placeholder entries from causing false merge conflicts.
- Decode cloud file names for display and use provider-managed cloud Trash where available.
- Use permanent deletion for providers that do not support Trash, including FTP/SFTP/SMB/SAF paths.
- Improve FTP transfer staging and suppress harmless logout warnings.
- Handle cancellation as cancellation, recover remote-operation failures cleanly, and avoid crashing when opening remote text files.

### Sync, transfer and backup
- Fix scheduled synchronization in the background and two-step source/destination folder selection.
- Make incomplete sync previews actionable and sync profile deletion easier to find.
- Add transfer history clearing, source/destination details and reliable start/end timestamps.
- Track deletion operations in Transfer Center.
- Restore settings while skipping obsolete non-security values, with a summary of skipped entries.
- Browse for settings backups inside WizeFiles and correctly read selected app paths.

### Browser and Vault
- Correct archive-entry display names, filename collisions, picker handoffs and selection behavior.
- Improve background indexing and file-metadata resilience.
- Harden local sharing and Android 17 error compatibility.
- Make Vault mutations crash-safe and stream files for external editing.

### Maintenance
- Decompose browser search, selection, presentation, actions, operation orchestration and workspace coordination.
- Strengthen architecture boundaries, routing, settings restoration and device verification.
- Complete cleanup localization and remaining app translations.

## 0.6.6 — 2026-08-17
<!-- release-order: 10 -->

### Added
- User-controlled local crash reports with explicit review and sharing.
- Adaptive browser grid controls, file-icon sizing and matching Vault layouts.
- A branded navigation drawer header and a secure-folder action in the browser menu.

### Fixed
- Browser startup and RecyclerView/grid holder refresh crashes.
- Live grid resizing, recycled icons, Vault selection controls, dialogs and system insets.
- App Manager selection, enable/disable and system-app action behavior.
- Text editor dirty-state tracking and save readiness.
- Historical Google Play Billing startup/product-query crashes and package-operation history cleanup. Purchasing was later removed in the open-source release.

### Maintenance
- Complete translations and placeholder validation.
- Split transfer/sync database access, operation commands, runtime lifecycle, scanning and notification handling into focused components.
- Improve release signing configuration and shorten pull-request verification.

## 0.6.4 — 2026-08-12
<!-- release-order: 9 -->

### Added and changed
- Historical Pro entitlement, purchase and feature-access infrastructure. These mechanisms were subsequently removed when WizeFiles became fully open source.
- Polish local sharing and Nearby Transfer screens.

### Fixed
- Initialization failures in minified release builds.

The tag retains the advanced viewers and package-signing features introduced in 0.6.3. Its automatically generated release text repeated several earlier changes; they are recorded once below.

## 0.6.3 — 2026-08-09
<!-- release-order: 8 -->

### Added
- Installed-app management and APK backup.
- Internal image/video previews, a built-in PDF viewer and service-backed audio playback.
- Extended archive and disk-image browsing.
- Offline ebook and saved-document viewers with bounded loading.
- Additional image-format previews and a LibVLC fallback for specialist media codecs.
- Signing and verification workflows for APK, AAB, APKS, APKM and XAPK packages, including key management and secure password re-entry.

### Changed and fixed
- Replace the older built-in image/video editors with focused viewing flows.
- Move selection actions into the bottom panel and improve browser tools, icons and navigation.
- Add adaptive file-icon shapes and correct remote-provider path resolution.
- Update website feature documentation and comparison information.

## 0.6.2 — 2026-08-06
<!-- release-order: 7 -->

Recorded source version; no separate retained release page is available.

- Add transactional archive modification.
- Restore browser inset handling.
- Make the file-operation panel compact and floating.
- Show full rounded floating-action-menu labels.

## 0.6.1 — 2026-08-04
<!-- release-order: 6 -->

Recorded source version; no separate retained release page is available.

- Add authenticated, resumable Nearby Transfer with file/folder offers and receiver approval.
- Introduce the historical beta-expiry presentation and update beta feature documentation. Beta expiry was later removed.

## 0.6.0 — 2026-08-03
<!-- release-order: 5 -->

- Add a persistent Transfer Center with operation history and recovery.
- Add folder synchronization, backup profiles and scheduled jobs.
- Add PC browser access and local-network sharing.
- Refine dual-pane and desktop-style browser interactions.
- Move app version values into source control and refine beta release publishing.

## 0.70 — 2026-08-01
<!-- release-order: 4 -->

This is the original legacy tag, not version 0.7.0.

- Add rclone-backed cloud storage, generated provider configuration and guided account sign-in.
- Fix Box setup, OAuth handoff, cloud path routing and remote file operations.
- Improve cloud-provider selection and remove obsolete external cloud shortcuts.
- Open supported archives with WizeFiles and fix content-URI archive handling.
- Add advanced batch renaming and indexed instant search.
- Add multiple browser tabs and adaptive dual-pane workspaces.
- Add the visual disk map and improve storage-cleanup warnings, permission guidance and layout.
- Polish storage icons, ordering and the floating action menu.
- Remove the earlier standalone Gallery and Music Player flows.
- Document the feature set and revise the licensing/distribution setup used at that time; the current app is GPL-3.0-only.

## 0.66 — 2026-06-11
<!-- release-order: 3 -->

- Publish the updated release/distribution configuration, with manual version overrides, named APK artifacts and optional F-Droid repository publication.
- Preserve release resources needed by the application.

The retained tag contains the same release-tooling milestone as 0.65; no additional app feature change can be established between these snapshots.

## 0.65 — 2026-05-23
<!-- release-order: 2 -->

- Add manual release version/name controls and consistent APK naming.
- Make repository publication optional and improve F-Droid index/signing and release-artifact handling.
- Adjust release resource shrinking to preserve required resources.

## 0.64 — 2026-05-23
<!-- release-order: 1 -->

- Earliest retained WizeFiles tag, pointing to the initial repository snapshot from 2026-05-06.
- Establish the app and its Android build/release baseline in this repository.

No earlier per-version notes are available in the retained history, so individual feature introduction dates before this snapshot are not asserted.
