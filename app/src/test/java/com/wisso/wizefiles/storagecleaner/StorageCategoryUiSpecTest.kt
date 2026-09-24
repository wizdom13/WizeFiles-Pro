package com.wisso.wizefiles.storagecleaner

import com.wisso.wizefiles.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCategoryUiSpecTest {

    @Test
    fun formatDisplayNameCapitalizesTokens() {
        assertEquals("Images", formatDisplayName("images"))
        assertEquals("App Storage", formatDisplayName("app_storage"))
        assertEquals("Large Files", formatDisplayName(" large   files "))
    }

    @Test
    fun buildCompositionSegmentsUsesStableOrderAndOmitsZeroByteCategories() {
        val segments = buildCompositionSegments(
            categories = listOf(
                StorageCompositionSummary(StorageCompositionCategory.APP_STORAGE, 0, 0),
                StorageCompositionSummary(StorageCompositionCategory.IMAGES, 300, 10),
                StorageCompositionSummary(StorageCompositionCategory.OTHER, 200, 3),
                StorageCompositionSummary(StorageCompositionCategory.APKS, 500, 2)
            ),
            totalStorageBytes = 2_000
        )

        assertEquals(
            listOf(StorageCompositionCategory.IMAGES, StorageCompositionCategory.APKS, StorageCompositionCategory.OTHER),
            segments.map { it.category }
        )
        assertEquals(3, segments.size)
        assertTrue(segments.none { it.category == StorageCompositionCategory.APP_STORAGE })
        assertEquals(0.5f, segments.sumOf { it.fraction.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun buildCategorySummaryRowsOmitsZeroOnlyRowsAndUsesCategoryColors() {
        val rows = buildCategorySummaryRows(
            categories = listOf(
                StorageCompositionSummary(StorageCompositionCategory.IMAGES, 1024, 2),
                StorageCompositionSummary(StorageCompositionCategory.APKS, 0, 0),
                StorageCompositionSummary(StorageCompositionCategory.VIDEOS, 256, 1)
            ),
            formatSize = { "${it}B" }
        )

        assertEquals(listOf(StorageCompositionCategory.IMAGES, StorageCompositionCategory.VIDEOS), rows.map { it.category })
        assertEquals("Images: 1024B (2)", rows[0].text)
        assertEquals(R.color.storage_category_images, rows[0].colorRes)
        assertEquals(R.color.storage_category_videos, rows[1].colorRes)
    }

    @Test
    fun recommendationDotColorResMapsSectionsToCleanupPaletteColors() {
        val duplicate = recommendation(RecommendationType.DUPLICATE_MEDIA)
        val oldDownloads = recommendation(RecommendationType.DOWNLOADS)
        val staleOldFile = recommendation(RecommendationType.STALE_FILE, path = "/storage/emulated/0/Documents/old.pdf")

        assertEquals(R.color.storage_cleanup_duplicate_media, recommendationDotColorRes(duplicate))
        assertEquals(R.color.storage_cleanup_old_downloads, recommendationDotColorRes(oldDownloads))
        assertEquals(R.color.storage_cleanup_old_files, recommendationDotColorRes(staleOldFile))
    }


    @Test
    fun cleanupSectionColorResMapsExpectedSections() {
        assertEquals(R.color.storage_cleanup_duplicate_media, cleanupSectionColorRes(SectionKey.DUPLICATE_MEDIA))
        assertEquals(R.color.storage_cleanup_duplicate_files, cleanupSectionColorRes(SectionKey.DUPLICATE_FILES))
        assertEquals(R.color.storage_cleanup_large_files, cleanupSectionColorRes(SectionKey.LARGE_FILES))
        assertEquals(R.color.storage_cleanup_apk_files, cleanupSectionColorRes(SectionKey.APK_FILES))
        assertEquals(R.color.storage_cleanup_unused_apps, cleanupSectionColorRes(SectionKey.UNUSED_APPS))
        assertEquals(R.color.storage_cleanup_old_downloads, cleanupSectionColorRes(SectionKey.OLD_DOWNLOADS))
        assertEquals(R.color.storage_cleanup_junk, cleanupSectionColorRes(SectionKey.JUNK_FILES))
        assertEquals(R.color.storage_cleanup_old_files, cleanupSectionColorRes(SectionKey.OLD_FILES))
        assertEquals(R.color.storage_cleanup_other, cleanupSectionColorRes(SectionKey.OTHER))
    }

    @Test
    fun compositionOtherColorResDiffersFromRemainderBackgroundColor() {
        assertEquals(R.color.storage_category_other, StorageCompositionCategory.OTHER.colorRes())
        assertTrue(StorageCompositionCategory.OTHER.colorRes() != R.color.storage_category_composition_remainder)
    }

    private fun recommendation(type: RecommendationType, path: String? = null): CleanupRecommendation {
        return CleanupRecommendation(
            id = type.name,
            type = type,
            title = "title",
            reason = "reason",
            reclaimableBytes = 10,
            path = path,
            score = RecommendationScore(10, 0.5, 0.5, 0.5, 0.5, false),
            preselected = false
        )
    }
}
