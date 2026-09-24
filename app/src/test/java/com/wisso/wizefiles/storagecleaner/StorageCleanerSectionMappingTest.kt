package com.wisso.wizefiles.storagecleaner

import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.iconRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCleanerSectionMappingTest {
    @Test
    fun sectionKeyMapsRecommendationTypes() {
        assertEquals(SectionKey.DUPLICATE_MEDIA, recommendation(RecommendationType.DUPLICATE_MEDIA).toSectionKey())
        assertEquals(SectionKey.DUPLICATE_FILES, recommendation(RecommendationType.DUPLICATE_FILES).toSectionKey())
        assertEquals(SectionKey.LARGE_FILES, recommendation(RecommendationType.LARGE_FILE).toSectionKey())
        assertEquals(SectionKey.APK_FILES, recommendation(RecommendationType.APK_FILE).toSectionKey())
        assertEquals(SectionKey.UNUSED_APPS, recommendation(RecommendationType.UNUSED_APP).toSectionKey())
        assertEquals(SectionKey.OLD_DOWNLOADS, recommendation(RecommendationType.DOWNLOADS).toSectionKey())
        assertEquals(SectionKey.JUNK_FILES, recommendation(RecommendationType.JUNK).toSectionKey())
    }


    @Test
    fun staleFileUsesExplicitDownloadSignalForSectionMapping() {
        val staleFromDownload = recommendation(
            RecommendationType.STALE_FILE,
            path = "/storage/emulated/0/Documents/old.bin",
            isDownloadRelated = true
        )
        val staleNormal = recommendation(
            RecommendationType.STALE_FILE,
            path = "/storage/emulated/0/Documents/old.bin",
            isDownloadRelated = false
        )

        assertEquals(SectionKey.OLD_DOWNLOADS, staleFromDownload.toSectionKey())
        assertEquals(SectionKey.OLD_FILES, staleNormal.toSectionKey())
    }

    @Test
    fun toSectionedListDefaultsToCollapsedHeadersOnly() {
        val listItems = listOf(
            recommendation(RecommendationType.JUNK, id = "junk", reclaimableBytes = 20),
            recommendation(RecommendationType.DUPLICATE_MEDIA, id = "dup", reclaimableBytes = 10)
        ).toSectionedList(expandedSections = emptyMap(), selectedIds = emptySet()) { recs -> "${recs.size} items" }

        val headers = listItems.filterIsInstance<StorageCleanerListItem.SectionHeader>()
        assertEquals(listOf("Duplicate media", "Junk files"), headers.map { it.title })
        assertTrue(listItems.all { it is StorageCleanerListItem.SectionHeader })
        assertTrue(headers.all { !it.expanded })
    }

    @Test
    fun toSectionedListComputesSectionSelectionState() {
        val ids = listOf("dup-1", "dup-2")
        val items = ids.map {
            recommendation(
                RecommendationType.LARGE_FILE,
                id = it,
                path = "/storage/emulated/0/$it.bin"
            )
        }.toSectionedList(expandedSections = emptyMap(), selectedIds = setOf("dup-1")) { recs -> "${recs.size} items" }
        val header = items.first() as StorageCleanerListItem.SectionHeader
        assertFalse(header.areAllChildrenSelected)

        val allSelectedHeader = ids.map {
            recommendation(
                RecommendationType.LARGE_FILE,
                id = it,
                path = "/storage/emulated/0/$it.bin"
            )
        }.toSectionedList(expandedSections = emptyMap(), selectedIds = ids.toSet()) { recs -> "${recs.size} items" }
            .first() as StorageCleanerListItem.SectionHeader
        assertTrue(allSelectedHeader.areAllChildrenSelected)
        assertEquals(ids, allSelectedHeader.childRecommendationIds)
    }



    @Test
    fun unusedAppsAreActionOnlyAndExcludedFromSectionSelection() {
        val items = listOf(
            recommendation(RecommendationType.UNUSED_APP, id = "app")
                .copy(packageName = "com.example.unused")
        ).toSectionedList(
            expandedSections = emptyMap(),
            selectedIds = setOf("app")
        ) { recs -> "${recs.size} items" }

        val header = items.single() as StorageCleanerListItem.SectionHeader
        assertTrue(header.childRecommendationIds.isEmpty())
        assertFalse(header.areAllChildrenSelected)
    }

    @Test
    fun toSectionedListUsesExpectedSectionOrderIncludingApkAndDuplicateFiles() {
        val listItems = listOf(
            recommendation(RecommendationType.UNUSED_APP, id = "app"),
            recommendation(RecommendationType.DOWNLOADS, id = "downloads"),
            recommendation(RecommendationType.DUPLICATE_FILES, id = "dup-files"),
            recommendation(RecommendationType.DUPLICATE_MEDIA, id = "dup-media"),
            recommendation(RecommendationType.APK_FILE, id = "apk"),
            recommendation(RecommendationType.LARGE_FILE, id = "large")
        ).toSectionedList(expandedSections = emptyMap(), selectedIds = emptySet()) { recs -> "${recs.size} items" }

        val headers = listItems.filterIsInstance<StorageCleanerListItem.SectionHeader>()
        assertEquals(
            listOf("Duplicate media", "Duplicate files", "Large files", "APK files", "Unused apps", "Old downloads"),
            headers.map { it.title }
        )
    }

    @Test
    fun toSectionedListShowsRowsForExpandedSectionsOnly() {
        val listItems = listOf(
            recommendation(RecommendationType.JUNK, id = "junk", reclaimableBytes = 20),
            recommendation(RecommendationType.DUPLICATE_MEDIA, id = "dup", reclaimableBytes = 10)
        ).toSectionedList(expandedSections = mapOf(SectionKey.DUPLICATE_MEDIA to true), selectedIds = emptySet()) { recs -> "${recs.size} items" }

        assertTrue(listItems.first() is StorageCleanerListItem.SectionHeader)
        assertTrue(listItems[1] is StorageCleanerListItem.RecommendationRow)
        assertEquals("dup", (listItems[1] as StorageCleanerListItem.RecommendationRow).recommendation.id)
    }

    @Test
    fun visualSpecFallsBackToIconsWhenPreviewUnavailable() {
        val duplicateSpec = recommendationVisualSpec(recommendation(RecommendationType.DUPLICATE_MEDIA))
        assertEquals(R.drawable.ic_bs_images_24dp, duplicateSpec.fallbackIconRes)
        assertFalse(duplicateSpec.shouldUsePackageIcon)

        val appSpec = recommendationVisualSpec(
            recommendation(RecommendationType.UNUSED_APP).copy(packageName = "com.example.app")
        )
        assertTrue(appSpec.shouldUsePackageIcon)
        assertEquals(R.drawable.ic_bs_app_indicator_24dp, appSpec.fallbackIconRes)
    }

    @Test
    fun iconMappingUsesExpectedDefaults() {
        assertEquals(R.drawable.ic_file_type_generic, iconForRecommendation(recommendation(RecommendationType.LARGE_FILE)))
        assertEquals(R.drawable.ic_bs_app_indicator_24dp, iconForRecommendation(recommendation(RecommendationType.UNUSED_APP)))
        assertEquals(R.drawable.ic_bs_download_24dp, iconForRecommendation(recommendation(RecommendationType.DOWNLOADS)))
        assertEquals(R.drawable.ic_bs_images_24dp, iconForRecommendation(recommendation(RecommendationType.DUPLICATE_MEDIA)))
        assertEquals(R.drawable.ic_file_type_generic, iconForRecommendation(recommendation(RecommendationType.DUPLICATE_FILES)))
        assertEquals(R.drawable.ic_bs_trash_24dp, iconForRecommendation(recommendation(RecommendationType.JUNK)))
    }

    @Test
    fun storageCleanerFileVisualMapsCommonTypesToFileTypeIcons() {
        val movieVisual = storageCleanerFileVisual("/tmp/movie.mp4")!!
        val apkVisual = storageCleanerFileVisual("/tmp/installer.apk")!!

        assertEquals(fallbackMimeTypeForPath("movie.mp4").iconRes, movieVisual.fallbackIconRes)
        assertEquals(fallbackMimeTypeForPath("archive.zip").iconRes, storageCleanerFileVisual("/tmp/archive.zip")!!.fallbackIconRes)
        assertEquals(fallbackMimeTypeForPath("doc.pdf").iconRes, storageCleanerFileVisual("/tmp/doc.pdf")!!.fallbackIconRes)
        assertEquals(fallbackMimeTypeForPath("installer.apk").iconRes, apkVisual.fallbackIconRes)
        assertFalse(movieVisual.isAppIcon)
        assertTrue(apkVisual.isAppIcon)
    }

    @Test
    fun storageCleanerFileVisualReturnsNullWhenPathMissing() {
        assertNull(storageCleanerFileVisual(null))
        assertNull(storageCleanerFileVisual("   "))
    }

    @Test
    fun storageCleanerFileVisualUsesImageFallbackWithoutAndroidAppInitialization() {
        val visual = storageCleanerFileVisual("/tmp/nonexistent-sample.jpg")

        assertNotNull(visual)
        assertEquals(fallbackMimeTypeForPath("nonexistent-sample.jpg").iconRes, visual!!.fallbackIconRes)
        assertFalse(visual.shouldLoadPreview)
        assertNull(visual.requestData)
    }

    private fun recommendation(
        type: RecommendationType,
        id: String = type.name,
        reclaimableBytes: Long = 1,
        path: String? = null,
        isDownloadRelated: Boolean = false
    ): CleanupRecommendation {
        return CleanupRecommendation(
            id = id,
            type = type,
            title = "title-$id",
            reason = "reason-$id",
            reclaimableBytes = reclaimableBytes,
            path = path,
            isDownloadRelated = isDownloadRelated,
            score = RecommendationScore(reclaimableBytes, 0.5, 0.5, 0.5, 0.5, false),
            preselected = false
        )
    }
}
