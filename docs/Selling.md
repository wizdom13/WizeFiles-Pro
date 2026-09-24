Keep WizeFiles Free a genuinely capable, ad-free file manager and reserve advanced power-user workflows for WizeFiles Pro.

Because WizeFiles is primarily an offline utility, the main purchase should be a lifetime Pro unlock. A subscription can be offered as an alternative, but should not be the only option.

## Recommended Free/Pro boundary

| Area                 | WizeFiles Free                                                                          | WizeFiles Pro                                                                              |
| -------------------- | --------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Core file management | Local/SAF browsing, copy, move, rename, delete, recycle bin, search, sorting, bookmarks | —                                                                                          |
| Browser productivity | Multi-tab, keyboard shortcuts, context menus                                            | Dual-pane, cross-pane drag-and-drop and pane synchronization                               |
| Transfer Center      | Progress, conflict handling, recovery and basic history                                 | Advanced automation and saved workflows                                                    |
| Network and cloud    | One saved remote/cloud connection for evaluation                                        | Unlimited SMB, SFTP, FTP, WebDAV and cloud/rclone connections; rclone power-user mode      |
| Sync and backup      | Manual copy operations                                                                  | Sync/backup profiles, scheduled operations and reusable presets                            |
| Internal viewers     | Image, video, audio, PDF, ebook, text and specialist document viewing                   | Keep these free                                                                            |
| Archives             | Open, browse, extract and basic ZIP creation                                            | Advanced archive creation/editing, encryption and specialist archive/disk-image operations |
| App Manager          | Inspect, open, uninstall, share and single-app backup                                   | Batch APK/APKS backup and advanced package workflows                                       |
| Package signing      | Signature and certificate verification                                                  | APK, AAB, APKS, XAPK and APKM signing                                                      |
| Security             | App lock, biometric protection and one Vault                                            | Multiple vaults and advanced vault automation                                              |
| Root access          | Normal Android storage access                                                           | Root/Shizuku power tools                                                                   |
| Device sharing       | Android sharing and basic Nearby transfer                                               | Built-in servers and advanced Local Share controls                                         |
| Storage Cleaner      | Analysis and manual cleanup                                                             | Saved cleanup rules, batch actions and future automatic cleanup                            |
| Appearance           | Standard themes, dark mode and accessibility                                            | Premium themes, colors and advanced appearance options                                     |

The strongest Pro selling points are:

* Dual-pane productivity
* Unlimited cloud/network connections
* Sync and backup profiles
* Root/Shizuku
* Package signing
* Advanced archive editing
* Batch App Manager operations
* Built-in servers
* Multiple vaults

## Features that should never be paywalled

WizeFiles should never restrict:

* Access to, opening or exporting the user’s own files
* Core copy/move/delete reliability
* Conflict handling and Transfer Center recovery
* Recycle bin and delete confirmations
* Signature verification
* Security and compatibility updates
* Decrypting or exporting an existing Vault after Pro expires
* Access to files already created using a Pro feature

If a subscription expires, disable creating new Pro jobs—not access to the user’s data.

## Recommended purchase products

Use one internal entitlement named simply `PRO`, obtained through either product:

| Play product                      | Type                       | Suggested price |
| --------------------------------- | -------------------------- | --------------: |
| `wizefiles_pro_lifetime`          | One-time, non-consumable   |        US$29.99 |
| `wizefiles_pro` yearly base plan  | Auto-renewing subscription |   US$14.99/year |
| `wizefiles_pro` monthly base plan | Auto-renewing subscription |   US$2.49/month |

An introductory seven-day trial can be attached to the annual subscription. Google Play supplies localized pricing, so the app must display the price returned by Play rather than hardcoding dollar amounts.

Lifetime should always take precedence. A lifetime buyer should never be asked to subscribe later.

