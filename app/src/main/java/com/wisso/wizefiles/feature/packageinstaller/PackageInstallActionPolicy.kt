package com.wisso.wizefiles.feature.packageinstaller

/**
 * Centralizes the action gate so the review screen and installer backends keep the same safety
 * boundary. A signing mismatch can reach only the root backend after explicit approval; Android
 * still decides whether Core Patch or another compatible system modification accepts it.
 */
internal object PackageInstallActionPolicy {
    fun canProceed(
        comparison: PackageInstallComparison,
        capabilities: PackageInstallCapabilities,
        options: PackageInstallOptions
    ): Boolean {
        val signerAllowed = comparison.signerCompatible ||
            capabilities.canSilentlyInstall &&
            options.usePrivilegedInstaller &&
            options.allowSignatureMismatch
        if (!signerAllowed) return false
        if (comparison.action != PackageInstallAction.DOWNGRADE) return true
        return capabilities.canForceDowngrade &&
            options.usePrivilegedInstaller &&
            options.allowDowngrade
    }
}
