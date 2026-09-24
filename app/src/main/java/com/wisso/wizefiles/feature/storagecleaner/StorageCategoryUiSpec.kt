// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import com.wisso.wizefiles.R
import java.util.Locale

private data class CompositionUiSpec(
    val category: StorageCompositionCategory,
    val displayName: String,
    val colorRes: Int
)

internal data class CategoryCompositionSegment(
    val category: StorageCompositionCategory,
    val displayName: String,
    val bytes: Long,
    val fraction: Float,
    val colorRes: Int
)


internal data class CategorySummaryRow(
    val category: StorageCompositionCategory,
    val text: String,
    val colorRes: Int
)

private val orderedCompositionSpecs = listOf(
    CompositionUiSpec(StorageCompositionCategory.IMAGES, "Images", R.color.storage_category_images),
    CompositionUiSpec(StorageCompositionCategory.VIDEOS, "Videos", R.color.storage_category_videos),
    CompositionUiSpec(StorageCompositionCategory.AUDIO, "Audio", R.color.storage_category_audio),
    CompositionUiSpec(StorageCompositionCategory.DOCUMENTS, "Documents", R.color.storage_category_documents),
    CompositionUiSpec(StorageCompositionCategory.ARCHIVES, "Archives", R.color.storage_category_archives),
    CompositionUiSpec(StorageCompositionCategory.APKS, "APKs", R.color.storage_category_apks),
    CompositionUiSpec(StorageCompositionCategory.APP_STORAGE, "App storage", R.color.storage_category_app_storage),
    CompositionUiSpec(StorageCompositionCategory.OTHER, "Other", R.color.storage_category_other)
)

private val compositionSpecsByCategory = orderedCompositionSpecs.associateBy { it.category }

internal fun StorageCompositionCategory.displayName(): String =
    compositionSpecsByCategory[this]?.displayName ?: formatDisplayName(name)

internal fun StorageCompositionCategory.colorRes(): Int =
    compositionSpecsByCategory[this]?.colorRes ?: R.color.storage_category_other

internal fun formatDisplayName(raw: String): String {
    return raw
        .trim()
        .replace('_', ' ')
        .lowercase(Locale.getDefault())
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { token -> token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
}

internal fun buildCategorySummaryRows(
    categories: List<StorageCompositionSummary>,
    formatSize: (Long) -> String
): List<CategorySummaryRow> {
    return categories
        .filter { it.bytes > 0L || it.itemCount > 0 }
        .sortedBy { orderedCompositionSpecs.indexOfFirst { spec -> spec.category == it.category }.let { idx -> if (idx >= 0) idx else Int.MAX_VALUE } }
        .map {
            CategorySummaryRow(
                category = it.category,
                text = "${it.category.displayName()}: ${formatSize(it.bytes)} (${it.itemCount})",
                colorRes = it.category.colorRes()
            )
        }
}


internal fun buildCompositionSegments(
    categories: List<StorageCompositionSummary>,
    totalStorageBytes: Long
): List<CategoryCompositionSegment> {
    if (totalStorageBytes <= 0L) return emptyList()
    val positiveByCategory = categories.filter { it.bytes > 0L }.associateBy { it.category }
    if (positiveByCategory.isEmpty()) return emptyList()

    val ordered = orderedCompositionSpecs.mapNotNull { spec ->
        val summary = positiveByCategory[spec.category] ?: return@mapNotNull null
        summary to spec
    }

    return ordered.map { (summary, spec) ->
        CategoryCompositionSegment(
            category = summary.category,
            displayName = spec.displayName,
            bytes = summary.bytes,
            fraction = (summary.bytes.toDouble() / totalStorageBytes.toDouble()).toFloat().coerceIn(0f, 1f),
            colorRes = spec.colorRes
        )
    }
}

internal fun cleanupSectionColorRes(sectionKey: SectionKey): Int {
    return when (sectionKey) {
        SectionKey.DUPLICATE_MEDIA -> R.color.storage_cleanup_duplicate_media
        SectionKey.DUPLICATE_FILES -> R.color.storage_cleanup_duplicate_files
        SectionKey.LARGE_FILES -> R.color.storage_cleanup_large_files
        SectionKey.APK_FILES -> R.color.storage_cleanup_apk_files
        SectionKey.UNUSED_APPS -> R.color.storage_cleanup_unused_apps
        SectionKey.OLD_DOWNLOADS -> R.color.storage_cleanup_old_downloads
        SectionKey.JUNK_FILES -> R.color.storage_cleanup_junk
        SectionKey.OLD_FILES -> R.color.storage_cleanup_old_files
        SectionKey.OTHER -> R.color.storage_cleanup_other
    }
}

internal fun recommendationDotColorRes(item: CleanupRecommendation): Int {
    return cleanupSectionColorRes(item.toSectionKey())
}
