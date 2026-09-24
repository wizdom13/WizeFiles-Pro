package com.wisso.wizefiles.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo

fun PackageManager.getPackageInfoOrNull(packageName: String, flags: Int): PackageInfo? =
    getPackageManagerInfoOrNull { getPackageInfo(packageName, flags) }

fun PackageManager.getPermissionInfoOrNull(permissionName: String, flags: Int): PermissionInfo? =
    getPackageManagerInfoOrNull { getPermissionInfo(permissionName, flags) }

private inline fun <T> getPackageManagerInfoOrNull(block: () -> T): T? {
    return try {
        block()
    } catch (e: PackageManager.NameNotFoundException) {
        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
        null
    }
}
