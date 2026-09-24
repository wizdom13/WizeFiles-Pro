// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCleanerCacheJsonTest {

    @Test
    fun encodeDecodePreservesTotalStorageBytesAndCompositionCategories() {
        val analysis = StorageAnalysisResult(
            compositionCategories = listOf(StorageCompositionSummary(StorageCompositionCategory.APKS, 512L, 1)),
            recommendations = emptyList(),
            progress = ScanProgress(1, 1, "Completed", true),
            missingCapabilities = emptyList(),
            totalStorageBytes = 1024L
        )

        val encoded = StorageCleanerCacheJson.encode(analysis, ScanFilters())
        val decoded = StorageCleanerCacheJson.decode(encoded, lastUpdatedMillis = 50L)

        assertEquals(1024L, decoded.analysis.totalStorageBytes)
        assertEquals(listOf(StorageCompositionCategory.APKS), decoded.analysis.compositionCategories.map { it.category })
    }

    @Test
    fun decodeLegacyCategoriesMapsToCompositionCategories() {
        val legacy = """
            {
              "analysis": {
                "categories": [
                  {"category":"DOWNLOADS","bytes":20,"itemCount":1},
                  {"category":"APKS","bytes":40,"itemCount":2},
                  {"category":"LARGE_FILES","bytes":100,"itemCount":1}
                ],
                "recommendations": [],
                "progress": {"scannedItems":1,"totalItemsEstimate":1,"phase":"Completed","isFinished":true},
                "missingCapabilities": [],
                "totalStorageBytes": 1000
              },
              "filters": {}
            }
        """.trimIndent()

        val decoded = StorageCleanerCacheJson.decode(legacy, lastUpdatedMillis = 1L)

        assertEquals(listOf(StorageCompositionCategory.DOCUMENTS, StorageCompositionCategory.APKS), decoded.analysis.compositionCategories.map { it.category })
        assertTrue(decoded.analysis.compositionCategories.none { it.category == StorageCompositionCategory.OTHER })
    }

    @Test
    fun encodeDecodePreservesStaleDownloadSignal() {
        val recommendation = CleanupRecommendation(
            id = "stale:downloaded",
            type = RecommendationType.STALE_FILE,
            title = "old",
            reason = "old",
            reclaimableBytes = 10,
            path = "/storage/emulated/0/Download/old.zip",
            isDownloadRelated = true,
            score = RecommendationScore(10, 0.5, 0.5, 0.5, 0.0, false),
            preselected = false
        )
        val analysis = StorageAnalysisResult(
            compositionCategories = emptyList(),
            recommendations = listOf(recommendation),
            progress = ScanProgress(1, 1, "Completed", true),
            missingCapabilities = emptyList(),
            totalStorageBytes = 0L
        )

        val encoded = StorageCleanerCacheJson.encode(analysis, ScanFilters())
        val decoded = StorageCleanerCacheJson.decode(encoded, lastUpdatedMillis = 1L)

        assertTrue(decoded.analysis.recommendations.single().isDownloadRelated)
    }


    @Test
    fun encodeDecodePreservesRecommendedAndOverriddenKeepCandidates() {
        val group = DuplicateGroup(
            id = "group",
            hash = "hash",
            candidates = listOf(
                FileCandidate("/storage/emulated/0/a.jpg", 10L, 2L),
                FileCandidate("/storage/emulated/0/b.jpg", 20L, 1L)
            ),
            keepCandidatePath = "/storage/emulated/0/b.jpg",
            recommendedKeepCandidatePath = "/storage/emulated/0/a.jpg",
            keepSelectionRequiresReview = true
        )
        val recommendation = CleanupRecommendation(
            id = "dup:group:members",
            type = RecommendationType.DUPLICATE_MEDIA,
            title = "Duplicate media",
            reason = "Duplicate group",
            reclaimableBytes = 10L,
            duplicateGroup = group,
            score = RecommendationScore(10L, 0.99, 0.9, 0.5, 1.0, false),
            preselected = true
        )
        val analysis = StorageAnalysisResult(
            compositionCategories = emptyList(),
            recommendations = listOf(recommendation),
            progress = ScanProgress(2, 2, "Completed", true),
            missingCapabilities = emptyList()
        )

        val decoded = StorageCleanerCacheJson.decode(
            StorageCleanerCacheJson.encode(analysis, ScanFilters()),
            lastUpdatedMillis = 1L
        )
        val decodedGroup = decoded.analysis.recommendations.single().duplicateGroup

        assertEquals("/storage/emulated/0/b.jpg", decodedGroup?.keepCandidatePath)
        assertEquals(
            "/storage/emulated/0/a.jpg",
            decodedGroup?.recommendedKeepCandidatePath
        )
        assertTrue(decodedGroup?.keepSelectionRequiresReview == true)
    }

    @Test
    fun decodeLegacyStaleRecommendationsInfersDownloadSignalFromPath() {
        val legacy = """
            {
              "analysis": {
                "categories": [],
                "recommendations": [
                  {
                    "id":"stale:download",
                    "type":"STALE_FILE",
                    "title":"x",
                    "reason":"y",
                    "reclaimableBytes": 11,
                    "path":"/storage/emulated/0/Download/old.apk",
                    "score":{"reclaimableBytes":11,"confidence":0.5,"safety":0.5,"staleness":0.5,"duplicateCertainty":0.0,"ignored":false},
                    "preselected":false
                  },
                  {
                    "id":"stale:doc",
                    "type":"STALE_FILE",
                    "title":"x",
                    "reason":"y",
                    "reclaimableBytes": 12,
                    "path":"/storage/emulated/0/Documents/old.txt",
                    "score":{"reclaimableBytes":12,"confidence":0.5,"safety":0.5,"staleness":0.5,"duplicateCertainty":0.0,"ignored":false},
                    "preselected":false
                  }
                ],
                "progress": {"scannedItems":1,"totalItemsEstimate":1,"phase":"Completed","isFinished":true},
                "missingCapabilities": [],
                "totalStorageBytes": 0
              },
              "filters": {}
            }
        """.trimIndent()

        val decoded = StorageCleanerCacheJson.decode(legacy, lastUpdatedMillis = 1L)

        val downloadStale = decoded.analysis.recommendations.first()
        val normalStale = decoded.analysis.recommendations.last()
        assertTrue(downloadStale.isDownloadRelated)
        assertTrue(!normalStale.isDownloadRelated)
    }

}