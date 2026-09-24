// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {
    @Test
    fun exactDuplicateGroupingUsesContentHash() {
        val dir = createTempDirectory().toFile()
        val one = File(dir, "one.txt").apply { writeText("same-content") }
        val two = File(dir, "two.txt").apply { writeText("same-content") }
        val three = File(dir, "three.txt").apply { writeText("different") }
        val groups = DuplicateDetector().detectExactDuplicates(listOf(one, two, three), 100)
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().candidates.size)
        assertTrue(groups.first().candidates.any { it.path == one.path })
    }

    @Test
    fun keepRecommendationPrefersCanonicalMediaFolder() {
        val root = createTempDirectory().toFile()
        val dcim = File(root, "DCIM/Camera").apply { mkdirs() }
        val downloads = File(root, "Download").apply { mkdirs() }
        val original = File(dcim, "IMG_1000.jpg").apply {
            writeText("identical-photo")
            setLastModified(1_000L)
        }
        val downloadedCopy = File(downloads, "IMG_1000.jpg").apply {
            writeText("identical-photo")
            setLastModified(9_000L)
        }

        val group = DuplicateDetector()
            .detectExactDuplicates(listOf(downloadedCopy, original), 100)
            .single()

        assertEquals(original.path, group.recommendedKeepCandidatePath)
        assertEquals(original.path, group.keepCandidatePath)
    }

    @Test
    fun keepRecommendationPrefersOriginalLookingFilenameBeforeTimestamp() {
        val root = createTempDirectory().toFile()
        val pictures = File(root, "Pictures").apply { mkdirs() }
        val original = File(pictures, "holiday.jpg").apply {
            writeText("identical-photo")
            setLastModified(9_000L)
        }
        val numberedCopy = File(pictures, "holiday (1).jpg").apply {
            writeText("identical-photo")
            setLastModified(1_000L)
        }

        val group = DuplicateDetector()
            .detectExactDuplicates(listOf(numberedCopy, original), 100)
            .single()

        assertEquals(original.path, group.recommendedKeepCandidatePath)
    }

    @Test
    fun keepRecommendationUsesOldestKnownTimestampAsTieBreaker() {
        val root = createTempDirectory().toFile()
        val documents = File(root, "Documents").apply { mkdirs() }
        val older = File(documents, "alpha.bin").apply {
            writeText("same")
            setLastModified(1_000L)
        }
        val newer = File(documents, "beta.bin").apply {
            writeText("same")
            setLastModified(9_000L)
        }

        val group = DuplicateDetector()
            .detectExactDuplicates(listOf(newer, older), 100)
            .single()

        assertEquals(older.path, group.recommendedKeepCandidatePath)
    }

    @Test
    fun keepRankerTreatsCacheCopiesAsLowestPriority() {
        val candidates = listOf(
            FileCandidate("/storage/emulated/0/Android/data/app/cache/photo.jpg", 10L, 1_000L),
            FileCandidate("/storage/emulated/0/Pictures/photo copy.jpg", 10L, 1_000L),
            FileCandidate("/storage/emulated/0/Pictures/photo.jpg", 10L, 2_000L)
        )

        assertEquals(
            "/storage/emulated/0/Pictures/photo.jpg",
            DuplicateKeepRanker().choose(candidates)?.path
        )
    }
}
