# Android security surface inventory

This inventory records the application components reachable by other packages and
the privileged permissions requested by the main manifest. Review it whenever a
manifest entry, intent contract, media command, or permission changes.

## Exported components

| Component | Exposure and intended caller | Controls and audit result |
| --- | --- | --- |
| `FileListActivity` | Launcher/Leanback entry point and the app-specific `VIEW_DOWNLOADS` action. | Treat all incoming intents as untrusted. It does not directly expose a file provider. |
| `ExternalViewRouterActivity` | Accepts `content:` and `file:` `VIEW` intents for supported documents, archives, scripts, and directories. | The router is intentionally narrow and forwards to non-exported viewers. Preserve URI-grant checks, bounded parsing, and scheme/type validation. |
| `AudioPlaybackService` | Media3 `MediaSessionService` endpoint used by WizeFiles and trusted system media controllers. | Export is required for system media controls. `onGetSession` and `onConnect` reject controllers that are neither the application package nor trusted by Media3. Trusted system controllers receive standard playback controls, while only the application package receives or may invoke load/stop custom commands. Session identifiers resolve through the app's in-memory store rather than caller-supplied paths. |
| `SettingsActivity` | Android `APPLICATION_PREFERENCES` entry point. | Displays application settings; it must not execute privileged mutations solely from unvalidated intent extras. |
| `SaveAsActivity` | Android `SEND` target for any MIME type. | Treat ClipData, streams, names, sizes, and MIME types as hostile. Access must remain limited to caller-granted URIs and user-selected destinations. |

All other application services and providers are non-exported. Boot/time receivers
are also non-exported, and the app file provider requires per-URI grants. Components
without an intent filter inherit a non-exported default but should be made explicit
when touched.

### Media playback service audit checklist

- [x] An untrusted external controller is rejected before receiving a session.
- [x] Connection and session lookup use the same authorization policy.
- [x] Custom commands repeat the authorization check as defense in depth.
- [x] Only an app-package controller receives or may invoke the load-session and stop-playback custom commands.
- [x] The load command accepts an opaque session ID, not a path or URI.
- [x] The session activity uses an immutable `PendingIntent`.
- [x] The instrumented cross-package controller test verifies that an untrusted test-package identity cannot connect to the exported session service.

## Privileged and sensitive permissions

| Permission group | Purpose | Principal risk / required control |
| --- | --- | --- |
| `MANAGE_EXTERNAL_STORAGE`, legacy `WRITE_EXTERNAL_STORAGE` | User-directed file management across shared storage. | Broad file visibility and mutation. Gate destructive actions behind explicit UI, validate paths, and preserve recycle/conflict handling. |
| `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` | App Manager inventory and optional usage information. | Package and behavioral metadata are sensitive. Keep local, minimize retention, and do not transmit. Usage access remains a separate user-granted special access. |
| `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` | User-confirmed APK installation and app removal. | Never bypass Android confirmation; validate package artifacts and content URIs before launching platform flows. |
| Bluetooth, Nearby Wi-Fi, local-network, Wi-Fi state and multicast permissions | Nearby transfer, discovery, and local sharing. | Require runtime consent where applicable, authenticate peers, bound all protocol fields and streams, and stop advertising/discovery promptly. |
| `INTERNET`, network-state permissions | FTP, SFTP, SMB, rclone, licensing, and local sharing. | Cleartext is disabled globally; provider exceptions must be deliberate. Protect credentials, verify host identity where supported, and redact logs. |
| Foreground service permissions (`DATA_SYNC`, `CONNECTED_DEVICE`, `MEDIA_PLAYBACK`) | Durable file jobs, transfers, sharing, authorization, and playback. | Start only for user-visible work, use the matching service type, maintain notifications, and handle platform timeouts/cancellation. |
| `POST_NOTIFICATIONS`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | Job visibility, active-operation continuity, and schedule recovery. | Hold wake locks only while necessary; restore schedules rather than silently starting unrelated work at boot. |
| Launcher shortcut install permission | Compatibility shortcut creation. | Only create shortcuts after an explicit user action and use immutable intents where possible. |

### Complete requested-permission index

The main manifest currently requests the following permissions; SDK bounds and
flags remain authoritative in `AndroidManifest.xml`:

- Network and Wi-Fi: `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
  `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `INTERNET`, and
  `ACCESS_LOCAL_NETWORK`.
- Nearby and Bluetooth: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_ADVERTISE`,
  `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `ACCESS_FINE_LOCATION`, and
  `NEARBY_WIFI_DEVICES`.
- Storage: `MANAGE_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE`.
- Foreground execution: `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- Packages and usage: `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES`,
  `REQUEST_DELETE_PACKAGES`, and `REQUEST_INSTALL_PACKAGES`.
- Lifecycle and user visibility: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`,
  and `WAKE_LOCK`.
- Launcher integration: `INSTALL_SHORTCUT`
  (`com.android.launcher.permission.INSTALL_SHORTCUT`).

## Review triggers

Update this document and its tests when any of the following changes:

1. An `android:exported` value or intent filter.
2. A requested permission, foreground-service type, or file-provider path.
3. A Media3 command or controller authorization rule.
4. A public intent extra, URI scheme, or accepted MIME type.
5. Backup, cleartext-traffic, or network-security configuration.
