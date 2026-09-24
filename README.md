# WizeFiles

**WizeFiles** is a powerful Android file manager for local storage, removable drives, network shares, cloud accounts, archives, media, Android packages, synchronization, secure storage, and advanced file-management workflows.

**WizeFiles 1.0.0 is free and open-source software licensed under GNU GPL-3.0-only.** Every implemented feature is available without advertisements, subscriptions, in-app purchases, paid tiers, license keys, or feature paywalls.

> [!NOTE]
> WizeFiles is designed to be a complete file-management workspace rather than only a local file browser. Some capabilities depend on Android version, granted permissions, storage-provider support, device hardware, or optional root/Shizuku access.

## Highlights

- Local, SD card, USB, SAF, network, and cloud file management
- Multiple tabs and adaptive dual-pane browsing
- Cross-pane drag and drop
- Persistent Transfer Center with pause, resume, retry, history, and recovery
- FTP/FTPS, SFTP, SMB, WebDAV, S3-compatible storage, and broad cloud support
- Folder synchronization and scheduled backups
- Nearby phone-to-phone transfer with QR-authenticated pairing
- Browser/PC access and local sharing
- Archive browsing, creation, extraction, and supported archive editing
- Encrypted Vaults and standalone file encryption
- Trash Bin and secure local-file shredding
- Storage Cleanup Wizard, duplicate finder, and visual storage analysis
- Built-in image, video, audio, PDF, e-book, text, offline-document, and font viewers
- Installed App Manager and APK/APKS backup
- APK, APKS, APKM, and XAPK inspection and installation
- Android package signing and verification tools
- Root and Shizuku-assisted workflows
- Android TV, tablet, foldable, mouse, and physical-keyboard support

# Features

## File browsing and organization

- Browse internal device storage
- SD card and removable-storage support
- USB storage support
- Android Storage Access Framework locations and document trees
- Multiple tabs
- Adaptive dual-pane browsing
- Independent navigation in each pane
- Cross-pane copy and move
- Cross-pane drag and drop
- Mouse context menus
- Physical-keyboard shortcuts where supported
- Android multi-window support
- Bookmarks and favorite folders
- Standard Android folders
- Configurable storage roots
- Configurable default directory
- File and folder sorting
- Filtering
- Indexed search
- Recent and category views
- File and folder creation
- Rename
- Advanced batch rename
- Copy, move, duplicate, and delete
- Conflict-aware replace, skip, abort, and keep-both behavior
- Home-screen shortcuts
- File associations and Android Open/Edit integration
- Configurable list appearance and filename ellipsizing
- Material-style file and folder presentation

## Transfer Center and background operations

WizeFiles keeps long-running file work separate from the browser so operations can continue without blocking navigation.

- Background copy and move
- Background delete
- Archive creation and extraction jobs
- Persistent Transfer Center
- Progress tracking
- Pause and resume where supported
- Retry failed work
- Recover interrupted work where possible
- Completed history
- Failed history
- Canceled history
- Start and completion timestamps
- Conflict handling
- Partial-completion reporting
- Provider-aware error handling
- Cooperative cancellation
- Resume/checkpoint support where available
- Durable operation state for long-running transfers

## Local, network, and cloud storage

- Local Android filesystem
- SD cards
- USB storage
- SAF/document-provider storage
- FTP
- FTPS
- SFTP
- SMB/LAN shares
- WebDAV
- S3-compatible storage
- Broad cloud-account support through the built-in cloud engine
- Simple WebDAV and S3 setup
- Cloud configuration import
- Advanced cloud-provider configuration
- Cross-provider transfers
- Cloud-to-cloud transfers
- Remote-aware file operations
- Pending/cached presentation for cloud transfers

## Folder synchronization and scheduled backups

- Folder synchronization
- Local-to-local synchronization
- Local-to-remote synchronization
- Remote-aware synchronization
- Update mode
- Mirror-style workflows
- Two-way synchronization
- Move-style synchronization
- File-level conflict handling
- Exclusions
- Safety limits
- Version-retention options
- Manual runs
- Interval schedules
- Daily schedules
- Weekly schedules
- Wi-Fi constraints
- Charging constraints
- Background execution
- Sync history and recovery

