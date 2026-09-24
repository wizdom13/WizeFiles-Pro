// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.wisso.wizefiles.core.android.compat.getPackageArchiveInfoCompat
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.LocalAppPath
import java.io.Closeable

val AppPath.isGetPackageArchiveInfoCompatible: Boolean
    get() = this is LocalAppPath

fun PackageManager.getPackageArchiveInfoCompat(
    path: AppPath,
    flags: Int
): Pair<PackageInfo?, Closeable?> {
    val archiveFilePath = when (path) {
        is LocalAppPath -> path.file.path
        else -> throw IllegalArgumentException(path.rawPath)
    }
    val closeable: Closeable? = null
    var successful = false
    val packageInfo: PackageInfo?
    try {
        packageInfo = getPackageArchiveInfoCompat(archiveFilePath, flags)?.apply {
            applicationInfo?.apply {
                sourceDir = archiveFilePath
                publicSourceDir = archiveFilePath
            }
        }
        successful = true
    } finally {
        if (!successful) {
            closeable?.close()
        }
    }
    return packageInfo to closeable
}
