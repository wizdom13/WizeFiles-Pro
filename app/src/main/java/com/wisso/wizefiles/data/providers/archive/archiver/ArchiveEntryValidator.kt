package com.wisso.wizefiles.provider.archive.archiver

import java.io.IOException
import java.nio.file.Path
import kotlin.math.max
import com.wisso.wizefiles.storage.SecureRelativePath

internal object ArchiveEntryValidator {
    private const val MAX_ENTRY_COUNT = 10_000
    private const val MAX_ENTRY_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 4L * 1024 * 1024 * 1024
    private const val MAX_EXPANSION_RATIO = 200L

    data class LimitsState(
        var entryCount: Int = 0,
        var totalUncompressedBytes: Long = 0
    )

    @Throws(IOException::class)
    fun sanitizeEntryName(entryName: String, isDirectory: Boolean): String? {
        if (entryName.contains('\u0000') || entryName.contains('\uFFFD')) {
            throw IOException("Unsafe archive entry name")
        }
        val normalized = entryName.trim().replace('\\', '/')
        if (normalized.isEmpty()) {
            return if (isDirectory) "" else throw IOException("Unsafe archive entry name")
        }
        if (normalized.startsWith('/') || normalized.startsWith("//")) {
            throw IOException("Unsafe archive entry name")
        }
        if (WINDOWS_ABSOLUTE_PATH_REGEX.containsMatchIn(normalized) || UNC_PREFIX_REGEX.containsMatchIn(normalized)) {
            throw IOException("Unsafe archive entry name")
        }

        val segments = mutableListOf<String>()
        normalized.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> throw IOException("Unsafe archive entry traversal")
                else -> segments += segment
            }
        }
        if (segments.isEmpty()) {
            return if (isDirectory) "" else throw IOException("Unsafe archive entry name")
        }
        return try {
            SecureRelativePath.validate(segments.joinToString("/"))
        } catch (exception: IllegalArgumentException) {
            throw IOException("Unsafe archive entry name", exception)
        }
    }

    fun validateSymlinkTarget(target: String): Boolean {
        if ('\\' in target) return false
        val normalized = target.trim().replace('\\', '/')
        return runCatching { SecureRelativePath.validate(normalized) }.isSuccess
    }

    @Throws(IOException::class)
    fun checkArchiveBombLimits(
        state: LimitsState,
        uncompressedSize: Long,
        compressedSize: Long? = null
    ) {
        val normalizedCompressedSize = compressedSize?.takeIf { it >= 0 }
        state.entryCount += 1
        if (state.entryCount > MAX_ENTRY_COUNT) {
            throw IOException("Archive has too many entries")
        }
        if (uncompressedSize >= 0) {
            if (uncompressedSize > MAX_ENTRY_UNCOMPRESSED_BYTES) {
                throw IOException("Archive entry is too large")
            }
            state.totalUncompressedBytes = safeAdd(state.totalUncompressedBytes, uncompressedSize)
            if (state.totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw IOException("Archive total size is too large")
            }
            when {
                normalizedCompressedSize == null -> Unit
                normalizedCompressedSize == 0L -> {
                    if (uncompressedSize > 0) {
                        throw IOException("Archive entry has unsafe expansion ratio")
                    }
                }
                exceedsExpansionRatio(uncompressedSize, normalizedCompressedSize) -> {
                    throw IOException("Archive entry has unsafe expansion ratio")
                }
            }
        }
    }

    private fun exceedsExpansionRatio(uncompressedSize: Long, compressedSize: Long): Boolean {
        if (compressedSize > Long.MAX_VALUE / MAX_EXPANSION_RATIO) {
            return false
        }
        val threshold = compressedSize * MAX_EXPANSION_RATIO
        return uncompressedSize > max(0L, threshold)
    }

    private fun safeAdd(left: Long, right: Long): Long {
        val result = left + right
        return if (result < left) Long.MAX_VALUE else result
    }

    private val WINDOWS_ABSOLUTE_PATH_REGEX = Regex("^[a-zA-Z]:.*")
    private val UNC_PREFIX_REGEX = Regex("^//.*")

    fun duplicateKey(path: Path): String = path.toString().lowercase()
}
