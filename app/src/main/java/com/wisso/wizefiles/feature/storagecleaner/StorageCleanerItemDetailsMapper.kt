// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.content.Context
import android.text.format.DateUtils
import com.wisso.wizefiles.R
import java.io.File


internal fun selectionImpactText(context: Context, reclaimableSize: String): String =
    context.getString(R.string.storage_cleaner_details_selection_impact, reclaimableSize)

internal fun buildDetailsActions(
    recommendation: CleanupRecommendation,
    path: String?
): StorageCleanerItemDetailsActions {
    val hasFilePath = !path.isNullOrBlank()
    val isAppItem = !recommendation.packageName.isNullOrBlank()
    return StorageCleanerItemDetailsActions(
        canOpenFile = hasFilePath && !isAppItem,
        canOpenFolder = hasFilePath && !isAppItem,
        canOpenAppInfo = isAppItem
    )
}

internal object StorageCleanerItemDetailsMapper {
    fun map(context: Context, recommendation: CleanupRecommendation): StorageCleanerItemDetails {
        val path = recommendation.path ?: recommendation.duplicateGroup?.keepCandidatePath
        val reclaimableSize = android.text.format.Formatter.formatFileSize(context, recommendation.reclaimableBytes)
        val summaryFields = buildSummaryFields(context, recommendation, path)
        val duplicateMembers = buildDuplicateMembers(context, recommendation)
        return StorageCleanerItemDetails(
            title = buildTitle(context, recommendation),
            category = recommendation.toSectionKey().localizedTitle(context),
            reclaimableSize = reclaimableSize,
            reason = recommendation.reason,
            selectionImpact = selectionImpactText(context, reclaimableSize),
            path = path,
            packageName = recommendation.packageName,
            uri = recommendation.uri?.toString(),
            safetyHint = buildSafetyHint(context, recommendation),
            fields = summaryFields,
            duplicateMembers = duplicateMembers,
            actions = buildDetailsActions(recommendation, path)
        )
    }

    private fun buildTitle(
        context: Context,
        recommendation: CleanupRecommendation
    ): String {
        return when (recommendation.type) {
            RecommendationType.UNUSED_APP -> recommendation.title.ifBlank {
                recommendation.packageName
                    ?: context.getString(R.string.storage_cleaner_unused_app_title)
            }
            RecommendationType.DUPLICATE_MEDIA, RecommendationType.DUPLICATE ->
                context.getString(R.string.storage_cleaner_duplicate_media_group_title)
            RecommendationType.DUPLICATE_FILES ->
                context.getString(R.string.storage_cleaner_duplicate_file_group_title)
            else -> recommendation.path
                ?.substringAfterLast('/')
                ?.ifBlank { recommendation.title }
                .orEmpty()
                .ifBlank { recommendation.title }
        }
    }

