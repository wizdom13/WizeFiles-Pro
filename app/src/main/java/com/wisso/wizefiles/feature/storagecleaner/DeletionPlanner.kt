// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.content.Context
import android.os.Environment
import com.wisso.wizefiles.core.fastops.FastFileOps
import com.wisso.wizefiles.feature.filejobs.DeleteOptions
import com.wisso.wizefiles.feature.filejobs.FileOperationService
import java.io.File
import java.nio.file.Path

interface StorageCleanupDeletionPlanner {
    fun buildPreview(selected: List<CleanupRecommendation>): List<DeletePreviewItem>
    fun validateTargets(items: List<DeletePreviewItem>): List<DeletePreviewItem>
}

class DeletionPlanner : StorageCleanupDeletionPlanner {
    override fun buildPreview(selected: List<CleanupRecommendation>): List<DeletePreviewItem> {
        return selected.flatMap { recommendation ->
            when {
                recommendation.duplicateGroup?.keepSelectionRequiresReview == true ->
                    emptyList()
                recommendation.duplicateGroup != null -> {
                    recommendation.duplicateGroup.candidates
                        .filterNot { it.path == recommendation.duplicateGroup.keepCandidatePath }
                        .map {
                            DeletePreviewItem(
                                recommendationId = recommendation.id,
                                path = it.path,
                                displayName = File(it.path).name,
                                bytes = it.size,
                                reason = recommendation.reason,
                                duplicateGroupHash = recommendation.duplicateGroup.hash,
                                keepCandidatePath = recommendation.duplicateGroup.keepCandidatePath
                            )
                        }
                }
                recommendation.path != null -> {
                    val file = File(recommendation.path)
                    listOf(
                        DeletePreviewItem(
                            recommendation.id,
                            file.path,
                            file.name,
                            file.length(),
                            recommendation.reason
                        )
                    )
                }
                else -> emptyList()
            }
        }
    }

    override fun validateTargets(items: List<DeletePreviewItem>): List<DeletePreviewItem> {
        val base = runCatching { Environment.getExternalStorageDirectory().canonicalFile }.getOrNull()
            ?: return emptyList()
        return items.groupBy { it.recommendationId }.values.flatMap { recommendationItems ->
            val duplicateHash = recommendationItems.firstOrNull()?.duplicateGroupHash
            if (duplicateHash == null) {
                recommendationItems.filter { isValidFile(it.path, base) }
            } else {
                validateDuplicateGroup(recommendationItems, duplicateHash, base)
            }
        }
    }

    private fun validateDuplicateGroup(
        items: List<DeletePreviewItem>,
        expectedHash: String,
        base: File
    ): List<DeletePreviewItem> {
        val keepPath = items.firstOrNull()?.keepCandidatePath ?: return emptyList()
        val paths = listOf(keepPath) + items.map { it.path }
        val valid = paths.all { path ->
            if (!isValidFile(path, base)) return@all false
            FastFileOps.computeSha256(File(path)) == expectedHash
        }
        return if (valid) items else emptyList()
    }

    private fun isValidFile(path: String, base: File): Boolean = runCatching {
        val file = File(path).canonicalFile
        file.path.startsWith(base.path + File.separator) && file.isFile
    }.getOrDefault(false)
}

fun interface StorageCleanupDeletionQueue {
    fun enqueue(paths: List<Path>, context: Context): String?
}

internal object FileOperationStorageCleanupDeletionQueue : StorageCleanupDeletionQueue {
    override fun enqueue(paths: List<Path>, context: Context): String? =
        FileOperationService.delete(
            paths,
            context,
            DeleteOptions(
                permanentDelete = false,
                skipConfirmationForSession = false,
                secureShred = false
            )
        )
}