## Nearby phone-to-phone transfer

- Direct nearby-device transfer
- Files and complete folders
- QR-authenticated pairing
- Bluetooth-assisted discovery/authentication
- Wi-Fi data transfer
- Durable session state
- Reconnect-aware transfers
- Protection against duplicate or late payloads
- Recovery after process recreation

## PC/browser access and local sharing

- Access files from another device through a web browser
- Local HTTP sharing
- Local FTP/FTPS server-style sharing where enabled
- Built-in sharing tools
- Android Share integration
- Save incoming shared content into WizeFiles
- Open files with external Android apps when preferred

## Trash Bin and permanent deletion

- Recoverable **Trash Bin** for supported local storage
- Restore deleted items
- Permanently delete Trash Bin contents
- Permanent deletion for providers that do not support recoverable deletion
- Clear distinction between recoverable deletion and irreversible deletion

## Secure shredder

For eligible writable local files, WizeFiles can perform irreversible deletion:

- Zero-overwrite supported local files
- Sync written data
- Delete after overwrite
- Apply shredding to supported files inside selected local folders
- Hide the option for remote, SAF, archive, cloud, and other paths where secure overwrite cannot be guaranteed

> [!WARNING]
> Secure shred is irreversible. On flash storage, filesystem behavior, wear leveling, snapshots, and hardware-level remapping can still limit guarantees beyond the file-level overwrite performed by the app.

## Archives, compressed files, and disk images

- Browse archives like folders
- Create archives
- Extract archives
- Password-protected archive support
- ZIP workflows
- 7z workflows
- TAR and related compressed formats
- Broad read-only archive support
- Extended archive and disk-image browsing
- Supported archive-content editing
- Archive filename-encoding options
- Defensive handling of malformed and hostile archive entries
- Traversal protection

## Image viewer

- Built-in image preview
- Local, removable, SAF, network, and cloud-backed images
- Large-image tiled zoom
- EXIF orientation
- GIF playback
- SVG rendering
- WebP support
- RAW and additional image-format decoding where supported
- TIFF, TGA, and ICO support
- Rotation
- Swipe between neighboring images
- Media metadata in File Properties
- Open with another app

## Video player

- Built-in video playback
- Local and provider-backed video
- Play/pause
- Seeking
- Buffering feedback
- Fullscreen playback
- Playback-speed control
- Rotation handling
- Remote-storage streaming
- Fallback playback for specialist containers/codecs
- Open with another app

## Audio player

- Dedicated background audio playback
- Folder-based queue
- Notification controls
- Lock-screen controls
- Headset controls
- Bluetooth media controls
- Metadata and artwork
- Seeking
- Queue controls
- Background playback
- Specialist-format fallback playback

## PDF reader

- Built-in read-only PDF viewer
- Progressive page display
- Zoom
- Text search
- Text selection
- Password prompts
- Safe hyperlink handling
- Fullscreen mode
- Adaptive two-page layouts on larger screens

## E-books, offline documents, and text

- EPUB reading
- MOBI reading
- Offline HTML viewing
- MHT viewing
- CHM viewing
- MAFF viewing
- Text viewing
- Text editing
- Common script and configuration formats
- Android external-edit integration
- Save As workflows
- Provider-aware write checks before editing

## Font Viewer

- TTF
- OTF
- TTC
- Local and provider-backed font files
- Font name and format information
- Editable specimen text
- Multilingual samples
- Adjustable preview size

## File Properties and information

- General file/folder details
- Size and timestamps
- Checksums
- Media metadata
- Image information
- APK/package information
- Signing-certificate details
- Permission information
- Storage/provider information

## App Manager

- Installed-app inventory
- Search installed apps
- Filter installed apps
- Sort installed apps
- Open installed apps
- Inspect app/package details
- Android-confirmed uninstall
- System-app-aware restrictions
- Enable/disable controls where Android permissions allow them
- APK sharing
- APK backup
- Split APK/APKS backup
- Durable backup jobs through Transfer Center

