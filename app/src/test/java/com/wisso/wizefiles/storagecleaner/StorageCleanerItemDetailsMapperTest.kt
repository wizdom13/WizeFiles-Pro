// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCleanerItemDetailsMapperTest {

    @Test
    fun fileRecommendationExposesOpenFileAndFolderActions() {
        val recommendation = recommendation(type = RecommendationType.LARGE_FILE, path = "/storage/emulated/0/Movies/movie.mp4")

        val actions = buildDetailsActions(recommendation, recommendation.path)

        assertTrue(actions.canOpenFile)
        assertTrue(actions.canOpenFolder)
        assertFalse(actions.canOpenAppInfo)
    }

    @Test
    fun unusedAppExposesOnlyAppInfoAction() {
        val recommendation = recommendation(type = RecommendationType.UNUSED_APP, packageName = "com.example.app")

        val actions = buildDetailsActions(recommendation, recommendation.path)

        assertFalse(actions.canOpenFile)
        assertFalse(actions.canOpenFolder)
        assertTrue(actions.canOpenAppInfo)
    }

    @Test
    fun duplicateRecommendationKeepsFileActionsWhenPathAvailable() {
        val duplicate = recommendation(
            type = RecommendationType.DUPLICATE,
            path = null,
            duplicateGroup = DuplicateGroup(
                id = "group1",
                hash = "hash",
                candidates = listOf(
                    FileCandidate("/storage/emulated/0/DCIM/IMG_1.jpg", 100, 1),
                    FileCandidate("/storage/emulated/0/DCIM/IMG_1_copy.jpg", 100, 2)
                ),
                keepCandidatePath = "/storage/emulated/0/DCIM/IMG_1.jpg"
            )
        )

        val actions = buildDetailsActions(duplicate, duplicate.duplicateGroup?.keepCandidatePath)

        assertTrue(actions.canOpenFile)
        assertTrue(actions.canOpenFolder)
        assertFalse(actions.canOpenAppInfo)
    }

    private fun recommendation(
        type: RecommendationType,
        path: String? = null,
        packageName: String? = null,
        duplicateGroup: DuplicateGroup? = null
    ): CleanupRecommendation {
        return CleanupRecommendation(
            id = type.name,
            type = type,
            title = type.name,
            reason = "reason",
            reclaimableBytes = 1024,
            path = path,
            packageName = packageName,
            duplicateGroup = duplicateGroup,
            score = RecommendationScore(
                reclaimableBytes = 1024,
                confidence = 0.8,
                safety = 0.8,
                staleness = 0.8,
                duplicateCertainty = 0.8,
                ignored = false
            ),
            preselected = false
        )
    }
}
