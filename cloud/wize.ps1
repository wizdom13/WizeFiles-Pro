#requires -Version 5.1

[CmdletBinding()]
param(
    [string]$GoogleAccount,
    [string]$ProjectId,
    [string]$BillingAccountId,
    [string]$BackendSourcePath,
    [string]$Region = "europe-west1",
    [string]$FirestoreLocation = "eur3",
    [string]$ServiceName = "wizefiles-license-api",
    [string]$LicenseKeyId = "wizefiles-prod-2026-01",
    [string]$GitHubRepositoryId = "1230677293",
    [string]$GitHubOwnerId = "1689896",
    [string]$WorkloadIdentityPoolId = "github-actions",
    [string]$WorkloadIdentityProviderId = "wizefiles",
    [string]$OpenSslDirectory = "C:\Program Files\OpenSSL-Win64\bin",
    [ValidateSet("Prompt", "Resume", "Replace")]
    [string]$ExistingProjectAction = "Prompt"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:CreatedProjectId = $null
$script:DeletedProjectId = $null
$script:BackendDeployed = $false
$script:ServiceUrl = $null

function Write-Step {
    param([Parameter(Mandatory)][string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok {
    param([Parameter(Mandatory)][string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-WarnMessage {
    param([Parameter(Mandatory)][string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Invoke-GcloudNative {
    param(
        [Parameter(Mandatory)][string[]]$Arguments
    )

    # Windows PowerShell 5.1 converts native stderr into ErrorRecord objects.
    # gcloud writes some successful status messages to stderr, so temporarily
    # avoid promoting those messages to terminating PowerShell errors. The
    # native process exit code remains the authoritative success/failure signal.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $commandOutput = @(& gcloud @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $outputText = ($commandOutput | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
    return [PSCustomObject]@{
        ExitCode = $exitCode
        Lines = @($commandOutput)
        Text = $outputText.Trim()
    }
}

function Invoke-Gcloud {
    param(
        [Parameter(Mandatory)][string[]]$Arguments,
        [switch]$Capture,
        [switch]$QuietDisplay
    )

    if (-not $QuietDisplay) {
        Write-Host ("gcloud " + ($Arguments -join " ")) -ForegroundColor DarkGray
    }

    $result = Invoke-GcloudNative -Arguments $Arguments

    if ($Capture) {
        if ($result.ExitCode -ne 0) {
            throw "gcloud failed with exit code $($result.ExitCode).`n$($result.Text)"
        }

        return $result.Text
    }

    foreach ($line in $result.Lines) {
        Write-Host $line.ToString()
    }
    if ($result.ExitCode -ne 0) {
        throw "gcloud failed with exit code $($result.ExitCode)."
    }
}

function Test-ProjectIdSyntax {
    param([Parameter(Mandatory)][string]$Value)
    return $Value -match '^[a-z][a-z0-9-]{4,28}[a-z0-9]$'
}

function Read-ValidProjectId {
    param(
        [Parameter(Mandatory)][string]$Prompt,
        [string]$DisallowedId
    )

    while ($true) {
        $candidate = (Read-Host $Prompt).Trim()

        if (-not (Test-ProjectIdSyntax -Value $candidate)) {
            Write-WarnMessage "A Project ID must be 6-30 characters, start with a lowercase letter, contain only lowercase letters, digits or hyphens, and end with a letter or digit."
            continue
        }

        if ($DisallowedId -and $candidate -eq $DisallowedId) {
            Write-WarnMessage "A project pending deletion cannot be recreated immediately with the same ID. Enter a different Project ID."
            continue
        }

        return $candidate
    }
}

function Test-AccessibleProject {
    param([Parameter(Mandatory)][string]$Id)

    $result = Invoke-GcloudNative -Arguments @(
        "projects", "describe", $Id,
        "--format=value(projectId)",
        "--quiet"
    )
    return $result.ExitCode -eq 0
}

function Test-ServiceAccount {
    param(
        [Parameter(Mandatory)][string]$Email,
        [Parameter(Mandatory)][string]$TargetProjectId
    )

    $result = Invoke-GcloudNative -Arguments @(
        "iam", "service-accounts", "describe", $Email,
        "--project=$TargetProjectId",
        "--quiet"
    )
    return $result.ExitCode -eq 0
}

function Test-Secret {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$TargetProjectId
    )

    $result = Invoke-GcloudNative -Arguments @(
        "secrets", "describe", $Name,
        "--project=$TargetProjectId",
        "--quiet"
    )
    return $result.ExitCode -eq 0
}

function Test-PubSubTopic {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$TargetProjectId
    )

    $result = Invoke-GcloudNative -Arguments @(
        "pubsub", "topics", "describe", $Name,
        "--project=$TargetProjectId",
        "--quiet"
    )
    return $result.ExitCode -eq 0
}

function Test-PubSubSubscription {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$TargetProjectId
    )

    $result = Invoke-GcloudNative -Arguments @(
        "pubsub", "subscriptions", "describe", $Name,
        "--project=$TargetProjectId",
        "--quiet"
    )
    return $result.ExitCode -eq 0
}

function Test-WorkloadIdentityPool {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$TargetProjectId
    )

    $result = Invoke-GcloudNative -Arguments @(
        "iam", "workload-identity-pools", "describe", $Name,
        "--project=$TargetProjectId",
        "--location=global",
        "--quiet"
    )
    return $result.ExitCode -eq 0
}

function Test-WorkloadIdentityProvider {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$PoolName,
        [Parameter(Mandatory)][string]$TargetProjectId
    )

    $result = Invoke-GcloudNative -Arguments @(
        "iam", "workload-identity-pools", "providers", "describe", $Name,
        "--project=$TargetProjectId",
        "--location=global",
        "--workload-identity-pool=$PoolName",
        "--quiet"
    )
    return $result.ExitCode -eq 0
}

function Test-FirestoreDefaultDatabase {
    param([Parameter(Mandatory)][string]$TargetProjectId)

    $result = Invoke-GcloudNative -Arguments @(
        "firestore", "databases", "describe",
        "--database=(default)",
        "--project=$TargetProjectId",
        "--quiet"
    )
    return $result.ExitCode -eq 0
}

function Select-BillingAccount {
    param([string]$RequestedBillingAccountId)

    $billingJson = Invoke-Gcloud -Arguments @(
        "billing", "accounts", "list",
        "--filter=open=true",
        "--format=json"
    ) -Capture

    $billingAccounts = @()
    if ($billingJson) {
        $billingAccounts = @($billingJson | ConvertFrom-Json)
    }

    if ($billingAccounts.Count -eq 0) {
        throw "No open Cloud Billing account is available to the active Google account. Create one at https://console.cloud.google.com/billing and rerun this script."
    }

    if ($RequestedBillingAccountId) {
        $selected = $billingAccounts | Where-Object {
            ($_.name -replace '^billingAccounts/', '') -eq $RequestedBillingAccountId
        } | Select-Object -First 1

        if (-not $selected) {
            throw "Billing account '$RequestedBillingAccountId' is not open or is not accessible to the active account."
        }

        return $RequestedBillingAccountId
    }

    Write-Host "Available open billing accounts:" -ForegroundColor Cyan
    for ($index = 0; $index -lt $billingAccounts.Count; $index++) {
        $accountId = $billingAccounts[$index].name -replace '^billingAccounts/', ''
        Write-Host ("  [{0}] {1} ({2})" -f ($index + 1), $billingAccounts[$index].displayName, $accountId)
    }

    while ($true) {
        $selectionText = Read-Host "Select the billing account number"
        $selection = 0
        if ([int]::TryParse($selectionText, [ref]$selection) -and
            $selection -ge 1 -and
            $selection -le $billingAccounts.Count) {
            return ($billingAccounts[$selection - 1].name -replace '^billingAccounts/', '')
        }
        Write-WarnMessage "Enter a number from 1 to $($billingAccounts.Count)."
    }
}

function Select-GcloudAccount {
    param([string]$RequestedGoogleAccount)

    $accountsJson = Invoke-Gcloud -Arguments @(
        "auth", "list",
        "--format=json"
    ) -Capture

    $knownAccounts = @()
    if ($accountsJson) {
        $knownAccounts = @($accountsJson | ConvertFrom-Json)
    }

    $selectedEmail = $RequestedGoogleAccount

    if (-not $selectedEmail) {
        Write-Host "Authenticated Google accounts:" -ForegroundColor Cyan
        for ($index = 0; $index -lt $knownAccounts.Count; $index++) {
            $activeMarker = if ($knownAccounts[$index].status -eq "ACTIVE") { " [active]" } else { "" }
            Write-Host ("  [{0}] {1}{2}" -f ($index + 1), $knownAccounts[$index].account, $activeMarker)
        }
        Write-Host "  [N] Sign in with another Google account"

        while (-not $selectedEmail) {
            $selection = (Read-Host "Select an account number or N").Trim()

            if ($selection -match '^[Nn]$') {
                $selectedEmail = (Read-Host "Enter the Google account email address").Trim()
                break
            }

            $selectionNumber = 0
            if ([int]::TryParse($selection, [ref]$selectionNumber) -and
                $selectionNumber -ge 1 -and
                $selectionNumber -le $knownAccounts.Count) {
                $selectedEmail = $knownAccounts[$selectionNumber - 1].account
                break
            }

            Write-WarnMessage "Choose a listed account number or enter N."
        }
    }

    if ($selectedEmail -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') {
        throw "'$selectedEmail' does not look like a valid Google account email address."
    }

    $knownMatch = $knownAccounts | Where-Object { $_.account -eq $selectedEmail } | Select-Object -First 1
    if (-not $knownMatch) {
        Write-WarnMessage "'$selectedEmail' is not authenticated in gcloud. A browser sign-in will open."
        Invoke-Gcloud -Arguments @("auth", "login", $selectedEmail)
    }

    Invoke-Gcloud -Arguments @("config", "set", "account", $selectedEmail)

    $confirmedAccount = Invoke-Gcloud -Arguments @(
        "auth", "list",
        "--filter=status:ACTIVE",
        "--format=value(account)"
    ) -Capture

    if ($confirmedAccount -ne $selectedEmail) {
        throw "The active gcloud account is '$confirmedAccount', not the selected account '$selectedEmail'."
    }

    return $confirmedAccount
}

function Grant-ProjectRole {
    param(
        [Parameter(Mandatory)][string]$TargetProjectId,
        [Parameter(Mandatory)][string]$Member,
        [Parameter(Mandatory)][string]$Role
    )

    Invoke-Gcloud -Arguments @(
        "projects", "add-iam-policy-binding", $TargetProjectId,
        "--member=$Member",
        "--role=$Role",
        "--condition=None",
        "--quiet"
    )
}

function New-ServiceAccountIfMissing {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Email,
        [Parameter(Mandatory)][string]$DisplayName,
        [Parameter(Mandatory)][string]$Description,
        [Parameter(Mandatory)][string]$TargetProjectId
    )

    if (Test-ServiceAccount -Email $Email -TargetProjectId $TargetProjectId) {
        Write-Ok "Service account already exists: $Email"
        return
    }

    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "create", $Name,
        "--project=$TargetProjectId",
        "--display-name=$DisplayName",
        "--description=$Description"
    )
    Write-Ok "Created service account: $Email"
}

function Try-NewGoogleCloudProject {
    param([Parameter(Mandatory)][string]$TargetProjectId)

    $arguments = @(
        "projects", "create", $TargetProjectId,
        "--name=Wize Soft Production"
    )
    Write-Host ("gcloud " + ($arguments -join " ")) -ForegroundColor DarkGray
    $result = Invoke-GcloudNative -Arguments $arguments

    foreach ($line in $result.Lines) {
        Write-Host $line.ToString()
    }

    return $result.ExitCode -eq 0
}

function Select-ExistingProjectAction {
    param([Parameter(Mandatory)][string]$RequestedAction)

    if ($RequestedAction -ne "Prompt") {
        return $RequestedAction
    }

    Write-Host "Choose what to do with this accessible project:" -ForegroundColor Cyan
    Write-Host "  [R] Resume/reuse it (recommended)"
    Write-Host "  [D] Delete it and create a replacement"
    Write-Host "  [Q] Quit without making more changes"

    while ($true) {
        $selection = (Read-Host "Select R, D, or Q").Trim().ToUpperInvariant()
        switch ($selection) {
            "R" { return "Resume" }
            "D" { return "Replace" }
            "Q" { throw "Setup cancelled. The existing project was not deleted." }
            default { Write-WarnMessage "Enter R to resume, D to replace, or Q to quit." }
        }
    }
}

function Resolve-BackendPath {
    param([string]$RequestedPath)

    $pathValue = $RequestedPath
    if (-not $pathValue) {
        $pathValue = Read-Host "Backend source directory (press Enter to provision infrastructure without deploying)"
    }

    if (-not $pathValue) {
        return $null
    }

    $resolvedPath = (Resolve-Path -LiteralPath $pathValue).Path
    $supportedFiles = @(
        "Dockerfile",
        "package.json",
        "pom.xml",
        "build.gradle",
        "build.gradle.kts",
        "requirements.txt",
        "go.mod"
    )

    $sourceRecognized = $false
    foreach ($file in $supportedFiles) {
        if (Test-Path -LiteralPath (Join-Path $resolvedPath $file) -PathType Leaf) {
            $sourceRecognized = $true
            break
        }
    }

    if (-not $sourceRecognized) {
        throw "The backend directory '$resolvedPath' does not contain a Dockerfile or a recognized build file. Infrastructure can be provisioned without deployment by leaving the path blank."
    }

    return $resolvedPath
}

try {
    Write-Host "WizeFiles Google Cloud production setup" -ForegroundColor White
    Write-Host "This script can create and delete billable Google Cloud resources." -ForegroundColor Yellow

    Write-Step "Checking local requirements before making cloud changes"

    if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
        throw "Google Cloud CLI (gcloud) is not installed or is not on PATH. Install it from https://cloud.google.com/sdk/docs/install-sdk."
    }

    $opensslExecutable = Join-Path $OpenSslDirectory "openssl.exe"
    if (-not (Test-Path -LiteralPath $opensslExecutable -PathType Leaf)) {
        throw "OpenSSL was not found at '$opensslExecutable'. Install Win64 OpenSSL there or rerun with -OpenSslDirectory '<path-to-bin>'."
    }

    $gcloudVersionJson = Invoke-Gcloud -Arguments @("version", "--format=json") -Capture -QuietDisplay
    $gcloudVersionObject = $gcloudVersionJson | ConvertFrom-Json
    $gcloudVersion = $gcloudVersionObject.'Google Cloud SDK'
    $opensslVersion = (& $opensslExecutable version 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "OpenSSL is installed but could not be executed."
    }

    Write-Ok "Google Cloud CLI detected: $gcloudVersion"
    Write-Ok "OpenSSL detected: $opensslVersion"

    $activeAccount = Select-GcloudAccount -RequestedGoogleAccount $GoogleAccount
    Write-Ok "Active account: $activeAccount"

    $selectedBillingAccountId = Select-BillingAccount -RequestedBillingAccountId $BillingAccountId
    Write-Ok "Billing account selected: $selectedBillingAccountId"

    $resolvedBackendPath = Resolve-BackendPath -RequestedPath $BackendSourcePath
    if ($resolvedBackendPath) {
        Write-Ok "Backend source validated: $resolvedBackendPath"
    }
    else {
        Write-WarnMessage "No backend source was supplied. Infrastructure will be created, but Cloud Run deployment and the RTDN push subscription will be skipped."
    }

    if (-not $ProjectId) {
        $ProjectId = Read-ValidProjectId -Prompt "Enter the Google Cloud Project ID to check"
    }
    elseif (-not (Test-ProjectIdSyntax -Value $ProjectId)) {
        throw "Invalid Project ID '$ProjectId'. Use 6-30 lowercase letters, digits or hyphens; start with a letter and end with a letter or digit."
    }

    if ($GitHubRepositoryId -notmatch '^\d+$' -or $GitHubOwnerId -notmatch '^\d+$') {
        throw "GitHubRepositoryId and GitHubOwnerId must be numeric GitHub IDs."
    }

    $targetProjectId = $ProjectId

    if (Test-AccessibleProject -Id $ProjectId) {
        Write-Step "Existing accessible project found"
        Invoke-Gcloud -Arguments @(
            "projects", "describe", $ProjectId,
            "--format=table(projectId,name,projectNumber,lifecycleState)"
        )

        $selectedProjectAction = Select-ExistingProjectAction -RequestedAction $ExistingProjectAction
        if ($selectedProjectAction -eq "Resume") {
            $targetProjectId = $ProjectId
            Write-Ok "Resuming setup in existing project: $targetProjectId"
        }
        else {
            Write-WarnMessage "Deleting this project schedules every resource in it for deletion. The Project ID normally cannot be reused immediately."
            Write-WarnMessage "Google may allow restoration during its recovery window, but you must not depend on recovery."

            $expectedConfirmation = "DELETE $ProjectId"
            $confirmation = Read-Host "Type exactly '$expectedConfirmation' to authorize deletion"
            if ($confirmation -cne $expectedConfirmation) {
                throw "Deletion was not confirmed. No project was deleted or created."
            }

            $replacementCreated = $false
            do {
                $replacementProjectId = Read-ValidProjectId `
                    -Prompt "Enter a NEW globally unique replacement Project ID" `
                    -DisallowedId $ProjectId

                if (Test-AccessibleProject -Id $replacementProjectId) {
                    Write-WarnMessage "You already have access to '$replacementProjectId'. Choose a new unused ID."
                    continue
                }

                Write-Step "Creating the replacement project before deleting the existing project"
                $replacementCreated = Try-NewGoogleCloudProject -TargetProjectId $replacementProjectId
                if (-not $replacementCreated) {
                    Write-WarnMessage "Project ID '$replacementProjectId' is unavailable or the selected account cannot create it. Choose another globally unique ID."
                }
            } while (-not $replacementCreated)

            $script:CreatedProjectId = $replacementProjectId
            $targetProjectId = $replacementProjectId
            Write-Ok "Replacement project created: $targetProjectId"

            Write-Step "Deleting the confirmed existing project"
            Invoke-Gcloud -Arguments @(
                "projects", "delete", $ProjectId,
                "--quiet"
            )
            $script:DeletedProjectId = $ProjectId
            Write-Ok "Project '$ProjectId' was scheduled for deletion."
        }
    }
    else {
        $projectCreated = $false
        $candidateProjectId = $ProjectId

        do {
            Write-Step "Creating Google Cloud project"
            Write-Host "The project was not accessible to this account. Creation will verify whether the ID is globally available."
            $projectCreated = Try-NewGoogleCloudProject -TargetProjectId $candidateProjectId

            if (-not $projectCreated) {
                Write-WarnMessage "Project ID '$candidateProjectId' is unavailable or the selected account cannot create it."
                $candidateProjectId = Read-ValidProjectId -Prompt "Enter a different globally unique Project ID"
            }
        } while (-not $projectCreated)

        $targetProjectId = $candidateProjectId
        $script:CreatedProjectId = $targetProjectId
        Write-Ok "Project created: $targetProjectId"
    }

    Write-Step "Selecting the new project and linking billing"
    Invoke-Gcloud -Arguments @("config", "set", "project", $targetProjectId)
    Invoke-Gcloud -Arguments @(
        "billing", "projects", "link", $targetProjectId,
        "--billing-account=$selectedBillingAccountId"
    )

    $billingEnabled = Invoke-Gcloud -Arguments @(
        "billing", "projects", "describe", $targetProjectId,
        "--format=value(billingEnabled)"
    ) -Capture
    if ($billingEnabled -ne "True" -and $billingEnabled -ne "true") {
        throw "Billing was linked but is not reported as enabled for '$targetProjectId'."
    }
    Write-Ok "Billing is enabled."

    Write-Step "Enabling Google Cloud APIs"
    $requiredApis = @(
        "run.googleapis.com",
        "cloudbuild.googleapis.com",
        "artifactregistry.googleapis.com",
        "secretmanager.googleapis.com",
        "androidpublisher.googleapis.com",
        "pubsub.googleapis.com",
        "firestore.googleapis.com",
        "iam.googleapis.com",
        "iamcredentials.googleapis.com",
        "sts.googleapis.com",
        "cloudresourcemanager.googleapis.com"
    )
    Invoke-Gcloud -Arguments (@("services", "enable") + $requiredApis + @("--project=$targetProjectId"))
    Write-Ok "Required APIs enabled."

    $projectNumber = Invoke-Gcloud -Arguments @(
        "projects", "describe", $targetProjectId,
        "--format=value(projectNumber)"
    ) -Capture

    $runtimeServiceAccount = "wizefiles-play-backend@$targetProjectId.iam.gserviceaccount.com"
    $pushServiceAccount = "wizefiles-pubsub-push@$targetProjectId.iam.gserviceaccount.com"
    $publisherServiceAccount = "wizefiles-play-publisher@$targetProjectId.iam.gserviceaccount.com"
    $computeBuilderServiceAccount = "$projectNumber-compute@developer.gserviceaccount.com"
    $pubSubServiceAgent = "service-$projectNumber@gcp-sa-pubsub.iam.gserviceaccount.com"

    Write-Step "Creating dedicated service accounts"
    New-ServiceAccountIfMissing `
        -Name "wizefiles-play-backend" `
        -Email $runtimeServiceAccount `
        -DisplayName "WizeFiles Play Backend" `
        -Description "Cloud Run identity for WizeFiles licensing and Google Play verification" `
        -TargetProjectId $targetProjectId

    New-ServiceAccountIfMissing `
        -Name "wizefiles-pubsub-push" `
        -Email $pushServiceAccount `
        -DisplayName "WizeFiles Pub/Sub Push" `
        -Description "OIDC identity for authenticated WizeFiles RTDN push requests" `
        -TargetProjectId $targetProjectId

    New-ServiceAccountIfMissing `
        -Name "wizefiles-play-publisher" `
        -Email $publisherServiceAccount `
        -DisplayName "WizeFiles Google Play Publisher" `
        -Description "Keyless GitHub Actions identity for publishing WizeFiles to Google Play" `
        -TargetProjectId $targetProjectId

    Write-Step "Configuring keyless GitHub Actions publishing identity"
    if (-not (Test-WorkloadIdentityPool -Name $WorkloadIdentityPoolId -TargetProjectId $targetProjectId)) {
        Invoke-Gcloud -Arguments @(
            "iam", "workload-identity-pools", "create", $WorkloadIdentityPoolId,
            "--project=$targetProjectId",
            "--location=global",
            "--display-name=GitHub Actions"
        )
    }

    $attributeMapping = "google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.ref=assertion.ref"
    $attributeCondition = "assertion.repository_id=='$GitHubRepositoryId' && assertion.repository_owner_id=='$GitHubOwnerId' && assertion.ref=='refs/heads/main'"
    $providerArguments = @(
        "--project=$targetProjectId",
        "--location=global",
        "--workload-identity-pool=$WorkloadIdentityPoolId",
        "--issuer-uri=https://token.actions.githubusercontent.com",
        "--attribute-mapping=$attributeMapping",
        "--attribute-condition=$attributeCondition"
    )

    if (Test-WorkloadIdentityProvider `
        -Name $WorkloadIdentityProviderId `
        -PoolName $WorkloadIdentityPoolId `
        -TargetProjectId $targetProjectId) {
        Invoke-Gcloud -Arguments (@(
            "iam", "workload-identity-pools", "providers", "update-oidc", $WorkloadIdentityProviderId
        ) + $providerArguments)
    }
    else {
        Invoke-Gcloud -Arguments (@(
            "iam", "workload-identity-pools", "providers", "create-oidc", $WorkloadIdentityProviderId,
            "--display-name=WizeFiles GitHub Actions"
        ) + $providerArguments)
    }

    $workloadIdentityPoolName = Invoke-Gcloud -Arguments @(
        "iam", "workload-identity-pools", "describe", $WorkloadIdentityPoolId,
        "--project=$targetProjectId",
        "--location=global",
        "--format=value(name)"
    ) -Capture

    $workloadIdentityProviderName = Invoke-Gcloud -Arguments @(
        "iam", "workload-identity-pools", "providers", "describe", $WorkloadIdentityProviderId,
        "--project=$targetProjectId",
        "--location=global",
        "--workload-identity-pool=$WorkloadIdentityPoolId",
        "--format=value(name)"
    ) -Capture

    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "add-iam-policy-binding", $publisherServiceAccount,
        "--project=$targetProjectId",
        "--member=principalSet://iam.googleapis.com/$workloadIdentityPoolName/attribute.repository_id/$GitHubRepositoryId",
        "--role=roles/iam.workloadIdentityUser",
        "--condition=None"
    )
    Write-Ok "GitHub OIDC publisher identity ready for repository ID $GitHubRepositoryId on main."

    Write-Step "Configuring least-privilege IAM"
    Grant-ProjectRole -TargetProjectId $targetProjectId -Member "user:$activeAccount" -Role "roles/run.sourceDeveloper"
    Grant-ProjectRole -TargetProjectId $targetProjectId -Member "user:$activeAccount" -Role "roles/serviceusage.serviceUsageConsumer"
    Grant-ProjectRole -TargetProjectId $targetProjectId -Member "serviceAccount:$runtimeServiceAccount" -Role "roles/datastore.user"

    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "add-iam-policy-binding", $runtimeServiceAccount,
        "--project=$targetProjectId",
        "--member=user:$activeAccount",
        "--role=roles/iam.serviceAccountUser",
        "--condition=None"
    )

    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "add-iam-policy-binding", $pushServiceAccount,
        "--project=$targetProjectId",
        "--member=user:$activeAccount",
        "--role=roles/iam.serviceAccountUser",
        "--condition=None"
    )

    # Current Cloud Run source deployments use the Compute Engine default
    # service account as the default build identity.
    Grant-ProjectRole `
        -TargetProjectId $targetProjectId `
        -Member "serviceAccount:$computeBuilderServiceAccount" `
        -Role "roles/run.builder"

    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "add-iam-policy-binding", $pushServiceAccount,
        "--project=$targetProjectId",
        "--member=serviceAccount:$pubSubServiceAgent",
        "--role=roles/iam.serviceAccountTokenCreator",
        "--condition=None"
    )
    Write-Ok "IAM configuration completed."

    Write-Step "Creating the production Firestore database"
    if (Test-FirestoreDefaultDatabase -TargetProjectId $targetProjectId) {
        Write-Ok "Firestore database '(default)' already exists."
    }
    else {
        Invoke-Gcloud -Arguments @(
            "firestore", "databases", "create",
            "--database=(default)",
            "--location=$FirestoreLocation",
            "--type=firestore-native",
            "--edition=standard",
            "--delete-protection",
            "--project=$targetProjectId",
            "--quiet"
        )
        Write-Ok "Firestore database created in '$FirestoreLocation' with delete protection."
    }

    Write-Step "Preparing the Ed25519 production license key pair"
    $keyRoot = Join-Path $env:USERPROFILE "WizeFiles-Production-Keys"
    New-Item -ItemType Directory -Path $keyRoot -Force | Out-Null

    $existingKeyDirectory = Get-ChildItem -LiteralPath $keyRoot -Directory -Filter "$targetProjectId-*" |
        Sort-Object LastWriteTime -Descending |
        Where-Object {
            (Test-Path -LiteralPath (Join-Path $_.FullName "wizefiles-license-private.pem") -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $_.FullName "wizefiles-license-public.pem") -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $_.FullName "wizefiles-license-public.der") -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $_.FullName "wizefiles-license-public-x509-base64.txt") -PathType Leaf)
        } |
        Select-Object -First 1

    $newKeyGenerated = -not $existingKeyDirectory
    if ($existingKeyDirectory) {
        $keyDirectory = $existingKeyDirectory.FullName
        $privateKeyPath = Join-Path $keyDirectory "wizefiles-license-private.pem"
        $publicPemPath = Join-Path $keyDirectory "wizefiles-license-public.pem"
        $publicDerPath = Join-Path $keyDirectory "wizefiles-license-public.der"
        $publicBase64Path = Join-Path $keyDirectory "wizefiles-license-public-x509-base64.txt"
        $publicKeyBase64 = ([IO.File]::ReadAllText($publicBase64Path, [Text.Encoding]::ASCII)).Trim()
        Write-Ok "Reusing the existing local production key pair in '$keyDirectory'."
    }
    else {
        $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
        $keyDirectory = Join-Path $keyRoot "$targetProjectId-$timestamp"
        New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null

        $privateKeyPath = Join-Path $keyDirectory "wizefiles-license-private.pem"
        $publicPemPath = Join-Path $keyDirectory "wizefiles-license-public.pem"
        $publicDerPath = Join-Path $keyDirectory "wizefiles-license-public.der"
        $publicBase64Path = Join-Path $keyDirectory "wizefiles-license-public-x509-base64.txt"

        & $opensslExecutable genpkey -algorithm ED25519 -out $privateKeyPath
        if ($LASTEXITCODE -ne 0) { throw "OpenSSL failed to generate the Ed25519 private key." }

        & $opensslExecutable pkey -in $privateKeyPath -pubout -out $publicPemPath
        if ($LASTEXITCODE -ne 0) { throw "OpenSSL failed to generate the public PEM." }

        & $opensslExecutable pkey -pubin -in $publicPemPath -outform DER -out $publicDerPath
        if ($LASTEXITCODE -ne 0) { throw "OpenSSL failed to generate the public DER key." }

        $publicKeyBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($publicDerPath))
        [IO.File]::WriteAllText($publicBase64Path, $publicKeyBase64, [Text.Encoding]::ASCII)
        Write-Ok "Key pair generated in '$keyDirectory'."
    }
    Write-WarnMessage "Back up this directory securely. Never add the private PEM to GitHub, the Android app, Play Console, or an email."

    Write-Step "Preparing the private license key in Secret Manager"
    $secretName = "wizefiles-license-private-key"
    $secretExists = Test-Secret -Name $secretName -TargetProjectId $targetProjectId
    if (-not $secretExists) {
        Invoke-Gcloud -Arguments @(
            "secrets", "create", $secretName,
            "--project=$targetProjectId",
            "--replication-policy=automatic"
        )
    }

    if ($newKeyGenerated -or -not $secretExists) {
        Invoke-Gcloud -Arguments @(
            "secrets", "versions", "add", $secretName,
            "--project=$targetProjectId",
            "--data-file=$privateKeyPath"
        )
    }
    else {
        Write-Ok "Reusing the enabled Secret Manager key version; no key rotation was performed."
    }

    $secretVersion = Invoke-Gcloud -Arguments @(
        "secrets", "versions", "list", $secretName,
        "--project=$targetProjectId",
        "--filter=state=ENABLED",
        "--sort-by=~createTime",
        "--limit=1",
        "--format=value(name)"
    ) -Capture

    if (-not $secretVersion) {
        throw "The private key was uploaded but its enabled Secret Manager version could not be determined."
    }

    Invoke-Gcloud -Arguments @(
        "secrets", "add-iam-policy-binding", $secretName,
        "--project=$targetProjectId",
        "--member=serviceAccount:$runtimeServiceAccount",
        "--role=roles/secretmanager.secretAccessor",
        "--condition=None"
    )
    Write-Ok "Private key stored as Secret Manager version $secretVersion."

    Write-Step "Creating the Google Play RTDN Pub/Sub topic"
    $topicName = "wizefiles-play-rtdn"
    $subscriptionName = "wizefiles-play-rtdn-push"

    if (-not (Test-PubSubTopic -Name $topicName -TargetProjectId $targetProjectId)) {
        Invoke-Gcloud -Arguments @(
            "pubsub", "topics", "create", $topicName,
            "--project=$targetProjectId"
        )
    }

    Invoke-Gcloud -Arguments @(
        "pubsub", "topics", "add-iam-policy-binding", $topicName,
        "--project=$targetProjectId",
        "--member=serviceAccount:google-play-developer-notifications@system.gserviceaccount.com",
        "--role=roles/pubsub.publisher"
    )
    Write-Ok "RTDN topic ready: projects/$targetProjectId/topics/$topicName"

    if ($resolvedBackendPath) {
        Write-Step "Deploying the backend source to Cloud Run"
        Push-Location $resolvedBackendPath
        try {
            $environmentVariables = "GOOGLE_CLOUD_PROJECT=$targetProjectId,WIZEFILES_PACKAGE_NAME=com.wisso.wizefiles,WIZEFILES_LICENSE_KEY_ID=$LicenseKeyId,WIZEFILES_LICENSE_PRIVATE_KEY_PATH=/var/run/secrets/wizefiles/license-private.pem,WIZEFILES_PUBSUB_PUSH_SERVICE_ACCOUNT=$pushServiceAccount"
            $secretMount = "/var/run/secrets/wizefiles/license-private.pem=${secretName}:${secretVersion}"

            Invoke-Gcloud -Arguments @(
                "run", "deploy", $ServiceName,
                "--project=$targetProjectId",
                "--region=$Region",
                "--source=.",
                "--service-account=$runtimeServiceAccount",
                "--allow-unauthenticated",
                "--ingress=all",
                "--port=8080",
                "--cpu=1",
                "--memory=512Mi",
                "--min-instances=0",
                "--max-instances=10",
                "--concurrency=40",
                "--timeout=30s",
                "--set-env-vars=$environmentVariables",
                "--update-secrets=$secretMount",
                "--quiet"
            )
        }
        finally {
            Pop-Location
        }

        $script:ServiceUrl = Invoke-Gcloud -Arguments @(
            "run", "services", "describe", $ServiceName,
            "--project=$targetProjectId",
            "--region=$Region",
            "--format=value(status.url)"
        ) -Capture

        if (-not $script:ServiceUrl) {
            throw "Cloud Run deployment completed without returning a service URL."
        }

        $script:BackendDeployed = $true
        Write-Ok "Cloud Run service deployed: $($script:ServiceUrl)"

        Write-Step "Configuring the authenticated RTDN token audience"
        Invoke-Gcloud -Arguments @(
            "run", "services", "update", $ServiceName,
            "--project=$targetProjectId",
            "--region=$Region",
            "--update-env-vars=WIZEFILES_RTDN_AUDIENCE=$($script:ServiceUrl)",
            "--quiet"
        )
        Write-Ok "RTDN audience configured for the Cloud Run service URL."

        Write-Step "Authorizing Pub/Sub and creating the authenticated RTDN push subscription"
        Invoke-Gcloud -Arguments @(
            "run", "services", "add-iam-policy-binding", $ServiceName,
            "--project=$targetProjectId",
            "--region=$Region",
            "--member=serviceAccount:$pushServiceAccount",
            "--role=roles/run.invoker",
            "--condition=None"
        )

        if (Test-PubSubSubscription -Name $subscriptionName -TargetProjectId $targetProjectId) {
            Invoke-Gcloud -Arguments @(
                "pubsub", "subscriptions", "update", $subscriptionName,
                "--project=$targetProjectId",
                "--push-endpoint=$($script:ServiceUrl)/v1/google-play/rtdn",
                "--push-auth-service-account=$pushServiceAccount",
                "--push-auth-token-audience=$($script:ServiceUrl)"
            )
        }
        else {
            Invoke-Gcloud -Arguments @(
                "pubsub", "subscriptions", "create", $subscriptionName,
                "--project=$targetProjectId",
                "--topic=$topicName",
                "--push-endpoint=$($script:ServiceUrl)/v1/google-play/rtdn",
                "--push-auth-service-account=$pushServiceAccount",
                "--push-auth-token-audience=$($script:ServiceUrl)",
                "--ack-deadline=30"
            )
        }

        Write-Ok "Authenticated RTDN push subscription ready."

        Write-Step "Testing the backend health endpoint"
        try {
            $healthResponse = Invoke-RestMethod -Method Get -Uri "$($script:ServiceUrl)/health" -TimeoutSec 30
            Write-Ok "Health endpoint responded successfully: $($healthResponse | ConvertTo-Json -Compress)"
        }
        catch {
            Write-WarnMessage "The Cloud Run deployment succeeded, but /health did not return a successful response: $($_.Exception.Message)"
        }
    }

    Write-Step "Writing the non-secret setup summary"
    $summaryPath = Join-Path $keyDirectory "wizefiles-production-summary.txt"
    $summaryLines = @(
        "WizeFiles production configuration",
        "Generated: $(Get-Date -Format o)",
        "Google account: $activeAccount",
        "Project ID: $targetProjectId",
        "Project number: $projectNumber",
        "Region: $Region",
        "Firestore location: $FirestoreLocation",
        "OpenSSL executable: $opensslExecutable",
        "Runtime service account: $runtimeServiceAccount",
        "Pub/Sub push service account: $pushServiceAccount",
        "Google Play publisher service account: $publisherServiceAccount",
        "GCP_WORKLOAD_IDENTITY_PROVIDER=$workloadIdentityProviderName",
        "GOOGLE_PLAY_PUBLISHER_SERVICE_ACCOUNT=$publisherServiceAccount",
        "RTDN topic: projects/$targetProjectId/topics/$topicName",
        "WIZEFILES_LICENSE_KEY_ID=$LicenseKeyId",
        "WIZEFILES_LICENSE_PUBLIC_KEY_X509_BASE64=$publicKeyBase64",
        "WIZEFILES_LICENSE_API_BASE_URL=$($script:ServiceUrl)",
        "",
        "Manual Play Console actions:",
        "1. Create WizeFiles with package com.wisso.wizefiles.",
        "2. Users and permissions: invite $runtimeServiceAccount.",
        "3. Grant View financial data, orders, and cancellation survey responses.",
        "4. Grant Manage orders and subscriptions, restricted to WizeFiles where possible.",
        "5. Monetize > Monetization setup: set RTDN topic projects/$targetProjectId/topics/$topicName.",
        "6. Send a Play Console RTDN test message.",
        "7. Invite $publisherServiceAccount with WizeFiles release permissions only.",
        "8. Add the WIF, publisher, and three WIZEFILES_* values to the protected google-play-production environment."
    )
    [IO.File]::WriteAllLines($summaryPath, $summaryLines, [Text.Encoding]::UTF8)
    Write-Ok "Summary written to '$summaryPath'."

    Write-Host ""
    Write-Host "WizeFiles cloud setup completed." -ForegroundColor Green
    Write-Host "Project ID: $targetProjectId"
    if ($script:DeletedProjectId) {
        Write-Host "Scheduled for deletion: $($script:DeletedProjectId)" -ForegroundColor Yellow
    }
    Write-Host "Runtime service account: $runtimeServiceAccount"
    Write-Host "Google Play publisher: $publisherServiceAccount"
    Write-Host "GitHub WIF provider: $workloadIdentityProviderName"
    Write-Host "RTDN topic: projects/$targetProjectId/topics/$topicName"
    Write-Host "Public-key file: $publicBase64Path"
    Write-Host "Non-secret summary: $summaryPath"

    if ($script:BackendDeployed) {
        Write-Host "Cloud Run URL: $($script:ServiceUrl)"
    }
    else {
        Write-WarnMessage "Backend deployment is still pending because no backend source directory was supplied. When the backend implementation is ready, rerun this script with its directory and choose R to resume/reuse project '$targetProjectId'."
    }
}
catch {
    Write-Host ""
    Write-Host "SETUP STOPPED" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red

    if ($script:CreatedProjectId) {
        Write-WarnMessage "The newly created project '$($script:CreatedProjectId)' was not automatically deleted. Inspect it before deciding what to do."
    }

    exit 1
}
