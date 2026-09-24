// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.packageinstaller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageInstallActionPolicyTest {
    private val rootCapabilities = PackageInstallCapabilities(
        canRequestUpdateOwnership = false,
        canInstallForOtherUsers = true,
        canSilentlyInstall = true,
        canForceDowngrade = true
    )

    @Test
    fun `enables a matching-signature downgrade after root and explicit approval`() {
        assertTrue(
            PackageInstallActionPolicy.canProceed(
                comparison(PackageInstallAction.DOWNGRADE, signerCompatible = true),
                rootCapabilities,
                options(usePrivilegedInstaller = true, allowDowngrade = true)
            )
        )
    }

    @Test
    fun `keeps a downgrade blocked until both privileged gates are selected`() {
        val downgrade = comparison(PackageInstallAction.DOWNGRADE, signerCompatible = true)
        assertFalse(
            PackageInstallActionPolicy.canProceed(
                downgrade,
                rootCapabilities,
                options(usePrivilegedInstaller = true, allowDowngrade = false)
            )
        )
        assertFalse(
            PackageInstallActionPolicy.canProceed(
                downgrade,
                rootCapabilities,
                options(usePrivilegedInstaller = false, allowDowngrade = true)
            )
        )
    }

    @Test
    fun `keeps a signature mismatch blocked without explicit bypass`() {
        assertFalse(
            PackageInstallActionPolicy.canProceed(
                comparison(PackageInstallAction.UPDATE, signerCompatible = false),
                rootCapabilities,
                options(usePrivilegedInstaller = true)
            )
        )
    }

    @Test
    fun `allows a signature mismatch only through the explicitly approved root path`() {
        val mismatch = comparison(PackageInstallAction.UPDATE, signerCompatible = false)
        assertTrue(
            PackageInstallActionPolicy.canProceed(
                mismatch,
                rootCapabilities,
                options(
                    usePrivilegedInstaller = true,
                    allowSignatureMismatch = true
                )
            )
        )
        assertFalse(
            PackageInstallActionPolicy.canProceed(
                mismatch,
                rootCapabilities.copy(canSilentlyInstall = false),
                options(
                    usePrivilegedInstaller = true,
                    allowSignatureMismatch = true
                )
            )
        )
        assertFalse(
            PackageInstallActionPolicy.canProceed(
                mismatch,
                rootCapabilities,
                options(
                    usePrivilegedInstaller = false,
                    allowSignatureMismatch = true
                )
            )
        )
    }

    @Test
    fun `requires both risky approvals for a differently signed downgrade`() {
        val mismatch = comparison(PackageInstallAction.DOWNGRADE, signerCompatible = false)
        assertTrue(
            PackageInstallActionPolicy.canProceed(
                mismatch,
                rootCapabilities,
                options(
                    usePrivilegedInstaller = true,
                    allowDowngrade = true,
                    allowSignatureMismatch = true
                )
            )
        )
        assertFalse(
            PackageInstallActionPolicy.canProceed(
                mismatch,
                rootCapabilities,
                options(
                    usePrivilegedInstaller = true,
                    allowDowngrade = false,
                    allowSignatureMismatch = true
                )
            )
        )
    }

    private fun options(
        usePrivilegedInstaller: Boolean,
        allowDowngrade: Boolean = false,
        allowSignatureMismatch: Boolean = false
    ) = PackageInstallOptions(
        packageSource = 0,
        usePrivilegedInstaller = usePrivilegedInstaller,
        allowDowngrade = allowDowngrade,
        allowSignatureMismatch = allowSignatureMismatch
    )

    private fun comparison(
        action: PackageInstallAction,
        signerCompatible: Boolean
    ) = PackageInstallComparison(
        action = action,
        installedVersionName = "2.0",
        installedVersionCode = 2,
        signerCompatible = signerCompatible,
        addedPermissions = emptyList(),
        removedPermissions = emptyList(),
        addedFeatures = emptyList(),
        removedFeatures = emptyList(),
        addedComponents = emptyList(),
        removedComponents = emptyList(),
        warnings = emptyList()
    )
}
