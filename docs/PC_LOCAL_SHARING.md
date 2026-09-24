# PC Access & Local Sharing

WizeFiles exposes only folders selected in a sharing profile. Browser access is the primary protocol; FTP and explicit FTPS are compatibility options backed by the same `ShareGateway`, provider paths, permissions, audit records, and Transfer Center limit.

## Security defaults

- Sharing starts manually and the service returns `START_NOT_STICKY`.
- Browser access is enabled; FTP is disabled and read-only unless Full management plus FTP write access are both selected.
- Exchange files is the default permission. Full management and destructive browser approval are explicit profile settings; approval is enabled by default.
- Pairing codes and QR tokens expire, browser sessions are revocable, and no credential is persisted.
- The server binds only to an Android-verified Wi-Fi or Ethernet LAN and stops when that network changes. Cellular, VPN, raw-interface fallback, and phone-hotspot-only operation are rejected.
- HTTP is intended only for trusted local networks. HTTPS and FTPS use an Android Keystore device certificate whose SHA-256 fingerprint is shown on the phone.
- Virtual roots reject traversal, symlink escape, credentials in URIs, Vault, root/Shizuku, app-private data, and WizeFiles internal paths.

## Protocol boundaries

HTTP supports authenticated listing, search, preview, ranged downloads, resumable temporary uploads, folder creation, copy/move/rename/delete, ZIP download, multiple roots, client revocation, and phone approval. FTP supports passive UTF-8 sessions, resume, listing, transfer, and explicitly enabled mutations. Anonymous, active-mode, FXP, `SITE`, WebDAV, and SFTP-server mode are not provided.

All long transfers use WizeFiles' global two-operation limiter and Transfer Center records. Stopping or losing the service invalidates credentials; the service is never restarted automatically.

## Merge gate

Keep the pull request draft until CI passes and physical-device tests cover Windows, macOS, Linux, Android-to-Android, local/SAF/removable storage, explicitly selected NAS/cloud roots, files larger than 4 GiB, interrupted transfers, malicious names and paths, multiple clients, screen-off/battery-saver behavior, network switching, HTTP/HTTPS, and FTP/FTPS clients.
