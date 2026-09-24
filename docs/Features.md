WizeFiles already has unusually broad storage, archive, vault, text-editing, root/Shizuku, and cloud capabilities. Its main missing features are the productivity workflows that established competitors make highly visible and easy to use.

## Most important missing features

| Priority | Missing feature                             | Competitors offering it                    | Why it matters                                                                                              |
| -------- | ------------------------------------------- | ------------------------------------------ | ----------------------------------------------------------------------------------------------------------- |
| 2        | **Advanced batch rename**                   | Solid Explorer, X-plore, File Manager Plus | Rename hundreds of files using numbering, date, replacement, prefixes, suffixes and patterns.               |
| 3        | **Duplicate-file finder**                   | Solid Explorer, Files by Google, Cx        | One of the most understandable and marketable cleaner features.                                             |
| 4        | **Access phone from a PC browser**          | Solid Explorer, X-plore, FX                | Starts a local web server so users can upload/download files without cables or installing desktop software. |
| 8        | **Indexed instant search**                  | Solid Explorer                             | Much faster than scanning storage every time, especially with hundreds of thousands of files.               |
| 9        | **Visual disk map**                         | X-plore and some storage analyzers         | Shows large folders as blocks, making wasted space easier to understand.                                    |
| 10       | **File-operation history and diagnostics**  | Mature desktop-style managers              | A persistent log should show successes, failures, skipped files, conflict decisions and retry actions.      |

Solid Explorer currently promotes dual-pane transfer, indexed search, patterned batch renaming, browser-based sharing, duplicate detection and resumable file operations. [Solid Explorer listing](https://play.google.com/store/apps/details?id=pl.solidexplorer2)

MiXplorer offers unlimited tabs, dual panels, parallel operation tasks, archive modification, FTP/HTTP servers and exceptionally broad archive support. [MiXplorer features](https://mixplorer.com/)

X-plore includes a dual-pane tree, Wi-Fi sharing, browser access from a PC, batch rename, hex and SQLite viewers, DLNA and an SSH terminal. [X-plore listing](https://play.google.com/store/apps/details?id=com.lonelycatgames.Xplore)

## Additional power-user gaps

These are valuable, but less important commercially:

* **Tree-view navigation:** particularly useful for deeply nested servers.
* **Hex/binary viewer:** available in X-plore and FX.
* **SQLite database viewer:** available in X-plore and Amaze.
* **PDF/e-book viewer:** X-plore and MiXplorer support this.
* **Edit archives in place:** add, remove and rename entries without extracting everything.
* **APK backup and application management:** backup installed APKs, inspect them and optionally uninstall applications.
* **SSH terminal:** X-plore exposes a shell alongside SFTP.
* **DLNA/UPnP browsing:** useful for TVs and media servers.
* **Tagging and color labels:** useful for organizing files across different locations.
* **Saved searches and smart collections:** for example, “PDFs modified this week larger than 20 MB.”
* **Keyboard and mouse shortcuts:** important for tablets, Chromebooks and Android desktop mode.
* **Phone-to-phone local transfer:** QR-assisted or nearby Wi-Fi transfer, similar to FX Connect.
* **Custom USB filesystem driver:** MiXplorer supports filesystems such as exFAT and read-only NTFS independently of some device limitations.

FX notably provides multiple windows, dual view, browser access, Wi-Fi Direct transfers, a hex viewer and script execution. [FX File Explorer listing](https://play.google.com/store/apps/details?id=nextapp.fx)

Amaze provides tabs, drag-and-drop, an FTP server, application management and database/APK readers. [Amaze listing](https://play.google.com/store/apps/details?id=com.amaze.filemanager)

## Features WizeFiles is no longer missing

The current repository shows that WizeFiles already has:

* Direct cloud-account onboarding over its embedded rclone engine
* Dedicated WebDAV and S3 setup
* FTP, SFTP and SMB clients
* SAF, SD card and external-drive access
* Advanced archives and password support
* Encrypted vault and biometric protection
* Storage cleaner/analyzer
* Recycle bin
* Checksums, APK and permission details
* Focused built-in image/video preview, service-backed audio player, read-only PDF reader, built-in text editing, and external Android Open/Edit with integration
* Root and Shizuku support
* Settings backup and restoration
* State-preserving in-app browser tabs and Android multi-window browsing
* Adaptive dual-pane browsing with independent navigation, search and selection in each pane
* Direct copy/move to the other pane through the normal conflict-aware transfer service
* Persistent Transfer Center with pause, resume, retry, recovery, queue controls and item history
* Folder synchronization and scheduled backup with update, mirror, two-way and move modes
* Saved first-run previews, file-level conflicts, exclusions, safety limits and version retention
* Manual, interval, daily and weekly WorkManager schedules with Wi-Fi and charging constraints

Therefore, “simple cloud onboarding” should no longer be listed as a fundamental missing feature, although its usability and provider reliability still need on-device testing. These capabilities are documented in the current [WizeFiles repository](https://github.com/wizdom13/WizeFiles).

## Recommended implementation roadmap

I would implement the gaps in this order:

1. **Duplicate-file finder**
2. **Advanced batch rename**
3. **Wi-Fi browser access from a computer**
4. **Dual-pane drag-and-drop**
5. **Indexed search**
6. **Archive-as-folder editing**

For monetization:

* Keep local/SAF file management, tabs, viewers, Transfer Center recovery, signature verification, one remote connection, one vault and single-app App Manager operations free.
* Reserve dual-pane and cross-pane workflows, sync profiles and schedules, package signing, archive mutation, batch App Manager actions, additional remote connections, rclone power-user setup, additional vaults, root access and built-in local servers for **WizeFiles Pro**.
* Never block access to existing files, vault data, operation history or recovery because Pro expires.
* Keep viewer support focused on file inspection: no built-in image/video editors, gallery or music library, playlists, or PDF editing/annotation. Focused viewers and background audio strengthen file workflows without turning WizeFiles into a media suite.

My overall conclusion is that WizeFiles is not missing basic capability. It is missing a polished **productivity layer**. Dual-pane navigation, batch rename, duplicates, transfer control, PC access and synchronization would close most of the meaningful gap with Solid Explorer, MiXplorer and X-plore.
