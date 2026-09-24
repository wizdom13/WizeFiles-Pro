// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class StorageScannerTest {
    private val scanner = StorageScanner()

    @Test
    fun compositionCategoryForApkAndArchiveInsideDownloadsUsesTypeNotFolder() {
        val apkInDownloads = File("/storage/emulated/0/Download/app-release.apk")
        val zipInDownloads = File("/storage/emulated/0/Download/backup.zip")

        assertEquals(StorageCompositionCategory.APKS, scanner.compositionCategoryFor(apkInDownloads))
        assertEquals(StorageCompositionCategory.APKS, scanner.compositionCategoryFor(File("/storage/emulated/0/Download/app-bundle.apkm")))
        assertEquals(StorageCompositionCategory.APKS, scanner.compositionCategoryFor(File("/storage/emulated/0/Download/app-bundle.xapk")))
        assertEquals(StorageCompositionCategory.ARCHIVES, scanner.compositionCategoryFor(zipInDownloads))
    }

    @Test
    fun compositionCategoryForKnownTypesAndUnknowns() {
        assertEquals(StorageCompositionCategory.IMAGES, scanner.compositionCategoryFor(File("/x/photo.jpg")))
        assertEquals(StorageCompositionCategory.VIDEOS, scanner.compositionCategoryFor(File("/x/movie.mp4")))
        assertEquals(StorageCompositionCategory.AUDIO, scanner.compositionCategoryFor(File("/x/song.mp3")))
        assertEquals(StorageCompositionCategory.DOCUMENTS, scanner.compositionCategoryFor(File("/x/readme.pdf")))
        assertEquals(StorageCompositionCategory.OTHER, scanner.compositionCategoryFor(File("/x/blob.abcxyz")))
    }

    @Test
    fun apkInstallerDetectionSupportsCommonExtensions() {
        assertEquals(true, scanner.isApkInstallerFile(File("/x/package.apk")))
        assertEquals(true, scanner.isApkInstallerFile(File("/x/package.apkm")))
        assertEquals(true, scanner.isApkInstallerFile(File("/x/package.xapk")))
        assertEquals(false, scanner.isApkInstallerFile(File("/x/archive.zip")))
    }
    @Test
    fun scanReportsWhenTheFileLimitTruncatesResults() {
        val root = Files.createTempDirectory("storage-scan").toFile()
        try {
            File(root, "one.bin").writeText("1")
            File(root, "two.bin").writeText("2")
            File(root, "three.bin").writeText("3")

            val result = StorageScanner { root }.scanAllFiles(limit = 2)

            assertEquals(2, result.files.size)
            assertEquals(2, result.scannedFileCount)
            assertTrue(result.scanWasTruncated)
            assertTrue(result.discoveredFileCountEstimate > result.scannedFileCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun scanReportsCompleteResultsWhenEveryFileWasExamined() {
        val root = Files.createTempDirectory("storage-scan-complete").toFile()
        try {
            File(root, "one.bin").writeText("1")
            File(root, "two.bin").writeText("2")

            val result = StorageScanner { root }.scanAllFiles(limit = 3)

            assertEquals(2, result.scannedFileCount)
            assertEquals(2, result.discoveredFileCountEstimate)
            assertFalse(result.scanWasTruncated)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun junkAnalysisExcludesRecentAndUnknownAgeFiles() {
        val now = TimeUnit.DAYS.toMillis(100)
        val root = Files.createTempDirectory("junk-age").toFile()
        try {
            val oldTemp = File(root, "old.tmp").apply {
                writeText("old")
                setLastModified(now - TimeUnit.DAYS.toMillis(8))
            }
            val recentPart = File(root, "active.part").apply {
                writeText("active")
                setLastModified(now - TimeUnit.DAYS.toMillis(2))
            }
            val recentLog = File(root, "recent.log").apply {
                writeText("log")
                setLastModified(now - TimeUnit.DAYS.toMillis(20))
            }
            val oldLog = File(root, "old.log").apply {
                writeText("log")
                setLastModified(now - TimeUnit.DAYS.toMillis(31))
            }
            val unknownAge = File(root, "unknown.tmp").apply {
                writeText("unknown")
                setLastModified(0L)
            }

            val candidates = JunkFileAnalyzer(
                strings = JunkReasonStrings(
                    temporaryFile = { days -> "temporary-$days" },
                    partialDownload = { days -> "partial-$days" },
                    logFile = { days -> "log-$days" }
                ),
                nowMillisProvider = { now }
            ).scan(
                listOf(oldTemp, recentPart, recentLog, oldLog, unknownAge),
                minAgeDays = 7
            )

            assertEquals(
                setOf(oldTemp.path, oldLog.path),
                candidates.mapTo(mutableSetOf()) { it.path }
            )
        } finally {
            root.deleteRecursively()
        }
    }

}
