package com.wisso.wizefiles.storagecleaner

import org.junit.Assert.assertEquals
import org.junit.Test

class DeletionPlannerTest {
    @Test
    fun duplicatePreviewExcludesKeepCandidateAndCarriesSafetyMetadata() {
        val group = DuplicateGroup(
            id = "id",
            hash = "hash",
            candidates = listOf(
                FileCandidate("/storage/emulated/0/a.jpg", 10, 1),
                FileCandidate("/storage/emulated/0/b.jpg", 10, 2)
            ),
            keepCandidatePath = "/storage/emulated/0/b.jpg",
            recommendedKeepCandidatePath = "/storage/emulated/0/a.jpg"
        )
        val recommendation = CleanupRecommendation(
            id = "dup:id:members",
            type = RecommendationType.DUPLICATE,
            title = "Duplicate",
            reason = "Duplicate group",
            reclaimableBytes = 10,
            duplicateGroup = group,
            score = RecommendationScore(10, 1.0, 1.0, 0.0, 1.0, false),
            preselected = true
        )

        val preview = DeletionPlanner().buildPreview(listOf(recommendation))

        assertEquals(1, preview.size)
        assertEquals("/storage/emulated/0/a.jpg", preview.first().path)
        assertEquals("/storage/emulated/0/b.jpg", preview.first().keepCandidatePath)
        assertEquals("hash", preview.first().duplicateGroupHash)
    }

    @Test
    fun changingKeepCandidateChangesWhichDuplicateIsRemoved() {
        val candidates = listOf(
            FileCandidate("/storage/emulated/0/a.jpg", 10, 1),
            FileCandidate("/storage/emulated/0/b.jpg", 20, 2),
            FileCandidate("/storage/emulated/0/c.jpg", 30, 3)
        )
        val recommendation = CleanupRecommendation(
            id = "dup:id:members",
            type = RecommendationType.DUPLICATE_FILES,
            title = "Duplicates",
            reason = "Duplicate group",
            reclaimableBytes = 40,
            duplicateGroup = DuplicateGroup(
                id = "id",
                hash = "hash",
                candidates = candidates,
                keepCandidatePath = "/storage/emulated/0/b.jpg",
                recommendedKeepCandidatePath = "/storage/emulated/0/a.jpg"
            ),
            score = RecommendationScore(40, 1.0, 1.0, 0.0, 1.0, false),
            preselected = true
        )

        val preview = DeletionPlanner().buildPreview(listOf(recommendation))

        assertEquals(
            setOf("/storage/emulated/0/a.jpg", "/storage/emulated/0/c.jpg"),
            preview.map { it.path }.toSet()
        )
    }
    @Test
    fun duplicatePreviewBlocksAKeepChoiceThatRequiresReview() {
        val group = DuplicateGroup(
            id = "id",
            hash = "hash",
            candidates = listOf(
                FileCandidate("/storage/emulated/0/a.jpg", 10, 1),
                FileCandidate("/storage/emulated/0/b.jpg", 10, 2)
            ),
            keepCandidatePath = "/storage/emulated/0/a.jpg",
            recommendedKeepCandidatePath = "/storage/emulated/0/a.jpg",
            keepSelectionRequiresReview = true
        )
        val recommendation = CleanupRecommendation(
            id = "dup:id:members",
            type = RecommendationType.DUPLICATE_FILES,
            title = "Duplicates",
            reason = "Duplicate group",
            reclaimableBytes = 10,
            duplicateGroup = group,
            score = RecommendationScore(10, 1.0, 1.0, 0.0, 1.0, false),
            preselected = false
        )

        assertEquals(
            emptyList<DeletePreviewItem>(),
            DeletionPlanner().buildPreview(listOf(recommendation))
        )
    }

}
