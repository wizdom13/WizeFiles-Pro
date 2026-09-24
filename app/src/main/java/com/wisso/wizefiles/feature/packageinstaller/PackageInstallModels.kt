// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.packageinstaller

import java.io.File

enum class PackageArchiveKind { APK, APKS, APKM, XAPK }

enum class PackageInstallAction { INSTALL, UPDATE, DOWNGRADE, REINSTALL }

enum class PackageInstallStage {
    MATERIALIZING,
    INSPECTING,
    REVIEWING,
    AWAITING_CONFIRMATION,
    WRITING_APKS,
    COMMITTING,
    INSTALLING_OBB,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    CANCELLED
}

val PackageInstallStage.isPostCommit: Boolean
    get() = when (this) {
        PackageInstallStage.COMMITTING,
        PackageInstallStage.AWAITING_CONFIRMATION,
        PackageInstallStage.INSTALLING_OBB,
        PackageInstallStage.PARTIALLY_COMPLETED,
        PackageInstallStage.COMPLETED -> true
        else -> false
    }

data class PackageInstallOptions(
    val packageSource: Int,
    val originatingUri: String? = null,
    val requestUpdateOwnership: Boolean = false,
    val installObb: Boolean = true,
    val usePrivilegedInstaller: Boolean = false,
    val allowDowngrade: Boolean = false,
    val allowSignatureMismatch: Boolean = false,
    val targetUserId: Int? = null
)

data class PackageApk(
    val entryName: String,
    val splitName: String?,
    val sizeBytes: Long,
    val sha256: String,
    val stagedFile: File? = null
) {
    val isBase: Boolean get() = splitName == null
}

data class PackageExpansion(
    val entryName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val stagedFile: File? = null
)

data class PackageInstallComparison(
    val action: PackageInstallAction,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val signerCompatible: Boolean,
    val addedPermissions: List<String>,
    val removedPermissions: List<String>,
    val addedFeatures: List<String>,
    val removedFeatures: List<String>,
    val addedComponents: List<String>,
    val removedComponents: List<String>,
    val warnings: List<String>
)

data class PackageInstallPlan(
    val operationId: String,
    val archiveKind: PackageArchiveKind,
    val sourceFile: File,
    val displayName: String,
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val minimumSdk: Int,
    val targetSdk: Int,
    val signerCertificateSha256: List<String>,
    val apks: List<PackageApk>,
    val excludedApks: List<PackageApk>,
    val expansions: List<PackageExpansion>,
    val requestedPermissions: List<String>,
    val requestedFeatures: List<String>,
    val components: List<String>,
    val comparison: PackageInstallComparison
) {
    init {
        require(operationId.isNotBlank())
        require(packageName.isNotBlank())
        require(apks.count(PackageApk::isBase) == 1) {
            "An install plan must contain exactly one base APK"
        }
        require(apks.map(PackageApk::entryName).distinct().size == apks.size) {
            "An install plan cannot contain duplicate APK entries"
        }
    }

    val totalApkBytes: Long get() = apks.sumOf(PackageApk::sizeBytes)
    val totalExpansionBytes: Long get() = expansions.sumOf(PackageExpansion::sizeBytes)
}

data class PackageInstallCapabilities(
    val canRequestUpdateOwnership: Boolean,
    val canInstallForOtherUsers: Boolean,
    val canSilentlyInstall: Boolean,
    val canForceDowngrade: Boolean
)
