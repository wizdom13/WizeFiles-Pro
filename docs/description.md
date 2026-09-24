# Wize Files — Detailed App Description

## Overview

**Wize Files** is a full-featured Android file manager built for users who need much more than simple folder browsing. It combines local storage access, network storage connections, archive handling, media browsing, secure vault features, cleanup tools, file inspection utilities, and advanced Android integrations in a single app.

The app is designed to work as both an everyday file browser and a power-user tool. It supports modern Android workflows such as Storage Access Framework paths and document trees, while also offering advanced capabilities like remote servers, checksums, APK inspection, background file jobs, and optional elevated access.

## What the App Does

Wize Files helps users:

- browse and manage files across many storage locations
- work with local, removable, document-tree, archive, and remote paths from one interface
- inspect files in depth, including metadata, permissions, hashes, and media details
- protect sensitive content with a Secure Vault and optional biometric unlocking
- clean up storage by identifying duplicates, stale files, junk, APKs, and unused apps
- run long file operations in the background with notifications and progress handling
- open, edit, share, compress, extract, encrypt, decrypt, and organize content

## Core File Management Features

At its heart, Wize Files is a capable file management app with support for the operations users expect from a serious Android file browser.

### File and Folder Browsing

- List and grid view modes
- Sort by name, type, size, and last modified date
- Ascending or descending sort order
- Optional “folders first” sorting
- Optional per-folder view and sort preferences
- Hidden file visibility toggle
- Breadcrumb navigation and jump-to-path workflows
- Shortcuts to common directories such as Downloads, Documents, Music, Pictures, Movies, Podcasts, Notifications, Alarms, and more

### File Operations

- Create new files and folders
- Rename files and directories
- Copy and move items
- Cut and paste workflows
- Delete items
- Recycle Bin support for recoverable deletion
- Permanent delete options
- Secure shred option for supported local writable files
- Share files with other apps
- Copy file paths
- Add bookmarks for frequently used folders
- Create home screen shortcuts
- Open folders in a terminal app when supported

### Background Job Support

Large operations are designed to continue through the app’s file job pipeline.

- Background copy and move operations
- Archive extraction and creation jobs
- Notifications for long-running operations
- Progress-aware file services and receivers
- Better handling of large or multi-item operations without blocking the UI

## Storage and Location Support

One of the app’s biggest strengths is its broad storage model. Wize Files is not limited to a single local folder tree.

### Local and Device Storage

- Primary internal storage
- External/removable storage shortcuts
- Android document tree support through the Storage Access Framework
- File-system based local paths
- Archive paths exposed as browseable virtual locations

### Remote Storage Support

Wize Files can connect to multiple remote protocols, letting users work with network locations in the same overall experience as local storage.

Supported remote storage types include:

- **FTP**
- **SFTP**
- **SMB / LAN shares**
- **WebDAV**

The app includes add/edit flows for remote servers and stores them as first-class navigation entries.

## Archive Features

Wize Files has substantial archive support beyond simply launching another app.

### Archive Browsing

- Open supported archives as virtual file systems
- Browse archive contents directly inside the app
- Treat archive entries like navigable items where supported

### Archive Creation and Extraction

- Compress files into archives
- Extract archives from the file list or multi-select flows
- Support for multiple output formats including:
  - ZIP
  - TAR.XZ
  - 7Z
- Optional password field for archive creation workflows where supported
- Configurable archive filename encoding

## File Viewing and Text Editing

The app combines focused image/video preview, service-backed audio playback, read-only PDF viewing, built-in text editing, and standard Android integrations.

### Opening Files

- Internal image preview with large-image zoom, EXIF orientation, animation, rotation, and sibling swiping
- Internal video preview with play/pause, seeking, buffering, fullscreen, rotation, and playback speed
- Internal audio player with background, notification, lock-screen, headset, metadata, artwork, seek, speed, and folder-queue controls
- Internal PDF reader with progressive rendering, zoom, search, text selection, passwords, safe hyperlinks, fullscreen, and adaptive two-page layouts
- “Open with another app” from every internal viewer plus standard Android handoff for other file types
- Text-friendly handling and “Open as…” support for forcing a file to be interpreted as a different type

### Editor Workflows

- Edit text-based content inside WizeFiles
- Save or Save As workflows for edited text files
- Hand off image/video editing and other unsupported operations to compatible Android apps
- Keep gallery/music libraries, playlists, PDF editing/annotation, and image/video editing out of scope

### APK Handling

Wize Files includes dedicated Android package handling.

- Detect APK files
- Offer install-or-browse choice for APKs
- Show APK details in the file properties area
- Allow configurable default behavior for opening APK files

## File Details and Inspection Tools

Wize Files includes an unusually rich “details/properties” capability for a mobile file manager.

### General Metadata

- File name
- File type and MIME interpretation
- File size
- Last modified date
- Parent directory
- Archive origin details where relevant
- Link target information for symbolic links where available

### Permissions and Ownership

- Owner and group information where supported
- Permission mode display
- Permission editing flows where supported
- Principal lookup/filter tools in permission editors

### Checksums and Verification

The app can calculate and display multiple checksums and compare user-provided values.

Supported algorithms include:

- CRC32
- MD5
- SHA-1
- SHA-256
- SHA-512

It also supports checksum comparison and match checking.

### Media and File-Type-Specific Details

- Image metadata
- Audio metadata
- Video metadata
- APK information

