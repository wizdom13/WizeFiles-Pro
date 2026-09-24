// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.packageinstaller

import java.util.Locale

internal object AndroidPackageInstallerInput {
    private val supportedExtensions = setOf("apk", "apks", "apkm", "xapk")

    fun extension(displayName: String): String? = displayName
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
        .takeIf { it in supportedExtensions }
}
