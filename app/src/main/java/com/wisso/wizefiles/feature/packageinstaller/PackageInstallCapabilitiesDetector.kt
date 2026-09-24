package com.wisso.wizefiles.feature.packageinstaller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class PackageInstallCapabilitiesDetector(private val context: Context) {
    fun detect(): PackageInstallCapabilities {
        val privilegedPermission = context.checkSelfPermission(Manifest.permission.INSTALL_PACKAGES) ==
            PackageManager.PERMISSION_GRANTED
        val root = PrivilegedPackageInstallerBackend().isAvailable()
        val privileged = privilegedPermission || root
        return PackageInstallCapabilities(
            canRequestUpdateOwnership = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                context.checkSelfPermission(Manifest.permission.ENFORCE_UPDATE_OWNERSHIP) ==
                PackageManager.PERMISSION_GRANTED,
            canInstallForOtherUsers = privileged,
            canSilentlyInstall = privileged,
            canForceDowngrade = privileged
        )
    }
}
