// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.os.Environment
import java.io.File
import java.util.Locale

class StorageScanner(
    private val storageRootProvider: () -> File? = {
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
    }
) {
    fun scanAllFiles(limit: Int): StorageScanResult {
        val root = storageRootProvider()
            ?: return StorageScanResult(emptyList(), false, 0, 0)
        if (!safeExists(root) || !safeIsDirectory(root)) {
            return StorageScanResult(emptyList(), false, 0, 0)
        }

        val safeLimit = limit.coerceAtLeast(0)
        val queue = ArrayDeque<File>()
        val files = mutableListOf<File>()
        var discoveredFiles = 0
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (isExcludedDirectory(current)) {
                continue
            }
            val children = runCatching { current.listFiles() }.getOrNull().orEmpty()
            for (child in children) {
                when {
                    safeIsDirectory(child) -> queue.add(child)
                    safeIsFile(child) -> {
                        discoveredFiles += 1
                        if (files.size >= safeLimit) {
                            return StorageScanResult(
                                files = files,
                                scanWasTruncated = true,
                                scannedFileCount = files.size,
                                discoveredFileCountEstimate = discoveredFiles
                            )
                        }
                        files += child
                    }
                }
            }
        }
        return StorageScanResult(
            files = files,
            scanWasTruncated = false,
            scannedFileCount = files.size,
            discoveredFileCountEstimate = discoveredFiles
        )
    }

    fun compositionCategoryFor(file: File): StorageCompositionCategory {
        val name = file.name.lowercase(Locale.US)
        return when {
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".heic") ->
                StorageCompositionCategory.IMAGES
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
                name.endsWith(".mov") || name.endsWith(".avi") -> StorageCompositionCategory.VIDEOS
            name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav") ||
                name.endsWith(".ogg") || name.endsWith(".flac") -> StorageCompositionCategory.AUDIO
            name.endsWith(".apk") || name.endsWith(".apkm") || name.endsWith(".xapk") -> StorageCompositionCategory.APKS
            name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ||
                name.endsWith(".tar") || name.endsWith(".gz") -> StorageCompositionCategory.ARCHIVES
            name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") ||
                name.endsWith(".txt") || name.endsWith(".ppt") || name.endsWith(".pptx") ||
                name.endsWith(".xls") || name.endsWith(".xlsx") -> StorageCompositionCategory.DOCUMENTS
            else -> StorageCompositionCategory.OTHER
        }
    }



    fun isApkInstallerFile(file: File): Boolean {
        val name = file.name.lowercase(Locale.US)
        return name.endsWith(".apk") || name.endsWith(".apkm") || name.endsWith(".xapk")
    }

    fun categoryFor(
        file: File,
        minJunkFileAgeDays: Int = 7,
        nowMillis: Long = System.currentTimeMillis()
    ): AnalysisCategory {
        val path = file.path.lowercase(Locale.US)
        val modifiedTimeMillis = runCatching { file.lastModified() }.getOrDefault(0L)
        return when {
            junkClassificationFor(
                fileName = file.name,
                modifiedTimeMillis = modifiedTimeMillis,
                nowMillis = nowMillis,
                minAgeDays = minJunkFileAgeDays
            ) != null -> AnalysisCategory.JUNK
            path.contains("/download") -> AnalysisCategory.DOWNLOADS
            else -> AnalysisCategory.DOCUMENTS
        }
    }

    private fun isExcludedDirectory(file: File): Boolean {
        val path = file.path.lowercase(Locale.US)
        return path.contains("/android/data") ||
            path.contains("/android/obb") ||
            path.contains("/android/media")
    }

    private fun safeExists(file: File): Boolean = runCatching { file.exists() }.getOrDefault(false)
    private fun safeIsDirectory(file: File): Boolean =
        runCatching { file.isDirectory }.getOrDefault(false)
    private fun safeIsFile(file: File): Boolean = runCatching { file.isFile }.getOrDefault(false)
}
