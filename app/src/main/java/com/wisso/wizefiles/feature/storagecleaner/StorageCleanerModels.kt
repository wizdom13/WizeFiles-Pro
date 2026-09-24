// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.net.Uri

enum class AnalysisCategory {
    IMAGES,
    VIDEOS,
    AUDIO,
    DOWNLOADS,
    DOCUMENTS,
    ARCHIVES,
    APKS,
    APP_STORAGE,
    LARGE_FILES,
    DUPLICATES,
    JUNK
}

enum class StorageCompositionCategory {
    IMAGES,
    VIDEOS,
    AUDIO,
    DOCUMENTS,
    ARCHIVES,
    APKS,
    APP_STORAGE,
    OTHER
}

data class StorageCompositionSummary(
    val category: StorageCompositionCategory,
    val bytes: Long,
    val itemCount: Int
)

enum class RecommendationType {
    LARGE_FILE,
    DOWNLOADS,
    DUPLICATE_MEDIA,
    DUPLICATE_FILES,
    DUPLICATE,
    APK_FILE,
    JUNK,
    UNUSED_APP,
    STALE_FILE
}

data class RecommendationScore(
    val reclaimableBytes: Long,
    val confidence: Double,
    val safety: Double,
    val staleness: Double,
    val duplicateCertainty: Double,
    val ignored: Boolean
)

data class CleanupRecommendation(
    val id: String,
    val type: RecommendationType,
    val title: String,
    val reason: String,
    val reclaimableBytes: Long,
    val path: String? = null,
    val uri: Uri? = null,
    val packageName: String? = null,
    val duplicateGroup: DuplicateGroup? = null,
    val isDownloadRelated: Boolean = false,
    val score: RecommendationScore,
    val preselected: Boolean
)

data class StorageCleanerItemDetails(
    val title: String,
    val category: String,
    val reclaimableSize: String,
    val reason: String,
    val selectionImpact: String,
    val path: String?,
    val packageName: String?,
    val uri: String?,
    val safetyHint: String,
    val fields: List<StorageCleanerItemDetailsField>,
    val duplicateMembers: List<StorageCleanerDuplicateMemberDetails>,
    val actions: StorageCleanerItemDetailsActions
)

data class StorageCleanerItemDetailsField(
    val label: String,
    val value: String
)

data class StorageCleanerDuplicateMemberDetails(
    val path: String,
    val size: String,
    val modifiedTime: String?,
    val isKeepCandidate: Boolean,
    val isRecommendedCandidate: Boolean
)

data class StorageCleanerItemDetailsActions(
    val canOpenFile: Boolean,
    val canOpenFolder: Boolean,
    val canOpenAppInfo: Boolean
)

sealed interface StorageCleanerListItem {
    data class SectionHeader(
        val key: SectionKey,
        val title: String,
        val summary: String,
        val expanded: Boolean,
        val childRecommendationIds: List<String>,
        val areAllChildrenSelected: Boolean
    ) : StorageCleanerListItem

    data class RecommendationRow(
        val recommendation: CleanupRecommendation
    ) : StorageCleanerListItem
}

data class DuplicateGroup(
    val id: String,
    val hash: String,
    val candidates: List<FileCandidate>,
    val keepCandidatePath: String,
    val recommendedKeepCandidatePath: String = keepCandidatePath,
    val keepSelectionRequiresReview: Boolean = false
)

data class LargeFileCandidate(
    val path: String,
    val size: Long,
    val mimeType: String?,
    val modifiedTimeMillis: Long
)



data class AppStorageBytes(
    val appBytes: Long?,
    val cacheBytes: Long?,
    val dataBytes: Long?
)

data class UnusedAppCandidate(
    val packageName: String,
    val label: String,
    val lastTimeUsedMillis: Long?,
    val appBytes: Long?,
    val cacheBytes: Long?,
    val dataBytes: Long?
)

data class JunkCandidate(
    val path: String,
    val reason: String,
    val size: Long,
    val modifiedTimeMillis: Long
)

data class StorageScanResult(
    val files: List<java.io.File>,
    val scanWasTruncated: Boolean,
    val scannedFileCount: Int,
    val discoveredFileCountEstimate: Int
)

data class ScanProgress(
    val scannedItems: Int,
    val totalItemsEstimate: Int,
    val phase: String,
    val isFinished: Boolean
)

data class ScanFilters(
    val minLargeFileBytes: Long = 100L * 1024L * 1024L,
    val oldFileDays: Int = 120,
    val includeNearDuplicates: Boolean = false,
    val maxFilesToHash: Int = 3000,
    val minJunkFileAgeDays: Int = 7
)

data class DeletePreviewItem(
    val recommendationId: String,
    val path: String,
    val displayName: String,
    val bytes: Long,
    val reason: String,
    val duplicateGroupHash: String? = null,
    val keepCandidatePath: String? = null
)

data class FileCandidate(
    val path: String,
    val size: Long,
    val modifiedTimeMillis: Long,
    val width: Int = 0,
    val height: Int = 0
)

data class StorageAnalysisResult(
    val compositionCategories: List<StorageCompositionSummary>,
    val recommendations: List<CleanupRecommendation>,
    val progress: ScanProgress,
    val missingCapabilities: List<String>,
    val totalStorageBytes: Long = 0L,
    val scanWasTruncated: Boolean = false,
    val scannedFileCount: Int = progress.scannedItems,
    val discoveredFileCountEstimate: Int = progress.totalItemsEstimate
)

internal val CleanupRecommendation.isDeletionCandidate: Boolean
    get() = path != null ||
        (duplicateGroup != null && !duplicateGroup.keepSelectionRequiresReview)
