// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.content.Context
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.mime.guessFromPath
import com.wisso.wizefiles.core.files.mime.isImage
import com.wisso.wizefiles.core.files.mime.isMedia
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class CleanupRecommendationEngine(
    private val strings: RecommendationStrings = RecommendationStrings.default()
) {
    fun build(
        largeFiles: List<LargeFileCandidate>,
        downloadCandidates: List<File>,
        duplicateGroups: List<DuplicateGroup>,
        apkCandidates: List<File>,
        junkCandidates: List<JunkCandidate>,
        unusedApps: List<UnusedAppCandidate>,
        ignoredIds: Set<String>
    ): List<CleanupRecommendation> {
        val now = System.currentTimeMillis()
        val output = mutableListOf<CleanupRecommendation>()
        largeFiles.take(60).forEach { file ->
            val staleDays = TimeUnit.MILLISECONDS.toDays(now - file.modifiedTimeMillis).coerceAtLeast(0)
            val id = "large:${file.path}"
            val score = RecommendationScore(file.size, 0.95, 0.85, staleDays / 365.0, 0.0, id in ignoredIds)
            val fileName = File(file.path).name.ifBlank { file.path }
            output += CleanupRecommendation(
                id = id,
                type = RecommendationType.LARGE_FILE,
                title = fileName,
                reason = strings.largeFileReason(staleDays),
                reclaimableBytes = file.size,
                path = file.path,
                score = score,
                preselected = score.confidence >= 0.9 && score.safety >= 0.8 && !score.ignored
            )
        }
        duplicateGroups.forEach { group ->
            val reclaimable = group.candidates.filterNot { it.path == group.keepCandidatePath }.sumOf { it.size }
            val id = duplicateRecommendationId(group)
            val score = RecommendationScore(reclaimable, 0.99, 0.9, 0.5, 1.0, id in ignoredIds)
            val sectionType = if (isMediaDuplicateGroup(group)) RecommendationType.DUPLICATE_MEDIA else RecommendationType.DUPLICATE_FILES
            output += CleanupRecommendation(
                id = id,
                type = sectionType,
                title = if (sectionType == RecommendationType.DUPLICATE_MEDIA) {
                    strings.duplicateMediaTitle
                } else {
                    strings.duplicateFilesTitle
                },
                reason = if (sectionType == RecommendationType.DUPLICATE_MEDIA) {
                    strings.duplicateBestCopyReason(group.candidates.size)
                } else {
                    strings.duplicateOneCopyReason(group.candidates.size)
                },
                reclaimableBytes = reclaimable,
                duplicateGroup = group,
                score = score,
                preselected = !score.ignored
            )
        }
        downloadCandidates.forEach { file ->
            val id = "downloads:${file.path}"
            val score = RecommendationScore(file.length(), 0.9, 0.75, 0.8, 0.0, id in ignoredIds)
            output += CleanupRecommendation(
                id = id,
                type = RecommendationType.STALE_FILE,
                title = strings.oldDownloadTitle,
                reason = strings.oldDownloadReason,
                reclaimableBytes = file.length(),
                path = file.path,
                isDownloadRelated = true,
                score = score,
                preselected = !score.ignored
            )
        }
        apkCandidates.forEach { file ->
            val id = "apk:${file.path}"
            val size = file.length()
            val score = RecommendationScore(size, 0.92, 0.85, 0.7, 0.0, id in ignoredIds)
            output += CleanupRecommendation(
                id = id,
                type = RecommendationType.APK_FILE,
                title = file.name.ifBlank { strings.apkPackageTitle },
                reason = strings.apkReason,
                reclaimableBytes = size,
                path = file.path,
                score = score,
                preselected = !score.ignored
            )
        }
        junkCandidates.forEach { candidate ->
            val id = "junk:${candidate.path}"
            val score = RecommendationScore(candidate.size, 0.93, 0.8, 0.75, 0.0, id in ignoredIds)
            output += CleanupRecommendation(
                id = id,
                type = RecommendationType.JUNK,
                title = strings.junkCandidateTitle,
                reason = candidate.reason,
                reclaimableBytes = candidate.size,
                path = candidate.path,
                score = score,
                preselected = !score.ignored
            )
        }
        unusedApps.forEach { app ->
            val bytes = (app.appBytes ?: 0) + (app.cacheBytes ?: 0) + (app.dataBytes ?: 0)
            val id = "app:${app.packageName}"
            val score = RecommendationScore(bytes, 0.85, 0.7, 0.9, 0.0, id in ignoredIds)
            output += CleanupRecommendation(
                id = id,
                type = RecommendationType.UNUSED_APP,
                title = app.label,
                reason = strings.unusedAppReason(app.label),
                reclaimableBytes = bytes,
                packageName = app.packageName,
                score = score,
                preselected = false
            )
        }
        return output.sortedByDescending { it.reclaimableBytes }
    }

    private fun duplicateRecommendationId(group: DuplicateGroup): String {
        val membership = group.candidates.map { it.path }.sorted().joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(membership.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "dup:${group.id}:$digest"
    }

    private fun isMediaDuplicateGroup(group: DuplicateGroup): Boolean {
        return group.candidates.all { candidate ->
            val detected = runCatching { MimeType.guessFromPath(candidate.path) }
                .getOrDefault(MimeType.GENERIC)
            val mimeType = if (detected == MimeType.GENERIC) {
                fallbackMimeTypeForPath(candidate.path)
            } else {
                detected
            }
            mimeType.isImage || mimeType.isMedia
        }
    }
}

data class RecommendationStrings(
    val duplicateMediaTitle: String,
    val duplicateFilesTitle: String,
    val oldDownloadTitle: String,
    val oldDownloadReason: String,
    val apkPackageTitle: String,
    val apkReason: String,
    val junkCandidateTitle: String,
    val unusedAppTitle: String,
    val largeFileReason: (Long) -> String,
    val duplicateBestCopyReason: (Int) -> String,
    val duplicateOneCopyReason: (Int) -> String,
    val unusedAppReason: (String) -> String
) {
    companion object {
        fun fromContext(context: Context): RecommendationStrings = RecommendationStrings(
            duplicateMediaTitle = context.getString(R.string.storage_cleaner_duplicate_media_title),
            duplicateFilesTitle = context.getString(R.string.storage_cleaner_duplicate_files_title),
            oldDownloadTitle = context.getString(R.string.storage_cleaner_old_download_title),
            oldDownloadReason = context.getString(R.string.storage_cleaner_reason_old_download),
            apkPackageTitle = context.getString(R.string.storage_cleaner_apk_package_title),
            apkReason = context.getString(R.string.storage_cleaner_reason_apk),
            junkCandidateTitle = context.getString(R.string.storage_cleaner_junk_candidate_title),
            unusedAppTitle = context.getString(R.string.storage_cleaner_unused_app_title),
            largeFileReason = { days -> context.getString(R.string.storage_cleaner_reason_large_file_stale, days) },
            duplicateBestCopyReason = { count -> context.getString(R.string.storage_cleaner_reason_duplicate_best_copy, count) },
            duplicateOneCopyReason = { count -> context.getString(R.string.storage_cleaner_reason_duplicate_one_copy, count) },
            unusedAppReason = { appLabel -> context.getString(R.string.storage_cleaner_reason_unused_app, appLabel) }
        )

        fun default(): RecommendationStrings = RecommendationStrings(
            duplicateMediaTitle = "Duplicate media",
            duplicateFilesTitle = "Duplicate files",
            oldDownloadTitle = "Old download",
            oldDownloadReason = "Old download in Downloads, not modified recently",
            apkPackageTitle = "APK package",
            apkReason = "Installer package found on storage; safe to remove if no longer needed",
            junkCandidateTitle = "Junk candidate",
            unusedAppTitle = "Unused app",
            largeFileReason = { days -> "Large file, not modified recently ($days days)" },
            duplicateBestCopyReason = { count -> "Duplicate group: $count items, keep best copy" },
            duplicateOneCopyReason = { count -> "Duplicate group: $count items, keep one copy" },
            unusedAppReason = { appLabel -> "Unused app: $appLabel, not used recently" }
        )
    }
}