Application data is intentionally not included in APK backups.

## APK and Android-package tools

- APK inspection
- APK signing
- APK signature verification
- Certificate details
- AAB signing workflows
- APKS inspection
- APKM inspection/import
- XAPK inspection
- Android-package icon extraction
- Split-set validation
- Signing-identity comparison
- Package metadata inspection

## Android package installer

WizeFiles includes a verified review and installation flow for:

- APK
- APKS
- APKM
- XAPK

Installer capabilities include:

- Safe staging of incoming provider-backed packages
- Device-compatible ABI split selection
- Density split selection
- Locale split selection
- Base/feature split retention
- Package/version consistency checks
- Signer consistency checks
- Comparison with an already installed version
- Version-change presentation
- Permission comparison
- Feature comparison
- Component comparison
- Standard Android installation confirmation
- Optional root-backed installation
- Explicit root-gated downgrade approval
- Separately confirmed signature-mismatch replacement for devices already using Core Patch or another compatible system modification
- XAPK OBB validation and placement
- OBB rollback/retry after partial completion
- **Open** after successful installation when the installed app exposes a launchable activity

**AAB installation is not supported.** AAB files can be inspected/signed, but installation requires generating installable APK splits first.

## Storage Cleanup Wizard

- Review-first cleanup
- Duplicate media detection
- Duplicate file detection
- Large-file analysis
- APK/package cleanup
- Junk analysis with conservative classification
- Unused-app analysis
- System apps excluded from unused-app cleanup
- Readable item details
- All categories deselected by default
- Ignore/exclude options
- Choose which duplicate copy to keep
- Provider-aware deletion
- Visual storage analysis
- Disk-map style presentation

## Encryption, Vaults, and privacy

- Encrypted Vaults
- Multiple Vaults
- Password protection
- Biometric unlock
- Vault lock/relock behavior
- App password
- Optional protection of browser access
- Standalone file encryption
- Recovery safeguards around Vault changes
- User-controlled local crash reports
- No automatic crash-report upload
- No advertising
- No advertising profile
- No subscription or purchase entitlement system
- No Free/Pro feature split

## Root and Shizuku

Root is optional and is not required for normal WizeFiles use.

Where explicitly enabled, advanced workflows can use:

- Root-capable file operations
- Shizuku-assisted access
- Permission/ownership operations where supported
- Advanced package-installation cases
- Version downgrade installation
- XAPK OBB placement when ordinary Android storage access is insufficient

WizeFiles does **not** disable Android package verification by itself. Signature-mismatch installation is only useful on devices where the user has already installed Core Patch or another compatible system modification.

## Appearance and device support

- Android 11 and later
- Phones
- Tablets
- Foldables
- Android TV / Leanback launcher
- Adaptive layouts
- Dual-pane layouts
- Mouse support
- Physical-keyboard support
- Light theme
- Dark theme
- Black night mode
- Material-style interface
- Configurable locale/language support

# Free and open source

WizeFiles has no commercial feature tier.

- No advertisements
- No Free/Pro split
- No Google Play Billing integration
- No subscriptions
- No in-app purchases
- No paid entitlement server
- No license-key feature unlocks
- No feature-count limits tied to payment
- All implemented capabilities are available to every user

WizeFiles project-owned source is licensed under **GNU GPL-3.0-only**. Third-party components retain their respective upstream licenses and notices.

# Feature comparison

The table below is a practical feature snapshot based on WizeFiles' current implementation and the comparison information maintained by the project. Competitor capabilities can change between versions, regions, editions, or optional plug-ins.

**Legend:** ✓ = supported · ◐ = partial, narrower, or plug-in based · — = not documented / not included in the compared product scope

## Browsing and organization

