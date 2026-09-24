# App Manager

App Manager is WizeFiles’ installed-package inventory and APK-backup feature. It is intentionally
separate from Storage Cleaner: an installed application is a package record, not an ordinary file.

## Scope and privacy

- Only packages visible to the current Android user/profile are queried.
- The package inventory remains in memory and package names are not sent to analytics.
- Backups contain APK files only. Application data, accounts, preferences, databases, and private
  files are never included.
- There is no root, Shizuku, silent uninstall, freeze, disable, or cross-profile operation.
- Android’s system uninstaller confirms every package removal.

WizeFiles already qualifies for and declares `QUERY_ALL_PACKAGES` as a file manager. App Manager
adds the normal `REQUEST_DELETE_PACKAGES` permission so it can open Android’s user-confirmed
uninstaller. No runtime permission is introduced.

## Backup format

- A package with one APK is exported as `Name_version.apk`.
- A split package is exported as `Name_version.apks`.
- `.apks` is a ZIP containing `base.apk`, every installed split, and `metadata.json` with the package
  version, archive entry names, and SHA-256 checksums.

Share exports use WizeFiles’ non-exported file provider with temporary read-only URI grants. Share
cache sessions are scheduled for cleanup after 24 hours. **Back up selected to…** uses WizeFiles’
directory picker, then sends the generated files through `FileOperationService` and Transfer Center;
those cache sessions are retained for seven days to protect slow remote transfers.

## Selection behavior

- Search or filter changes clear selection so no hidden package remains selected.
- Sort changes preserve selection.
- Select all applies only to the current filtered result set and becomes Deselect all when complete.
- Info and Open require exactly one selected package. Open is unavailable for disabled or headless
  packages.
- Multiple uninstall requests are launched sequentially because Android requires one system
  confirmation per package.

## Physical-device test plan

1. Verify All, User, System, and Disabled filters plus all five sort modes.
2. Verify launcher icons remain circular in light, dark, black, and dynamic-color themes.
3. Verify search/filter clears selection, sort preserves it, and Select all affects filtered results.
4. Verify Open, App Info, single uninstall, cancelled uninstall, and sequential multi-uninstall.
5. Share one regular package and confirm the exact `.apk` filename and installability.
6. Share one split package, inspect all ZIP entries/checksums, and reinstall it with a compatible
   split-package installer.
7. Back up regular and split packages to local, cloud, FTP/SFTP/SMB, and rclone destinations; verify
   Transfer Center progress, conflict handling, cancellation, and final bytes.
8. Cancel an in-progress export and confirm that no partial file remains.
