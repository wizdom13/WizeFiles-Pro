# WizeFiles Google Play Billing contract

WizeFiles release builds use Google Play Billing Library 9.1.0 to discover localized products,
launch checkout, restore purchases, and acknowledge completed purchases. Google Play purchase state
is never the Pro entitlement authority. The Wize Soft backend verifies every purchase with the
Google Play Developer API and returns the Ed25519-signed license document defined in
`license-verification.md`.

## Play products

| Product ID | Play type | Purpose |
| --- | --- | --- |
| `wizefiles_pro_lifetime` | One-time, non-consumable | Permanent Pro purchase |
| `wizefiles_pro` | Subscription | Monthly and yearly base plans and their eligible offers |

Product names, descriptions, prices, billing periods, base plans, and eligible trial offers are
always read from Google Play. The app never hardcodes display prices. Product IDs are intentionally
centralized in `BillingProducts` because Play product IDs cannot be renamed or reused.

## Purchase processing order

1. Connect to Google Play with automatic service reconnection and pending one-time purchases
   enabled.
2. Query both product types and active purchases whenever the billing service connects or the app
   returns to the foreground.
3. Ignore unknown products and purchases with an unspecified state.
4. Surface pending purchases without granting Pro or acknowledging them.
5. Send each completed known product's purchase token, product type, package name, app version, and
   encrypted installation binding directly to the HTTPS Wize Soft backend.
6. Let the backend verify the purchase with Google Play and return a signed license document.
7. Accept Pro only if the existing pinned-key verifier accepts that signed document.
8. Acknowledge the completed purchase after verified entitlement delivery. A failed acknowledgment
   is retried the next time Google Play returns the still-unacknowledged purchase.

Purchase tokens are not cached, written to logs, placed in analytics, or stored in the signed-license
cache. An SHA-256 digest of the installation binding is supplied to Google Play as the obfuscated
profile identifier; the raw installation ID remains between the app and Wize Soft backend.

## Backend endpoint

Release builds receive `WIZEFILES_LICENSE_API_BASE_URL` at build time. It must be an HTTPS origin or
base path without credentials, query, or fragment. The app posts to:

```text
POST {base_url}/v1/google-play/licenses:verify
```

Request schema version 1:

```json
{
  "schema_version": 1,
  "store": "google_play",
  "package_name": "com.wisso.wizefiles",
  "installation_id": "installation UUID",
  "product_id": "wizefiles_pro_lifetime",
  "product_type": "one_time",
  "purchase_token": "opaque Play token",
  "app_version_code": 603
}
```

Success response:

```json
{
  "license_document": "serialized signed document"
}
```

Missing endpoint configuration, TLS/network failures, redirects, non-success responses, oversized
responses, malformed JSON, and rejected signed documents all fail closed. The backend must also
consume Real-time Developer Notifications and use Google Play Developer API state as its source of
truth for renewals, cancellation, grace period, account hold, pause, expiry, refunds, and revocation.

## Production publishing

The Google Play release identity is intentionally separate from the purchase-verification backend.
See `google-play-publishing.md` for the keyless GitHub OIDC setup, first manual AAB upload, Play
Console publisher permissions, protected GitHub environment, and automated release procedure.

## Variant and PR boundaries

- Release builds contain Play Billing and require the production package `com.wisso.wizefiles`.
- GitHub beta and debug builds expose the unsupported billing controller and never launch real sales.
- This PR provides the billing engine and observable contract only. PR 4 owns the purchase/paywall
  UI and user-facing messaging. PR 5 owns all actual feature gates.