| Feature | WizeFiles | Solid Explorer | MiXplorer | Files by Google | X-plore | Total Commander | Cx | ASTRO |
|---|---|---|---|---|---|---|---|---|
| Local / SD / USB storage | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Recent/category collections | ✓ | ✓ | ◐ | ✓ | ◐ | ◐ | ✓ | ✓ |
| Multi-tab browsing | ✓ | ◐ | ✓ | — | — | ◐ | — | — |
| Dual-pane browsing | ✓ | ✓ | ✓ | — | ✓ | ✓ | — | — |
| Cross-pane drag and drop | ✓ | ✓ | ✓ | — | ◐ | ✓ | — | — |
| Mouse/keyboard support | ✓ | ✓ | ◐ | ◐ | ◐ | ◐ | ◐ | — |
| Multiple app windows | ✓ | — | — | — | — | — | — | — |
| Indexed/advanced search | ✓ | ✓ | ✓ | ◐ | ◐ | ◐ | ◐ | ◐ |
| Advanced batch rename | ✓ | ✓ | ✓ | — | ✓ | ✓ | ◐ | ◐ |
| Bookmarks/favorites | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Home-screen shortcuts | ✓ | ◐ | ✓ | ✓ | ✓ | ◐ | — | — |
| Settings/bookmark restore | ✓ | — | ✓ | — | ◐ | — | — | — |

## Cloud, network, sharing, and jobs

| Feature | WizeFiles | Solid Explorer | MiXplorer | Files by Google | X-plore | Total Commander | Cx | ASTRO |
|---|---|---|---|---|---|---|---|---|
| Direct cloud accounts | ✓ Broad | ✓ | ✓ | ◐ | ✓ | ◐ | ✓ | ✓ |
| Cross-provider/cloud transfers | ✓ | ✓ | ✓ | — | ✓ | ◐ | ✓ | ✓ |
| SMB/LAN client | ✓ | ✓ | ✓ | — | ✓ | ◐ | ✓ | ◐ |
| FTP/FTPS client | ✓ | ✓ | ✓ | — | ✓ | ◐ | ✓ | — |
| SFTP client | ✓ | ✓ | ✓ | — | ✓ | ◐ | ✓ | — |
| WebDAV client | ✓ | ✓ | ✓ | — | ✓ | ◐ | ✓ | — |
| S3-compatible storage | ✓ | — | ◐ | — | — | — | — | — |
| Persistent operation queue | ✓ Transfer Center | ◐ | ✓ | — | ◐ | ◐ | ◐ | — |
| Pause/resume/retry/history | ✓ | ✓ | ◐ | — | ◐ | ◐ | ◐ | — |
| Folder sync / scheduled backup | ✓ | — | ◐ | ◐ | ✓ | — | — | ◐ |
| Browser access to phone | ✓ | ✓ | ✓ | — | ✓ | ◐ | — | — |
| FTP server | ✓ | ✓ | ✓ | — | ✓ | — | ✓ | — |
| Nearby phone-to-phone transfer | ✓ QR-authenticated | ◐ | ✓ | ✓ | ✓ | ◐ | — | — |
| Stream media from remote storage | ✓ | ✓ | ✓ | — | ✓ | ✓ | ◐ | ◐ |

## Archives, security, packages, and recovery

| Feature | WizeFiles | Solid Explorer | MiXplorer | Files by Google | X-plore | Total Commander | Cx | ASTRO |
|---|---|---|---|---|---|---|---|---|
| Create/extract common archives | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | ✓ | ◐ |
| Extended archive/disk-image browsing | ✓ | ◐ | ✓ | — | ◐ | ◐ | — | — |
| Password-protected archives | ✓ | ✓ | ✓ | — | ◐ | ◐ | ◐ | — |
| Edit archive contents | ✓ | ◐ | ✓ | — | ✓ | ✓ | — | — |
| Standalone file encryption | ✓ | ✓ | ✓ | — | — | — | — | — |
| Encrypted vault/protected storage | ✓ | ✓ | ✓ | ✓ | ✓ | — | — | ✓ |
| Biometric/app lock | ✓ | ✓ | ◐ | ◐ | ✓ | — | — | ✓ |
| Trash/recoverable deletion | ✓ | ◐ | ◐ | ✓ | ✓ | — | ✓ | ◐ |
| Secure local-file shredder | ✓ | — | — | — | — | — | — | — |
| Root access | ✓ | ✓ | ✓ | — | ✓ | ✓ | — | — |
| Shizuku-assisted access | ✓ | — | ✓ | — | ✓ | ✓ | ✓ | — |
| Checksums/hash tools | ✓ | ◐ | ✓ | — | ◐ | ◐ | ◐ | ◐ |
| Android package signing | ✓ | — | ✓ | — | — | — | — | — |
| APK/APKS/APKM/XAPK installer | ✓ | — | — | — | — | — | — | — |

