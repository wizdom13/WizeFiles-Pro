package com.wisso.wizefiles.feature.apksigning

import java.io.IOException

enum class AndroidPackageContainerFormat {
    AAB,
    BUNDLETOOL_APKS,
    WIZEFILES_APKS,
    XAPK,
    APKM
}

enum class AndroidPackageContainerHint {
    AAB,
    APKS,
    XAPK,
    APKM
}

data class AndroidPackageContainerLimits(
    val maximumArchiveBytes: Long = 8L * 1024 * 1024 * 1024,
    val maximumEntries: Int = 4096,
    val maximumApkEntries: Int = 512,
    val maximumEntryBytes: Long = 4L * 1024 * 1024 * 1024,
    val maximumExpandedBytes: Long = 12L * 1024 * 1024 * 1024,
    val maximumCompressionRatio: Long = 250,
    val maximumPathLength: Int = 512,
    val maximumMetadataBytes: Long = 2L * 1024 * 1024
)

data class AndroidPackageContainerEntry(
    val name: String,
    val sizeBytes: Long,
    val compressedBytes: Long,
    val sha256: String,
    val isApk: Boolean
)

data class AndroidPackageContainerInventory(
    val format: AndroidPackageContainerFormat,
    val entries: List<AndroidPackageContainerEntry>,
    val apkEntries: List<AndroidPackageContainerEntry>,
    val packageNameHint: String?,
    val versionCodeHint: Long?
) {
    init {
        require(entries.isNotEmpty()) { "A package container cannot be empty" }
        require(format == AndroidPackageContainerFormat.AAB || apkEntries.isNotEmpty()) {
            "A split-package container must contain at least one APK"
        }
    }
}

class AndroidPackageContainerException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