    private fun buildSummaryFields(
        context: Context,
        recommendation: CleanupRecommendation,
        path: String?
    ): List<StorageCleanerItemDetailsField> {
        val fields = mutableListOf<StorageCleanerItemDetailsField>()
        val isDuplicateGroup = recommendation.duplicateGroup != null

        if (!isDuplicateGroup) {
            path?.let { rawPath ->
                val folder = File(rawPath).parent
                if (!folder.isNullOrBlank()) {
                    fields += StorageCleanerItemDetailsField(
                        context.getString(R.string.file_type_name_directory),
                        folder
                    )
                }
            }
        }
        recommendation.packageName?.let {
            fields += StorageCleanerItemDetailsField(
                context.getString(R.string.file_properties_apk_package_name),
                it
            )
        }
        recommendation.uri?.toString()?.let {
            fields += StorageCleanerItemDetailsField(
                context.getString(R.string.storage_edit_document_tree_uri),
                it
            )
        }

        when (recommendation.type) {
            RecommendationType.LARGE_FILE,
            RecommendationType.DOWNLOADS,
            RecommendationType.APK_FILE,
            RecommendationType.JUNK,
            RecommendationType.STALE_FILE -> {
                detectModifiedTimeMillis(recommendation)?.let {
                    fields += StorageCleanerItemDetailsField(
                        context.getString(R.string.storage_cleaner_field_last_modified),
                        formatDateTime(context, it)
                    )
                }
                val extension = recommendation.path
                    ?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotBlank() }
                    ?.uppercase()
                if (
                    recommendation.type == RecommendationType.LARGE_FILE ||
                    recommendation.type == RecommendationType.APK_FILE
                ) {
                    extension?.let {
                        fields += StorageCleanerItemDetailsField(
                            context.getString(R.string.storage_cleaner_field_file_type),
                            it
                        )
                    }
                }
                if (
                    recommendation.type == RecommendationType.DOWNLOADS ||
                    recommendation.type == RecommendationType.STALE_FILE
                ) {
                    val inDownloads =
                        recommendation.path?.contains("/Download", ignoreCase = true) == true
                    fields += StorageCleanerItemDetailsField(
                        context.getString(R.string.storage_cleaner_field_location),
                        context.getString(
                            if (inDownloads) {
                                R.string.navigation_standard_directory_downloads
                            } else {
                                R.string.storage_cleaner_location_outside_downloads
                            }
                        )
                    )
                }
            }

            RecommendationType.UNUSED_APP -> Unit

            RecommendationType.DUPLICATE_MEDIA,
            RecommendationType.DUPLICATE_FILES,
            RecommendationType.DUPLICATE -> Unit
        }
        return fields
    }

    private fun detectModifiedTimeMillis(recommendation: CleanupRecommendation): Long? {
        val fromDuplicate = recommendation.duplicateGroup?.candidates?.firstOrNull { it.path == recommendation.path }?.modifiedTimeMillis
            ?: recommendation.duplicateGroup?.candidates?.firstOrNull()?.modifiedTimeMillis
        if (fromDuplicate != null && fromDuplicate > 0) return fromDuplicate
        val fromFile = recommendation.path?.let { File(it) }
            ?.takeIf { it.exists() }
            ?.lastModified()
            ?.takeIf { it > 0 }
        return fromFile
    }

    private fun buildDuplicateMembers(
        context: Context,
        recommendation: CleanupRecommendation
    ): List<StorageCleanerDuplicateMemberDetails> {
        val group = recommendation.duplicateGroup ?: return emptyList()
        return group.candidates
            .sortedByDescending { it.path == group.keepCandidatePath }
            .map { candidate ->
                StorageCleanerDuplicateMemberDetails(
                    path = candidate.path,
                    size = android.text.format.Formatter.formatFileSize(context, candidate.size),
                    modifiedTime = candidate.modifiedTimeMillis.takeIf { it > 0 }
                        ?.let { formatDateTime(context, it) },
                    isKeepCandidate = candidate.path == group.keepCandidatePath,
                    isRecommendedCandidate = candidate.path == group.recommendedKeepCandidatePath
                )
            }
    }

    private fun buildSafetyHint(
        context: Context,
        recommendation: CleanupRecommendation
    ): String {
        return context.getString(
            when (recommendation.type) {
                RecommendationType.UNUSED_APP ->
                    R.string.storage_cleaner_safety_unused_app
                RecommendationType.DUPLICATE_MEDIA,
                RecommendationType.DUPLICATE ->
                    R.string.storage_cleaner_safety_duplicate_media
                RecommendationType.DUPLICATE_FILES ->
                    R.string.storage_cleaner_safety_duplicate_files
                RecommendationType.APK_FILE ->
                    R.string.storage_cleaner_safety_apk
                RecommendationType.LARGE_FILE ->
                    R.string.storage_cleaner_safety_large_file
                RecommendationType.DOWNLOADS,
                RecommendationType.STALE_FILE,
                RecommendationType.JUNK ->
                    R.string.storage_cleaner_safety_file
            }
        )
    }

    private fun formatDateTime(context: Context, timeMillis: Long): String {
        return DateUtils.formatDateTime(
            context,
            timeMillis,
            DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_SHOW_TIME or
                DateUtils.FORMAT_ABBREV_MONTH
        )
    }

}
