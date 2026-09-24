package com.wisso.wizefiles.feature.appmanager

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppRepository(
    private val packageManager: PackageManager
) {
    suspend fun loadInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            .asSequence()
            .filter { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 }
            .mapNotNull(::toInstalledApp)
            .toList()
    }

    private fun toInstalledApp(applicationInfo: ApplicationInfo): InstalledApp? {
        val packageName = applicationInfo.packageName?.takeIf(String::isNotBlank) ?: return null
        val packageInfo = runCatching {
            packageManager.getPackageInfo(packageName, 0)
        }.getOrNull() ?: return null
        val sourceApkPaths = buildList {
            applicationInfo.publicSourceDir?.takeIf(String::isNotBlank)?.let(::add)
            applicationInfo.splitPublicSourceDirs
                ?.filter(String::isNotBlank)
                ?.let(::addAll)
        }.distinct()
        if (sourceApkPaths.isEmpty() || sourceApkPaths.any { !File(it).isFile }) {
            return null
        }
        val isSystem = applicationInfo.flags and (
            ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        ) != 0
        val label = runCatching {
            applicationInfo.loadLabel(packageManager).toString()
        }.getOrNull()?.takeIf(String::isNotBlank) ?: packageName
        return InstalledApp(
            packageName = packageName,
            label = label,
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = packageInfo.longVersionCode,
            isSystem = isSystem,
            isEnabled = applicationInfo.enabled,
            isSplit = sourceApkPaths.size > 1,
            totalApkBytes = sourceApkPaths.sumOf { File(it).length().coerceAtLeast(0L) },
            firstInstallTimeMillis = packageInfo.firstInstallTime,
            lastUpdateTimeMillis = packageInfo.lastUpdateTime,
            sourceApkPaths = sourceApkPaths,
            hasLaunchIntent = packageManager.getLaunchIntentForPackage(packageName) != null
        )
    }
}
