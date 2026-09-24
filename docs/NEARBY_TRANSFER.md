# Nearby Transfer v1

Nearby Transfer sends files and complete folders directly between two WizeFiles devices through Google Play services Nearby Connections using `P2P_POINT_TO_POINT`.

## Scope

- WizeFiles-to-WizeFiles only; it does not implement Android Quick Share interoperability.
- One sender and one receiver per session.
- Sending is copy-only and never removes the source.
- The receiver must explicitly allow each request and display a one-time verification QR.
- The sender must scan that QR; there is no numeric fallback.
- The receiver is visible only after opening Nearby Transfer and only for two minutes.
- Local, SAF, cloud and network paths use WizeFiles' existing `Path` providers.
- Nearby send and receive jobs are persisted in Transfer Center.

## Recovery

Files stream sequentially. The receiver writes to `.wizefiles-part-*` temporary files, flushes and acknowledges durable progress every 4 MiB, and renames the temporary item only after completion. Reconnection resumes from the receiver's last acknowledged offset when the source provider can be reopened and skipped. Completed items are not sent again.

After a process stop or reboot, receiving does not restart automatically. Both users must reopen Nearby Transfer, authenticate with a fresh QR and reconnect. Transfer Center marks an interrupted operation as recoverable.

## QR authentication

- Nearby's raw authentication token is kept only in service memory and cleared after connection, rejection, timeout or process death.
- WizeFiles displays only a domain-separated SHA-256 proof encoded as `WZF-NEARBY:1:<base64url-proof>`; the raw token is never encoded in the QR.
- The receiver must tap **Allow and show QR** before its side is accepted.
- The sender accepts its side only after a constant-time proof match.
- Verification expires after 60 seconds, and every initial or resumed connection requires a fresh Nearby token and QR.
- Google Code Scanner reads only QR codes through Google Play services without adding camera permission to WizeFiles.

## Security boundaries

- Absolute paths, drive paths, backslashes, control characters, empty segments, `.` and `..` are rejected.
- Accepted paths are normalized and required to stay below the chosen destination.
- Symbolic links are not sent.
- The manifest is limited by item count, path depth, path length, decoded size and Nearby's bytes-payload limit.
- Existing destinations require an explicit Keep both, Skip or Replace decision. Replace uses a complete temporary file before the final rename.
- Source URIs remain local to the sender and are not included in the wire manifest.

## Physical-device validation

Test Android 11 through 17 on Pixel, Samsung and Xiaomi devices where available:

1. Receiver approval, matching QR, wrong QR, expired QR, scanner cancellation and fresh QR on resume.
2. Single files, empty files, empty folders and nested folder trees.
3. Thousands of small files and files larger than 4 GiB.
4. Local, SD-card, SAF, rclone, SMB, FTP/FTPS, SFTP and WebDAV sources.
5. Keep both, Skip and Replace conflicts.
6. Pause/resume from each device and from Transfer Center.
7. Bluetooth/Wi-Fi interruption, screen off, process kill and reboot.
8. Low-storage, source mutation, destination removal and permission revocation.
9. Verify the receiver is not discoverable after timeout, cancellation, completion or reboot.