## Storage intelligence, apps, and viewers

| Feature | WizeFiles | Solid Explorer | MiXplorer | Files by Google | X-plore | Total Commander | Cx | ASTRO |
|---|---|---|---|---|---|---|---|---|
| Storage analyzer | ✓ | ✓ | ◐ | ✓ | ✓ | — | ✓ | ✓ |
| Duplicate-file finder | ✓ | ✓ | — | ✓ | — | — | ✓ | — |
| Large/junk/unused cleanup | ✓ | ✓ | ◐ | ✓ | ◐ | — | ✓ | ✓ |
| Visual disk map | ✓ | ◐ | — | — | ✓ | — | ◐ | — |
| Installed App Manager / APK backup | ✓ | ◐ | ✓ | ◐ | ✓ | ✓ | ✓ | ✓ |
| Text editor | ✓ | ✓ | ✓ | — | ◐ | ✓ | ◐ | ◐ |
| Image viewer | ✓ | ✓ | ✓ | ✓ | ✓ | ◐ | ◐ | ◐ |
| Video player | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ◐ | ◐ |
| Background audio player | ✓ | ✓ | ✓ | ◐ | ✓ | ✓ | ◐ | ◐ |
| PDF reader | ✓ | — | ◐ | ✓ | ✓ | — | — | — |
| PDF search/selection | ✓ | — | ◐ | ✓ | — | — | — | — |
| EPUB/MOBI reader | ✓ | — | ✓ | — | — | — | — | — |
| Offline HTML/MHT/CHM/MAFF reader | ✓ | — | ✓ | — | — | — | — | — |
| RAW/TIFF/TGA/ICO decoding | ✓ | ◐ | ✓ | ◐ | ◐ | — | ◐ | ◐ |
| Font viewer | ✓ TTF/OTF/TTC | — | ✓ | — | — | — | — | — |
| Android TV support | ✓ | ◐ | ✓ | — | ✓ | ◐ | ✓ | — |
| Tablet/foldable optimization | ✓ | ✓ | ◐ | ◐ | ◐ | ◐ | ✓ | ◐ |

> [!NOTE]
> This comparison is intended as a user-facing feature guide, not a benchmark or ranking. “—” means a comparable capability is not documented in the project comparison; it does not necessarily mean the competing app can never perform a similar task through another workflow or add-on.

# Project direction and contributions

WizeFiles is developed and maintained as a **curated Wize Soft project**.

To keep product direction, release responsibility, and code ownership consistent, **external code contributions and pull requests are not being accepted at this time**. We appreciate the interest and respectfully ask contributors not to submit unsolicited pull requests.

Feedback is still very useful. Bug reports, reproducible problems, usability feedback, and feature suggestions are welcome through the repository's issue tracker.

When reporting a problem, please include enough information to reproduce it, but **never post passwords, server credentials, private keys, sensitive filenames, or other confidential data**.

# License

WizeFiles project-owned source is licensed under the **GNU General Public License v3.0 only (GPL-3.0-only)**.

Third-party components remain under their own upstream licenses and notices. See the project `LICENSE`, `THIRD_PARTY_NOTICES.md`, and included third-party license notices for details.

---

**WizeFiles** — one file manager for local storage, networks, cloud accounts, archives, media, Android packages, cleanup, synchronization, secure storage, and advanced Android workflows.
