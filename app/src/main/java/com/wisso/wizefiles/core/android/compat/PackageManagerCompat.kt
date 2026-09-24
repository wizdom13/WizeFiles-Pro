package com.wisso.wizefiles.core.android.compat

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build

object PackageManagerCompat {
    @SuppressLint("InlinedApi")
    const val MATCH_UNINSTALLED_PACKAGES = PackageManager.MATCH_UNINSTALLED_PACKAGES
}

fun PackageManager.getPackageArchiveInfoCompat(archiveFilePath: String, flags: Int): PackageInfo? {
    getPackageArchiveInfo(archiveFilePath, flags)?.let { return it }

    @Suppress("DEPRECATION")
    val legacySigning = PackageManager.GET_SIGNATURES
    val modernSigning =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            0
        }
    val signingFlags = legacySigning or modernSigning
    if (flags and signingFlags == 0) return null

    val fallbackFlags = flags and signingFlags.inv()
    return getPackageArchiveInfo(archiveFilePath, fallbackFlags)?.also { packageInfo ->
        @Suppress("DEPRECATION")
        if (flags and legacySigning != 0) packageInfo.signatures = emptyArray()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && flags and modernSigning != 0) {
            packageInfo.signingInfo = SigningInfo()
        }
    }
}
