// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.appmanager

data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val isSplit: Boolean,
    val totalApkBytes: Long,
    val firstInstallTimeMillis: Long,
    val lastUpdateTimeMillis: Long,
    val sourceApkPaths: List<String>,
    val hasLaunchIntent: Boolean
)

enum class AppManagerFilter {
    ALL,
    USER,
    SYSTEM,
    DISABLED
}

enum class AppManagerSort {
    NAME,
    APK_SIZE,
    INSTALLED_DATE,
    UPDATED_DATE,
    PACKAGE_NAME
}

enum class AppManagerSortOrder {
    ASCENDING,
    DESCENDING
}
