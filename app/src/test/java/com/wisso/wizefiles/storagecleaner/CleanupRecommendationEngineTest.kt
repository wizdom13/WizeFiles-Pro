package com.wisso.wizefiles.storagecleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CleanupRecommendationEngineTest {
    private val engine = CleanupRecommendationEngine()

    @Test
    fun duplicateRecommendationsAreRankedHigh() {
        val group = DuplicateGroup(
            id = "g1",
            hash = "h",
            candidates = listOf(
                FileCandidate("/a/1.jpg", 200, 100),
                FileCandidate("/a/2.jpg", 200, 90)
            ),
            keepCandidatePath = "/a/1.jpg"
        )
        val list = engine.build(
            largeFiles = emptyList(),
            downloadCandidates = emptyList(),
            duplicateGroups = listOf(group),
            apkCandidates = emptyList(),
            junkCandidates = emptyList(),
            unusedApps = emptyList(),
            ignoredIds = emptySet()
        )
        assertEquals(1, list.size)
        assertEquals(RecommendationType.DUPLICATE_MEDIA, list.first().type)
        assertTrue(list.first().preselected)
    }



    @Test
    fun duplicateGroupsAreSplitIntoMediaAndNonMediaSections() {
        val mediaGroup = DuplicateGroup(
            id = "media",
            hash = "h1",
            candidates = listOf(
                FileCandidate("/a/1.jpg", 300, 100),
                FileCandidate("/a/2.jpg", 300, 90)
            ),
            keepCandidatePath = "/a/1.jpg"
        )
        val fileGroup = DuplicateGroup(
            id = "files",
            hash = "h2",
            candidates = listOf(
                FileCandidate("/a/doc-1.pdf", 500, 120),
                FileCandidate("/a/doc-2.pdf", 500, 100),
                FileCandidate("/a/doc-3.pdf", 500, 80)
            ),
            keepCandidatePath = "/a/doc-1.pdf"
        )

        val list = engine.build(
            largeFiles = emptyList(),
            downloadCandidates = emptyList(),
            duplicateGroups = listOf(mediaGroup, fileGroup),
            apkCandidates = emptyList(),
            junkCandidates = emptyList(),
            unusedApps = emptyList(),
            ignoredIds = emptySet()
        )

        val media = list.first { it.id.startsWith("dup:media:") }
        val files = list.first { it.id.startsWith("dup:files:") }

        assertEquals(RecommendationType.DUPLICATE_MEDIA, media.type)
        assertTrue(media.reason.contains("keep best copy"))
        assertEquals(300, media.reclaimableBytes)

        assertEquals(RecommendationType.DUPLICATE_FILES, files.type)
        assertTrue(files.reason.contains("keep one copy"))
        assertEquals(1000, files.reclaimableBytes)
    }


    @Test
    fun downloadCandidatesProduceStaleRecommendationsMarkedAsDownloadRelated() {
        val list = engine.build(
            largeFiles = emptyList(),
            downloadCandidates = listOf(File("/storage/emulated/0/Download/old.zip")),
            duplicateGroups = emptyList(),
            apkCandidates = emptyList(),
            junkCandidates = emptyList(),
            unusedApps = emptyList(),
            ignoredIds = emptySet()
        )

        assertEquals(1, list.size)
        assertEquals(RecommendationType.STALE_FILE, list.first().type)
        assertTrue(list.first().isDownloadRelated)
    }

    @Test
    fun unusedAppRecommendationUsesApplicationLabelAsTitle() {
        val list = engine.build(
            largeFiles = emptyList(),
            downloadCandidates = emptyList(),
            duplicateGroups = emptyList(),
            apkCandidates = emptyList(),
            junkCandidates = emptyList(),
            unusedApps = listOf(
                UnusedAppCandidate(
                    packageName = "com.example.reader",
                    label = "Example Reader",
                    lastTimeUsedMillis = 1L,
                    appBytes = 100L,
                    cacheBytes = 20L,
                    dataBytes = 30L
                )
            ),
            ignoredIds = emptySet()
        )

        assertEquals("Example Reader", list.single().title)
        assertEquals(RecommendationType.UNUSED_APP, list.single().type)
    }

    @Test
    fun ignoredRecommendationsRemainAvailableForRestoreButAreNotPreselected() {
        val list = engine.build(
            largeFiles = listOf(LargeFileCandidate("/tmp/a.bin", 1024, null, 0)),
            downloadCandidates = emptyList(),
            duplicateGroups = emptyList(),
            apkCandidates = emptyList(),
            junkCandidates = emptyList(),
            unusedApps = emptyList(),
            ignoredIds = setOf("large:/tmp/a.bin")
        )
        assertEquals(1, list.size)
        assertTrue(list.single().score.ignored)
        assertTrue(!list.single().preselected)
    }


    @Test
    fun duplicateRecommendationIdChangesWhenMembershipChanges() {
        fun recommendationId(paths: List<String>): String {
            val group = DuplicateGroup(
                id = "same-content",
                hash = "hash",
                candidates = paths.mapIndexed { index, path ->
                    FileCandidate(path, 10L, index.toLong())
                },
                keepCandidatePath = paths.first()
            )
            return engine.build(
                largeFiles = emptyList(),
                downloadCandidates = emptyList(),
                duplicateGroups = listOf(group),
                apkCandidates = emptyList(),
                junkCandidates = emptyList(),
                unusedApps = emptyList(),
                ignoredIds = emptySet()
            ).single().id
        }

        val original = recommendationId(listOf("/a/one.jpg", "/a/two.jpg"))
        val changed = recommendationId(
            listOf("/a/one.jpg", "/a/two.jpg", "/a/three.jpg")
        )

        assertTrue(original.startsWith("dup:same-content:"))
        assertTrue(changed.startsWith("dup:same-content:"))
        assertTrue(original != changed)
    }

    @Test
    fun largeFileRecommendationsUseActualFileNameAsTitle() {
        val list = engine.build(
            largeFiles = listOf(LargeFileCandidate("/storage/emulated/0/Movies/big-video.mp4", 4096, null, 0)),
            downloadCandidates = emptyList(),
            duplicateGroups = emptyList(),
            apkCandidates = emptyList(),
            junkCandidates = emptyList(),
            unusedApps = emptyList(),
            ignoredIds = emptySet()
        )

        assertEquals(1, list.size)
        assertEquals(File("/storage/emulated/0/Movies/big-video.mp4").name, list.first().title)
        assertEquals(RecommendationType.LARGE_FILE, list.first().type)
    }

    @Test
    fun apkRecommendationsAreSeparatedFromDownloads() {
        val list = engine.build(
            largeFiles = emptyList(),
            downloadCandidates = emptyList(),
            duplicateGroups = emptyList(),
            apkCandidates = listOf(
                File("/storage/emulated/0/Download/installer.apkm"),
                File("/storage/emulated/0/Packages/game.xapk")
            ),
            junkCandidates = emptyList(),
            unusedApps = emptyList(),
            ignoredIds = emptySet()
        )

        assertEquals(2, list.size)
        assertTrue(list.all { it.type == RecommendationType.APK_FILE })
        assertTrue(list.all { it.id.startsWith("apk:") })
    }
}