Google requires subscriptions to deliver sustained value throughout their duration. Pro access and ongoing Pro improvements can provide that value, but a lifetime option is still the better fit for an offline file manager. [Google Play subscription policy](https://support.google.com/googleplay/android-developer/answer/9900533)

## How the payment works

1. In Play Console, Wize Soft creates a payments profile.
2. Create the lifetime one-time product.
3. Create the `wizefiles_pro` subscription, then its monthly/yearly base plans and any trial offer. Product IDs must be chosen carefully because they cannot later be changed or reused. [Play subscription configuration](https://support.google.com/googleplay/android-developer/answer/140504)
4. WizeFiles connects to Google Play through the Play Billing Library.
5. The app retrieves product information and localized prices using `queryProductDetailsAsync()`.
6. When the user taps Buy, Google Play shows its own secure checkout through `launchBillingFlow()`.
7. Google handles the payment method, confirmation and receipt.
8. WizeFiles receives a purchase token.
9. The token is sent to a Wize Soft backend and verified with the Google Play Developer API.
10. Only a verified purchase in `PURCHASED` state grants Pro.
11. The purchase must be acknowledged within three days or Google can automatically refund it and revoke the entitlement.
12. On subsequent launches, `queryPurchasesAsync()` restores purchases on other devices using the same Play account. [Official billing integration flow](https://developer.android.com/google/play/billing/integrate)

For subscriptions, the backend should receive Real-time Developer Notifications and query Google’s subscription API:

* Active: Pro enabled
* Cancelled: Pro remains enabled until the paid expiration date
* Grace period: keep Pro enabled
* Account hold or paused: temporarily disable new Pro operations
* Expired: return to Free
* Renewed: extend the entitlement

Google describes the subscription API as the source of truth for these transitions. [Subscription lifecycle](https://developer.android.com/google/play/billing/lifecycle/subscriptions)

## Required WizeFiles architecture

The current [app/build.gradle](https://github.com/wizdom13/WizeFiles/blob/main/app/build.gradle) has no Play Billing dependency or entitlement system. Billing should therefore be introduced as an isolated foundation.

Recommended structure:

```text
BillingRepository
    ├── Connects to Google Play
    ├── Loads products and localized prices
    ├── Starts purchases
    └── Restores purchases

EntitlementRepository
    ├── Verifies backend state
    ├── Maintains offline-safe cached state
    └── Exposes one isPro entitlement

ProFeatureGate
    ├── DUAL_PANE
    ├── UNLIMITED_REMOTE_CONNECTIONS
    ├── SYNC_PROFILES
    ├── ROOT_ACCESS
    ├── PACKAGE_SIGNING
    └── ...
```

Feature checks should not be scattered throughout `FileListFragment`, which is already a major integration hotspot. Gate each feature at both:

* Its user-facing entry point
* Its service/job execution boundary

That prevents deep links, restored activities or queued Transfer Center jobs from bypassing the entitlement check.

## Important Beta limitation

The repository currently uses:

```text
Release: com.wisso.wizefiles
Beta:   com.wisso.wizefiles.beta
Debug:  com.wisso.wizefiles.debug
```

Google Play products are tied to the application package. Purchases made for `com.wisso.wizefiles` will not automatically belong to the separate `.beta` package.

Therefore:

* Keep GitHub WizeFiles Beta fully Pro-enabled and time-limited for testing.
* Do not sell purchases from the `.beta` build.
* Test real billing through a Play internal/closed-testing build using the production package.
* If cross-store licensing is added later, it will require a Wize Soft account and backend entitlement system.

## Store fees and distribution

A Play-distributed WizeFiles build generally must use Google Play Billing when selling in-app digital functionality, unless Wize Soft enrolls in an applicable regional alternative-billing program. A separately distributed website/GitHub build can use another licensing system. [Google Play payments policy](https://support.google.com/googleplay/android-developer/answer/10281818)

Do not assume a universal 15% fee in the financial plan. Google began rolling out a revised structure in the US, UK and EEA on June 30, 2026, while other markets and program enrollments can use different tiers. Use the current Play Console terms for Wize Soft’s account. [Current Play service fees](https://support.google.com/googleplay/android-developer/answer/112622)

The best implementation would be a sequential program: entitlement foundation, secure backend verification, Play Billing integration, Pro purchase UI, then feature gates and full purchase-lifecycle testing.
