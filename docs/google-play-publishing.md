# Google Play production publishing

This runbook covers the release-upload identity and the first Google Play upload for WizeFiles.
Purchase verification uses a different service account and is documented in `play-billing.md`.

## Identity separation

| Purpose | Service account | Permissions |
| --- | --- | --- |
| Verify purchases from Cloud Run | `wizefiles-play-backend@wize-soft-production.iam.gserviceaccount.com` | View financial data and manage orders/subscriptions for WizeFiles |
| Publish Android releases from GitHub | `wizefiles-play-publisher@wize-soft-production.iam.gserviceaccount.com` | Release WizeFiles to testing tracks and production |

Do not give the publisher account purchase-verification permissions and do not give the backend
account release permissions.

## One-time Google Cloud setup

Run the repository's `cloud/wize.ps1` from PowerShell and choose **Resume** for
`wize-soft-production`. The script is idempotent: it reuses the deployed backend and enabled signing
key, then creates or updates:

- the dedicated `wizefiles-play-publisher` service account;
- the required IAM, Service Account Credentials, Security Token Service, and Resource Manager APIs;
- the `github-actions` workload identity pool;
- the `wizefiles` OIDC provider;
- the `roles/iam.workloadIdentityUser` binding on the publisher account.

The OIDC trust accepts only GitHub repository ID `1230677293`, owner ID `1689896`, and
`refs/heads/main`. Numeric IDs prevent a deleted or renamed repository from being impersonated by a
new repository that later claims the same name.

The setup summary prints these non-secret values:

```text
GCP_WORKLOAD_IDENTITY_PROVIDER=projects/532189954619/locations/global/workloadIdentityPools/github-actions/providers/wizefiles
GOOGLE_PLAY_PUBLISHER_SERVICE_ACCOUNT=wizefiles-play-publisher@wize-soft-production.iam.gserviceaccount.com
```

No service-account JSON key is created.

## Play Console publisher access

After `com.wisso.wizefiles` exists in Play Console:

1. Open the developer account's **Users and permissions** page.
2. Invite `wizefiles-play-publisher@wize-soft-production.iam.gserviceaccount.com`.
3. Under **App permissions**, add only WizeFiles.
4. Grant **View app information (read only)**.
5. Grant **Release apps to testing tracks**.
6. Grant **Release to production, exclude devices and use Play app signing** only when production
   publishing is required.
7. Do not grant financial data, order management, admin, policy, or store-presence permissions.

## GitHub environment

Create a protected GitHub Actions environment named `google-play-production`. Restrict deployment
branches to `main` and, if available for the repository plan, require an owner review.

Add these environment variables:

| Variable | Value |
| --- | --- |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | Full provider name printed by `wize.ps1` |
| `GOOGLE_PLAY_PUBLISHER_SERVICE_ACCOUNT` | `wizefiles-play-publisher@wize-soft-production.iam.gserviceaccount.com` |
| `WIZEFILES_LICENSE_API_BASE_URL` | `https://wizefiles-license-api-uckg2rb5pq-ew.a.run.app` |
| `WIZEFILES_LICENSE_KEY_ID` | `wizefiles-prod-2026-01` |
| `WIZEFILES_LICENSE_PUBLIC_KEY_X509_BASE64` | Single-line production public key |

Keep the existing Android signing secrets available to this environment:

- `APP_KEYSTORE`
- `KEY_ALIAS`
- `KEY_STORE_PASSWORD`
- `KEY_PASSWORD`

`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` is no longer used and should be deleted after the OIDC workflow
has been validated.

## First Play upload

Google Play's publishing API cannot create the initial app package. Perform the first upload manually:

1. Run **Google Play Release** from the `main` branch.
2. Set **Upload to Play** to `false`.
3. Download the `app-release-signed-aab` workflow artifact.
4. In Play Console, create WizeFiles with package `com.wisso.wizefiles`.
5. Open **Testing > Internal testing**, create a release, upload the signed AAB, and complete the
   required Play Console declarations.
6. Save and roll out the internal release.
7. Invite the publisher service account and grant the permissions listed above.

Every later release must increment `appVersionCode` in `app/build.gradle`. The workflow uses the
hardcoded `appVersionCode` and `appVersionName`; Git tags do not control Android versioning.

## Automated uploads

After the first manual upload and publisher invitation:

1. Run **Google Play Release** from `main`.
2. Leave **Upload to Play** enabled.
3. Choose the target track.
4. Use `completed` for an immediate track rollout or `draft` to finish it in Play Console.
5. Leave **Keep changes pending manual review** enabled until the full publishing flow has been
   validated. Submit those changes from **Publishing overview** in Play Console.

The workflow authenticates using GitHub OIDC, signs the AAB, preserves the signed artifact, uploads
the R8 mapping file, serializes Play edits, and submits the release with the current `tracks` action
input. Staged production rollouts are intentionally excluded until the workflow also collects and
validates a `userFraction`.
