# APK signing test corpus

These bounded fixtures exercise public APK signature schemes without containing usable application
code or a private key.

| File | Schemes | SHA-256 |
|---|---|---|
| `unsigned.apk` | none | `8ddf9a3b2aa151c7d06440654ef764065f138705af0ed67340aa18375d549ce2` |
| `v1.apk` | v1 | `673b5725539040bcfe5c8974b0ed7a810c0bf062fea4b8dbb3832837b4d78650` |
| `v2.apk` | v2 | `4bf55a414491cdf53248f85b03aebadfda1fb4cfccebf5e3dc4b5c512b4f7fc5` |
| `v3.apk` | v2 + v3 | `81ae0639ad93c32d18c6be73c806401c51490a0237ccbaae8ca0a8fa80e6c84d` |
| `v4.apk` | v2 + v3 | `81ae0639ad93c32d18c6be73c806401c51490a0237ccbaae8ca0a8fa80e6c84d` |
| `v4.apk.idsig` | detached v4 | `e22a6f683304560a5140f787e8fea68d93ca41e55e934dbfc50878867e1308dc` |

The v3 and v4 APK hashes deliberately match: v4 is stored only in the companion idsig. The fixtures
were created with the pinned apksig-android 4.4.0 API and are independently checked by Android Build
Tools 36.0.0 in CI.
