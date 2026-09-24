# Secure shredder

Secure shred is an irreversible deletion option for writable local files and folders. It is separate
from the recoverable Trash Bin and from ordinary permanent deletion.

## Eligibility

The option is available only when every selected path:

- uses the local filesystem;
- still exists and is writable; and
- is a regular file or directory.

Remote, rclone, FTP, SFTP, SMB, SAF/document-provider, archive, and unknown provider paths are not
eligible. Mixed selections containing an unsupported path cannot be marked for secure shredding.

## Operation

For each supported local file, WizeFiles:

1. overwrites the current logical file length with one zero-filled pass;
2. synchronizes the file descriptor so the write is handed to storage;
3. permanently deletes the file instead of moving it to the Trash Bin.

Directory selections are traversed and the contained local files are treated before their directory
entries are removed. The choice is persisted for the active delete operation, while “skip
confirmation for this session” is intentionally not persisted across process recovery.

## Storage limitations

Secure shred reduces the chance of recovering data from ordinary local filesystem blocks, but no
Android application can guarantee physical erasure on flash storage. Wear levelling, copy-on-write,
journaling, snapshots, controller remapping, backups, and previously synchronized copies may retain
older data outside the file's current logical blocks.

For highly sensitive material, use encryption before writing the file, remove every synchronized or
backup copy, and treat device-level secure erase as a separate operation.