## Secure Vault Features

One of the app’s standout capabilities is the **Secure Vault** system for private content.

### Vault Capabilities

- Create encrypted vaults
- Name vaults and protect them with a password
- Unlock vaults using password
- Optional biometric unlock support
- Import files and folders into the vault
- Create folders inside the vault
- Open vault contents from inside the secure area
- Export secure vault backups
- Rename vaults
- Delete vaults permanently
- Lock vaults when leaving the screen

### Vault Convenience and Safety

- Optional prompt to lock the vault on exit
- Import completion flow with option to delete original files from device storage
- Recovery-aware messaging if re-encryption of edited files fails
- Secure handling aimed at keeping sensitive files isolated from the main browsing flow

## Security and Privacy Features

Wize Files includes several security-related features beyond the Secure Vault.

### App-Level Protection

- App security password setting
- Protect browser access
- Optional biometric authentication
- Configurable relock timer

### File Protection Features

- Encrypt files
- Decrypt files
- Password-based workflows
- Support for modern crypto algorithm choices visible in the UI, including:
  - AES-256-GCM
  - ChaCha20-Poly1305
- Key derivation options exposed in the UI, including:
  - Argon2id
  - bcrypt

## Storage Cleanup and Space Management

The app includes a dedicated **Storage Cleanup Wizard** designed to help users reclaim space.

### Cleanup Categories

- Duplicate media
- Duplicate files
- Large files
- APK files
- Old downloads
- Old files
- Junk files
- Unused apps
- Other findings

### Cleanup Experience

- Scan storage for cleanup opportunities
- Show reason each item was flagged
- Show reclaimable space
- Review items before deleting
- Open folders or app info from cleanup results
- Handle usage-access-dependent analysis for unused apps

This makes the app more than a browser: it also acts as a lightweight storage maintenance tool.

## Settings, Preferences, and Customization

The app exposes a wide range of settings for interface, behavior, security, and maintenance.

### Interface Customization

- Locale/language preference
- Night mode options
- Black/dark theme option
- File list animations toggle
- File name ellipsize behavior

### Behavior Settings

- Default startup directory
- Manage storages
- Manage standard directory shortcuts
- Manage bookmark directories
- Recycle Bin toggle
- Root strategy preference
- Archive filename encoding
- APK open default action
- Remote file thumbnail reading preference
- PDF thumbnail option on older devices

### Backup and Restore

- Backup settings
- Restore settings from backup files
- Settings import/export flows

## Root, Advanced Access, and Power-User Features

Wize Files includes advanced access features aimed at technical users and power users.

### Elevated / Advanced Integration

- Root-aware workflows
- Integration with libsu-based root strategies
- Hidden API compatibility layer
- Native Linux bridge through JNI/C code
- SELinux-related integration hooks
- Better support for low-level file operations not easily handled through standard Android APIs alone

These features position the app beyond basic consumer file browsers.

## Android Platform Integrations

The project makes broad use of Android capabilities.

### Platform Features

- FileProvider support for secure URI sharing
- Shortcuts support
- Launcher and Leanback launcher entries
- Foreground services for data-sync style operations
- App locale support
- Support for opening files and folders via intents

## Recycle Bin and Recovery

Instead of always forcing permanent deletion, Wize Files supports a more forgiving workflow.

- Recycle Bin toggle in settings
- Dedicated Recycle Bin navigation entry
- Restore support for deleted items
- Empty-bin style cleanup flows

## User Experience Strengths

From the code and resources, the app is clearly designed to balance power with usability.

### UX Characteristics

- Modern Android UI built with AndroidX and Material components
- Dedicated flows for editing, properties, cleanup, settings, vaults, storage setup, and remote server configuration
- Flexible navigation model with storages, standard directories, bookmarks, recycle bin, and secure vaults
- Explicit dialogs for risky actions such as delete, replace, lock, restore, and vault cleanup

## Technical Capabilities and Architecture

The codebase also reflects substantial engineering scope.

### Technical Foundation

- Kotlin-first Android app with some Java and native C/JNI components
- AndroidX architecture pieces, ViewBinding, LiveData, coroutines, and foreground services
- Provider-based storage abstraction for local, remote, archive, and document-tree paths
- Native integrations for filesystem and compatibility work
- Support for API 21+ with newer platform features conditionally enabled

## Who the App Is For

Wize Files is suitable for:

- everyday users who want a stronger file browser than the default Android app
- power users who manage archives, network shares, APKs, and hidden files regularly
- privacy-conscious users who want secure vaults and app-level protection
- people who transfer files across devices through FTP, SMB, SFTP, or WebDAV
- users who want cleanup and storage-management tools in the same app
- developers and maintainers looking at a serious Android file manager architecture

## Summary

**Wize Files** is a comprehensive Android file manager with capabilities that span file browsing, remote storage access, secure storage, archive tools, media management, cleanup tools, background operations, and advanced Android integrations. It is much closer to a desktop-style file management suite than a minimal mobile file picker.

Its key strengths are breadth, technical depth, and feature completeness:

- robust file management
- wide storage support
- strong inspection and metadata tools
- secure vault and privacy features
- remote storage workflows
- cleanup and recycle bin support
- advanced customization and power-user options

In short, Wize Files is a **feature-rich, modern, and technically ambitious Android file manager** intended for serious day-to-day file work as well as advanced storage tasks.
