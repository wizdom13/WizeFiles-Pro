# WizeFiles Pro feature gates

WizeFiles uses the signed `PRO` entitlement as the only authority for paid capabilities. Billing
products never grant a feature directly.

Every implemented Pro workflow is checked twice:

1. at its user-facing entry point, which opens the WizeFiles Pro screen when access is unavailable;
2. at the service, worker, repository, or operation boundary that performs the work.

## Free guarantees

Free users retain local and SAF file management, tabs, viewers, archive browsing and extraction,
signature and certificate verification, Transfer Center history and recovery, one saved remote
connection, one vault, and single-app App Manager actions. Existing files and vault data are never
made inaccessible when Pro ends.

## Enforced capabilities

- dual-pane browsing and cross-pane drag-and-drop
- sync profiles and scheduled sync/backup execution
- APK, AAB, APKS, XAPK, and APKM signing operations (verification stays free)
- archive mutation (browsing, extraction, and basic creation stay free)
- batch App Manager operations (single-app actions stay free)
- additional remote/cloud connections and rclone power-user configuration
- additional vault creation
- root-backed file access
- built-in HTTP/FTP local sharing

Beta builds retain their existing time-limited Pro entitlement. Debug builds retain the
non-persistent Free/Pro override. Catalogue entries without a shipped execution path remain dormant
until their feature is implemented.
