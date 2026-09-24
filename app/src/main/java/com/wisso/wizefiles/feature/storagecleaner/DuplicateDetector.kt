package com.wisso.wizefiles.storagecleaner

import com.wisso.wizefiles.core.fastops.FastFileOps
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

class DuplicateDetector {
    private val keepRanker = DuplicateKeepRanker()

    fun detectExactDuplicates(files: List<File>, maxFilesToHash: Int): List<DuplicateGroup> {
        val bySize = files.groupBy { runCatching { it.length() }.getOrDefault(0L) }
            .filter { it.key > 0 && it.value.size > 1 }
        val duplicateGroups = mutableListOf<DuplicateGroup>()
        var processed = 0
        bySize.values.forEach { candidates ->
            if (processed >= maxFilesToHash) return@forEach
            val limited = candidates.take(maxFilesToHash - processed)
            val hashes = limited
                .groupBy { hashFile(it) }
                .filterKeys { it.isNotEmpty() }
                .filter { it.value.size > 1 }
            processed += limited.size
            hashes.forEach { (hash, dupes) ->
                val fileCandidates = dupes.mapNotNull {
                    val size = runCatching { it.length() }.getOrNull() ?: return@mapNotNull null
                    FileCandidate(
                        path = it.path,
                        size = size,
                        modifiedTimeMillis = runCatching { it.lastModified() }.getOrDefault(0L)
                    )
                }
                if (fileCandidates.size < 2) return@forEach
                val keep = keepRanker.choose(fileCandidates) ?: fileCandidates.first()
                duplicateGroups += DuplicateGroup(
                    id = "dup-$hash",
                    hash = hash,
                    candidates = fileCandidates,
                    keepCandidatePath = keep.path,
                    recommendedKeepCandidatePath = keep.path
                )
            }
        }
        return duplicateGroups
    }

    private fun hashFile(file: File): String {
        return FastFileOps.computeSha256(file) ?: runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }
}

/**
 * Chooses the safest default copy to keep for byte-identical files.
 *
 * Explicit user keep selections are applied later by [StorageCleanerViewModel] and always win.
 * This ranker only chooses the recommendation shown before the user overrides it.
 */
internal class DuplicateKeepRanker {
    fun choose(candidates: List<FileCandidate>): FileCandidate? = candidates.minWithOrNull(
        compareBy<FileCandidate> { -preferredFolderScore(it.path) }
            .thenBy { duplicateNamePenalty(it.path) }
            .thenBy { effectiveModifiedTime(it.modifiedTimeMillis) }
            .thenBy { File(it.path).name.length }
            .thenBy { it.path.lowercase(Locale.ROOT) }
    )

    private fun preferredFolderScore(path: String): Int {
        val normalized = path.replace('\\', '/').lowercase(Locale.ROOT)
        return when {
            containsSegment(normalized, ".thumbnails") ||
                containsSegment(normalized, "cache") ||
                containsSegment(normalized, "tmp") ||
                containsSegment(normalized, "temp") -> 0
            containsSegment(normalized, "dcim") -> 6
            containsSegment(normalized, "pictures") -> 5
            containsSegment(normalized, "movies") ||
                containsSegment(normalized, "music") -> 4
            containsSegment(normalized, "documents") -> 3
            containsSegment(normalized, "download") ||
                containsSegment(normalized, "downloads") -> 2
            else -> 1
        }
    }

    private fun containsSegment(path: String, segment: String): Boolean =
        path.split('/').any { it == segment }

    private fun duplicateNamePenalty(path: String): Int {
        val stem = File(path).nameWithoutExtension.lowercase(Locale.ROOT).trim()
        return when {
            DUPLICATE_WORD_PATTERN.containsMatchIn(stem) -> 2
            NUMBERED_COPY_PATTERN.containsMatchIn(stem) -> 1
            else -> 0
        }
    }

    private fun effectiveModifiedTime(modifiedTimeMillis: Long): Long =
        modifiedTimeMillis.takeIf { it > 0L } ?: Long.MAX_VALUE

    private companion object {
        val DUPLICATE_WORD_PATTERN = Regex("(?:^|[\\s._-])(copy|duplicate)(?:[\\s._-]|$)")
        val NUMBERED_COPY_PATTERN = Regex("\\(\\d+\\)$")
    }
}
