// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.batchrename

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatchRenameSource(
    val originalName: String,
    val modifiedEpochMillis: Long?,
    val sizeBytes: Long,
    val apkLabel: String? = null,
    val apkVersionName: String? = null
)

data class BatchRenameOptions(
    val pattern: String,
    val replaceText: String = "",
    val replacementText: String = "",
    val regexReplacement: Boolean = false,
    val startNumber: Long = 1
)

data class BatchRenamePreviewRow(
    val sourceIndex: Int,
    val originalName: String,
    val targetName: String,
    val error: String? = null
) {
    val isChanged: Boolean
        get() = error == null && originalName != targetName
}

data class BatchRenamePlan(
    val rows: List<BatchRenamePreviewRow>,
    val globalError: String? = null
) {
    val isValid: Boolean
        get() = globalError == null && rows.isNotEmpty() && rows.none { it.error != null }

    val changedCount: Int
        get() = rows.count(BatchRenamePreviewRow::isChanged)
}

object BatchRenamePatternEngine {
    private val compoundExtensions = listOf(
        ".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst", ".tar.lz", ".tar.lzma",
        ".user.js", ".min.js", ".min.css"
    )

    fun defaultPattern(fileCount: Int): String {
        val digits = fileCount.coerceAtLeast(1).toString().length
        return "%n (${"#".repeat(digits)})%E"
    }

    fun plan(
        sources: List<BatchRenameSource>,
        options: BatchRenameOptions,
        existingNames: Collection<String> = emptyList()
    ): BatchRenamePlan {
        if (options.pattern.isBlank()) {
            return BatchRenamePlan(emptyList(), "Pattern cannot be empty")
        }
        if (options.startNumber < 0) {
            return BatchRenamePlan(emptyList(), "Starting number cannot be negative")
        }
        val replacementRegex = if (options.regexReplacement && options.replaceText.isNotEmpty()) {
            try {
                Regex(options.replaceText)
            } catch (exception: IllegalArgumentException) {
                return BatchRenamePlan(emptyList(), "Invalid regular expression: ${exception.message}")
            }
        } else {
            null
        }

        val sourceNames = sources.map { normalizeForCollision(it.originalName) }.toSet()
        val unrelatedExistingNames = existingNames
            .map(::normalizeForCollision)
            .filterNot(sourceNames::contains)
            .toSet()
        val rows = sources.mapIndexed { index, source ->
            buildRow(index, source, options, replacementRegex)
        }.toMutableList()

        val targetGroups = rows
            .filter { it.error == null }
            .groupBy { normalizeForCollision(it.targetName) }
        targetGroups.values.filter { it.size > 1 }.flatten().forEach { duplicate ->
            rows[duplicate.sourceIndex] = duplicate.copy(error = "Duplicate resulting name")
        }
        rows.forEachIndexed { index, row ->
            if (row.error == null && normalizeForCollision(row.targetName) in unrelatedExistingNames) {
                rows[index] = row.copy(error = "A file with this name already exists")
            }
        }
        return BatchRenamePlan(rows)
    }

    private fun buildRow(
        index: Int,
        source: BatchRenameSource,
        options: BatchRenameOptions,
        replacementRegex: Regex?
    ): BatchRenamePreviewRow {
        val (baseName, extension) = splitName(source.originalName)
        val transformedBaseName = when {
            options.replaceText.isEmpty() -> baseName
            replacementRegex != null -> replacementRegex.replace(baseName, options.replacementText)
            else -> baseName.replace(options.replaceText, options.replacementText)
        }
        val number = options.startNumber + index
        val (targetName, missingApkMetadata) = renderPattern(
            pattern = options.pattern,
            transformedBaseName = transformedBaseName,
            extension = extension,
            source = source,
            number = number
        )

        val error = when {
            missingApkMetadata -> "APK application or version information is unavailable"
            targetName.isBlank() -> "Resulting name is empty"
            targetName == "." || targetName == ".." -> "Invalid filename"
            targetName.any { it == '/' || it == '\u0000' } -> "Filename contains an invalid character"
            targetName.length > 255 -> "Filename is longer than 255 characters"
            else -> null
        }
        return BatchRenamePreviewRow(index, source.originalName, targetName, error)
    }

    private fun renderPattern(
        pattern: String,
        transformedBaseName: String,
        extension: String,
        source: BatchRenameSource,
        number: Long
    ): Pair<String, Boolean> {
        val output = StringBuilder(pattern.length + transformedBaseName.length)
        var missingApkMetadata = false
        var index = 0
        while (index < pattern.length) {
            when {
                pattern[index] == '#' -> {
                    val start = index
                    while (index < pattern.length && pattern[index] == '#') {
                        index++
                    }
                    output.append(number.toString().padStart(index - start, '0'))
                }
                pattern[index] == '%' && index + 1 < pattern.length -> {
                    val token = pattern[index + 1]
                    when (token) {
                        '%' -> output.append('%')
                        'n' -> output.append(transformedBaseName)
                        'N' -> output.append(transformedBaseName).append(extension)
                        'E' -> output.append(extension)
                        'D' -> output.append(formatDate(source.modifiedEpochMillis, "yyyy-MM-dd"))
                        'T' -> output.append(formatDate(source.modifiedEpochMillis, "HH-mm-ss"))
                        'S' -> output.append(source.sizeBytes)
                        'A' -> {
                            val label = source.apkLabel
                            if (label == null) missingApkMetadata = true else output.append(label)
                        }
                        'V' -> {
                            val versionName = source.apkVersionName
                            if (versionName == null) {
                                missingApkMetadata = true
                            } else {
                                output.append(versionName)
                            }
                        }
                        else -> output.append('%').append(token)
                    }
                    index += 2
                }
                else -> {
                    output.append(pattern[index])
                    index++
                }
            }
        }
        return output.toString() to missingApkMetadata
    }

    internal fun splitName(name: String): Pair<String, String> {
        val lowerName = name.lowercase(Locale.ROOT)
        val compound = compoundExtensions.firstOrNull {
            lowerName.endsWith(it) && name.length > it.length
        }
        if (compound != null) {
            return name.dropLast(compound.length) to name.takeLast(compound.length)
        }
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < name.lastIndex) {
            name.substring(0, dotIndex) to name.substring(dotIndex)
        } else {
            name to ""
        }
    }

    private fun formatDate(epochMillis: Long?, pattern: String): String =
        if (epochMillis == null || epochMillis <= 0) {
            "unknown"
        } else {
            SimpleDateFormat(pattern, Locale.US).format(Date(epochMillis))
        }

    private fun normalizeForCollision(name: String): String = name.lowercase(Locale.ROOT)
}
