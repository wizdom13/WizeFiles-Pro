# Archive editing (v1)

WizeFiles edits supported archives transactionally. The archive browser exposes paste, delete,
rename, new folder, keyboard actions, and internal drag-and-drop for unencrypted ZIP, 7z, and
TAR.XZ archives. The archive filesystem remains read-only at the NIO provider level; browser
actions are collected into one `ARCHIVE_MODIFY` Transfer Center operation.

## Safety model

1. Normalize and validate the complete final namespace before writing.
2. Fingerprint and lock the original archive.
3. Stream existing entries and additions into one hidden temporary archive.
4. Reopen the result, compare its manifest, and read every regular entry to EOF.
5. Use same-filesystem atomic replacement where available.
6. Otherwise keep a complete recovery backup while overwriting and revalidating the provider
   object.

The original is never modified during reconstruction. The short commit phase is non-cancellable.
Process loss during a journaled swap is resolved on resume by either completing the validated swap
or restoring the backup. Pause or process loss during reconstruction restarts the reconstruction;
partially written compressed output is never appended to.

Encrypted, split, nested, signed, RAR, ISO, CAB, APK, AAB, JAR, DOCX, XLSX, PPTX, EPUB, and
unknown formats remain read-only. Symbolic links cannot be pasted into an archive. Existing unsafe
links, traversal paths, case-insensitive duplicates, and archive-bomb limit violations abort the
transaction.

## Physical validation checklist

- ZIP: add one file, a deep folder tree, an empty folder, delete multiple entries, rename a file,
  and rename a populated folder.
- Repeat supported actions with unencrypted 7z and TAR.XZ.
- Exercise Replace/merge, Keep both, Skip, and Cancel; confirm Keep both remaps a whole folder.
- Copy and cut/paste external local, SAF, SMB, SFTP, WebDAV, and rclone sources into each format.
- Drag one and multiple files into an archive in single- and dual-pane modes.
- Pause/cancel during rebuilding and verify the original hash is unchanged.
- Kill the process during rebuilding, validation, atomic replacement, provider overwrite, and
  source cleanup; resume from Transfer Center and verify recovery.
- Test low local space, remote quota exhaustion, read-only providers, and disconnected providers.
- Test 10,000 small entries, 1 GiB entries, Unicode/case collisions, corrupt archives, and malicious
  traversal/symlink inputs.
- Confirm all tabs and panes refresh after commit and indexed search no longer shows removed paths.
- Confirm encrypted/split/signed/structured/nested archives show as read-only.

For beta validation, retain the recovery backup if WizeFiles reports a commit failure and collect
the Transfer Center detail log before retrying.
