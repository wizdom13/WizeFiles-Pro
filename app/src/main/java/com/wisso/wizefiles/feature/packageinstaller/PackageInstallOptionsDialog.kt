// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.packageinstaller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wisso.wizefiles.R

internal object PackageInstallOptionsDialog {
    fun show(
        activity: AppCompatActivity,
        plan: PackageInstallPlan?,
        capabilities: PackageInstallCapabilities,
        current: PackageInstallOptions,
        onApply: (PackageInstallOptions) -> Unit
    ) {
        val labels = mutableListOf<String>()
        val checked = mutableListOf<Boolean>()
        var privilegedIndex: Int? = null
        var downgradeIndex: Int? = null
        var signatureMismatchIndex: Int? = null
        if (capabilities.canSilentlyInstall) {
            privilegedIndex = labels.size
            labels += activity.getString(R.string.package_installer_option_privileged)
            checked += current.usePrivilegedInstaller
        }
        if (plan?.comparison?.action == PackageInstallAction.DOWNGRADE &&
            capabilities.canForceDowngrade
        ) {
            downgradeIndex = labels.size
            labels += activity.getString(R.string.package_installer_option_downgrade)
            checked += current.allowDowngrade
        }
        if (plan?.comparison?.signerCompatible == false && capabilities.canSilentlyInstall) {
            signatureMismatchIndex = labels.size
            labels += activity.getString(R.string.package_installer_option_signature_mismatch)
            checked += current.allowSignatureMismatch
        }
        val obbIndex = if (plan?.expansions?.isNotEmpty() == true) labels.size else null
        if (obbIndex != null) {
            labels += activity.getString(R.string.package_installer_option_obb)
            checked += current.installObb
        }
        val canRequestOwnership = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            activity.checkSelfPermission(Manifest.permission.ENFORCE_UPDATE_OWNERSHIP) ==
            PackageManager.PERMISSION_GRANTED &&
            plan?.comparison?.action == PackageInstallAction.INSTALL
        val ownershipIndex = if (canRequestOwnership) labels.size else null
        if (ownershipIndex != null) {
            labels += activity.getString(R.string.package_installer_option_update_ownership)
            checked += current.requestUpdateOwnership
        }
        if (labels.isEmpty()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.package_installer_options)
                .setMessage(R.string.package_installer_no_advanced_options)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val values = checked.toBooleanArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.package_installer_options)
            .setMultiChoiceItems(labels.toTypedArray(), values) { _, which, enabled ->
                values[which] = enabled
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val allowSignatureMismatch =
                    signatureMismatchIndex?.let { values[it] } ?: false
                val updated = current.copy(
                    usePrivilegedInstaller =
                        (privilegedIndex?.let { values[it] } ?: false) ||
                            allowSignatureMismatch,
                    allowDowngrade = downgradeIndex?.let { values[it] } ?: false,
                    allowSignatureMismatch = allowSignatureMismatch,
                    installObb = obbIndex?.let { values[it] } ?: current.installObb,
                    requestUpdateOwnership = ownershipIndex?.let { values[it] }
                        ?: current.requestUpdateOwnership
                )
                if (updated.allowSignatureMismatch && !current.allowSignatureMismatch) {
                    confirmSignatureMismatch(activity, updated, onApply)
                } else {
                    onApply(updated)
                }
            }
            .show()
    }

    private fun confirmSignatureMismatch(
        activity: AppCompatActivity,
        updated: PackageInstallOptions,
        onApply: (PackageInstallOptions) -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.package_installer_option_signature_mismatch)
            .setMessage(R.string.package_installer_signature_mismatch_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> onApply(updated) }
            .show()
    }
}
