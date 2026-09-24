# WizeFiles Pro license verification contract

WizeFiles never treats a local `isPro` value or a Google Play purchase object as the entitlement
authority. A Wize Soft backend verifies the purchase with Google Play and returns a short,
backend-signed license document. The app pins the corresponding Ed25519 public key and can verify
the cached document while offline.

## Signed document

The outer document is JSON:

```json
{
  "key_id": "wize-2026-01",
  "payload": "<base64url raw UTF-8 payload bytes>",
  "signature": "<base64url Ed25519 signature over the raw payload bytes>"
}
```

The signed payload contains:

```json
{
  "schema_version": 1,
  "license_id": "opaque backend identifier",
  "package_name": "com.wisso.wizefiles",
  "installation_id": "app-generated UUID",
  "entitlements": ["PRO"],
  "issued_at_ms": 1800000000000,
  "not_before_ms": 1800000000000,
  "refresh_after_ms": 1800086400000,
  "valid_until_ms": 1802592000000,
  "server_time_ms": 1800000000000
}
```

The backend signs the exact payload bytes before base64url encoding them. The algorithm is fixed to
Ed25519; it is not selected by an untrusted field in the document.

`valid_until_ms` is the hard offline lease boundary. For a lifetime purchase the backend can issue
a rolling lease with a long refresh window; for a subscription it must never exceed the verified
paid-through time plus any policy-approved grace period. `refresh_after_ms` tells the future billing
layer when to refresh proactively but never extends the signed validity window.

A verified response with an empty `entitlements` array revokes Pro locally. A cancelled
subscription remains Pro until the backend stops including `PRO` or the signed validity window
ends.

## Trust and storage rules

- The backend private key never enters the app, repository, CI logs, or Play Console metadata.
- Release builds receive the public key ID and X.509-encoded public key through
  `WIZEFILES_LICENSE_KEY_ID` and `WIZEFILES_LICENSE_PUBLIC_KEY_X509_BASE64` at build time.
- The public key is not secret. Production workflows receive it from the protected
  `google-play-production` GitHub Actions environment so rotation does not require committing key
  material to source.
- The app binds each license to both the release package and an encrypted, installation-scoped UUID.
- Only the signed document and last trusted backend time are cached. Purchase tokens and account
  identifiers are not written to this cache.
- The cache uses Android Keystore-backed encrypted preferences and is excluded from backup by the
  app's existing backup policy.
- Unknown keys, invalid signatures, malformed claims, binding mismatches, expiry, rollback, and
  storage failures all fail closed to Free.
- `key_id` enables overlapping public keys during rotation. A future release should carry old and
  new public keys until all valid offline leases signed by the old key have expired.

## Production build provisioning

Create a protected GitHub Actions environment named `google-play-production` and define these
environment variables:

| Variable | Production value |
| --- | --- |
| `WIZEFILES_LICENSE_API_BASE_URL` | `https://wizefiles-license-api-uckg2rb5pq-ew.a.run.app` |
| `WIZEFILES_LICENSE_KEY_ID` | `wizefiles-prod-2026-01` |
| `WIZEFILES_LICENSE_PUBLIC_KEY_X509_BASE64` | Single-line contents of `wizefiles-license-public-x509-base64.txt` |

The expected SHA-256 fingerprint of the decoded X.509 public key is
`595f2fea65882e8d253133f28b83c5afabcddd392846a8640f8a3cf045dcf8e0`. Both production workflows validate the URL, key ID, Base64 encoding, and public
key fingerprint before invoking Gradle. A missing or mismatched value stops the release so an
unconfigured or incorrectly pinned production build cannot be published.

## Billing integration

The release-only Play Billing layer submits a completed known product's purchase token and the
installation ID directly to the HTTPS backend, then passes the returned signed document to
`AppEntitlements.licenseController`. The purchase token is never stored in this cache or treated as
an entitlement. See `play-billing.md` for the product, endpoint, acknowledgment, and variant
contracts. Purchase UI and actual feature gates remain later PRs.
