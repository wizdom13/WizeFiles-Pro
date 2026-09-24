package com.wisso.wizefiles.feature.packageinstaller

import java.util.Locale

internal object AndroidPackageInstallerInput {
    private val supportedExtensions = setOf("apk", "apks", "apkm", "xapk")

    fun extension(displayName: String): String? = displayName
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
        .takeIf { it in supportedExtensions }
}
