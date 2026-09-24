# Google Play production publishing

This runbook covers stable WizeFiles publishing to Google Play.

WizeFiles 1.0.0 and later do not use Google Play Billing, purchase verification, subscription
entitlements, or a license backend. Google Play is only a distribution channel.

## Publisher identity

Use the dedicated publisher service account:

`wizefiles-play-publisher@wize-soft-production.iam.gserviceaccount.com`

Grant only the permissions needed to read app information and publish WizeFiles releases. Do not
grant financial-data or order-management permissions because WizeFiles has no in-app purchases or
subscriptions.

The GitHub workflow uses workload identity federation rather than a downloaded service-account key.

## GitHub environment

Create a protected GitHub Actions environment named `google-play-production` and restrict
deployments to `main`.

Configure these non-secret environment variables:

| Variable | Value |
| --- | --- |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/532189954619/locations/global/workloadIdentityPools/github-actions/providers/wizefiles` |
| `GOOGLE_PLAY_PUBLISHER_SERVICE_ACCOUNT` | `wizefiles-play-publisher@wize-soft-production.iam.gserviceaccount.com` |

Keep the Android signing secrets available to this environment:

- `APP_KEYSTORE`
- `KEY_ALIAS`
- `KEY_STORE_PASSWORD`
- `KEY_PASSWORD`

For Google Play signing, keep the corresponding `*_GOOGLE` secrets used by
`.github/workflows/google-play.yml`.

## First Play upload

Google Play's publishing API cannot create the initial app package. If the package has not already
been created:

1. Run **Google Play Release** from `main`.
2. Set **Upload to Play** to `false`.
3. Download the signed AAB workflow artifact.
4. Create WizeFiles in Play Console with package `com.wisso.wizefiles`.
5. Upload the signed AAB to Internal testing and complete the required Play declarations.
6. Invite the publisher service account and grant the minimum release permissions described above.

Every later release must increment `appVersionCode` and `appVersionName` in
`app/build.gradle`. Git tags label releases but do not override Android version metadata.

## Automated uploads

After the initial package exists:

1. Run **Google Play Release** from `main`.
2. Leave **Upload to Play** enabled.
3. Choose the target track.
4. Use `completed` for rollout or `draft` to finish in Play Console.
5. Keep changes pending manual review when appropriate.

The workflow authenticates through GitHub OIDC, builds and signs the release AAB, keeps the signed
artifact, uploads the R8 mapping file, and publishes the selected track.
