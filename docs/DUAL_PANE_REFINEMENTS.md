# Dual-pane interaction refinements

This feature uses Android's native local drag session. `ClipData` carries only an opaque session
identifier; file paths and provider data remain inside `FileListActivity`. Tabs are hover navigation
targets only. Actual drops are accepted by writable pane backgrounds, folders and breadcrumbs and
are executed by the existing `FileOperationService` and Transfer Center pipeline.

## v1 boundaries

- Internal WizeFiles drag-and-drop only; no cross-app import or export.
- No tab reordering and no direct transfer onto a tab.
- Touch asks Copy or Move. Mouse uses Ctrl for Copy and Shift for Move.
- Existing conflict, overwrite and delete confirmations remain authoritative.
- Picker flows and Recycle Bin drag mutations are disabled.
- Archive contents are copy/extract only.
- No new permission or third-party drag dependency.

## Physical merge gate

- Touch phone; touch/stylus tablet; Chromebook mouse/trackpad/keyboard.
- Samsung DeX or Android desktop mode; foldable unfolded and hinged postures.
- Local, SD, SAF, SMB, SFTP and rclone/cloud paths.
- Same-provider and cross-provider transfers, including large multi-selection.
- Read-only targets, archives, Recycle Bin and picker flows.
- Breadcrumb and tab-hover navigation during a live drag.
- Ctrl/Shift mouse modes, conflict handling, cancellation and Transfer Center recovery.
