# Extended archives and disk images

WizeFiles keeps libarchive as the primary archive reader and uses the official 7-Zip 26.02
format module only when libarchive cannot open a source. The selected backend is recorded on each
entry, so reading an entry never probes a second backend or changes interpretation mid-session.
Archive creation and editing remain on the existing libarchive path.

## Read support

The fallback covers the formats compiled by 7-Zip's `Format7zF` bundle, including 7z, ZIP,
RAR/RAR5, CAB, CHM, ISO/UDF, WIM, XAR, ARJ, LHA/LZH, MSI compound files, NSIS installers, DMG,
APFS, EXT, FAT, HFS/HFS+, NTFS, SquashFS, CramFS, QCOW2, VDI, VHD/VHDX, VMDK, MBR/GPT and
UEFI/IHEX containers. Availability still depends on the selected file actually containing a valid
structure; an extension alone never makes arbitrary bytes a container.

Disk images are exposed only as read-only virtual folders. WizeFiles does not mount them, attach
loop devices, write back sectors, resize images, repair filesystems or edit partition tables.
Contained files can be previewed or copied out through the normal read-only archive filesystem.

Split 7z, ZIP and RAR sets are opened through 7-Zip's adjacent-volume callback. Requested volume
names must be plain basenames and are resolved only inside the staged source directory. Paths with
separators, `.` or `..` are rejected.

## Safety limits

The existing archive validator remains authoritative for both backends:

- at most 10,000 entries;
- at most 1 GiB for one uncompressed entry;
- at most 4 GiB total uncompressed bytes per container view;
- at most a 200:1 expansion ratio when compressed size is known;
- no absolute paths, traversal, unsafe symlink targets or duplicate normalized paths.

The native bridge independently caps metadata enumeration and output bytes. Extraction writes to a
mode-0600 cache file and deletes that file when its stream closes or when extraction fails. Device
nodes, FIFOs, ownership and privilege metadata are never materialized.

## Native provenance

The build downloads `7z2602-src.tar.xz` from 7-zip.org, verifies its published SHA-256 digest and
compiles the upstream `Format7zF` bundle for each Android ABI. The app carries the upstream license
text and source/build coordinates in `native-dependencies.json`. RAR support is read-only; WizeFiles
does not create RAR archives or use the restricted unRAR code to develop a RAR-compatible archiver.

Both `lib7z.so` and `libsevenzip-jni.so` are checked by the existing 16 KiB ELF alignment gate for
64-bit Android ABIs.
