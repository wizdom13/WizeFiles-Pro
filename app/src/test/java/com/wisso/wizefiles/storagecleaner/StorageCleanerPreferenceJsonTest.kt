// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCleanerPreferenceJsonTest {
    @Test
    fun encodeDecodePreservesIgnoredItemsAndKeepOverrides() {
        val preferences = StorageCleanerPreferences(
            ignoredItems = listOf(
                IgnoredCleanupItem(
                    id = "large:/storage/emulated/0/video.mp4",
                    type = RecommendationType.LARGE_FILE,
                    title = "video.mp4",
                    location = "/storage/emulated/0/video.mp4",
                    ignoredAtMillis = 42L
                )
            ),
            duplicateKeepOverrides = mapOf(
                "group-hash" to "/storage/emulated/0/preferred.jpg"
            )
        )

        val decoded = StorageCleanerPreferenceJson.decode(
            StorageCleanerPreferenceJson.encode(preferences)
        )

        assertEquals(preferences, decoded)
    }

    @Test
    fun decodeSkipsUnknownRecommendationTypesAndBlankOverrides() {
        val decoded = StorageCleanerPreferenceJson.decode(
            """
            {
              "schemaVersion": 1,
              "ignoredItems": [
                {
                  "id": "future:item",
                  "type": "FUTURE_TYPE",
                  "title": "Future",
                  "ignoredAtMillis": 10
                }
              ],
              "duplicateKeepOverrides": {
                "valid": "/storage/emulated/0/keep.jpg",
                "blank": ""
              }
            }
            """.trimIndent()
        )

        assertTrue(decoded.ignoredItems.isEmpty())
        assertEquals(
            mapOf("valid" to "/storage/emulated/0/keep.jpg"),
            decoded.duplicateKeepOverrides
        )
    }
}
