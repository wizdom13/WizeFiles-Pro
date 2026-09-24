package com.wisso.wizefiles.feature.advancedformats.staging

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.UUID

/**
 * Per-viewer cache session for sources that cannot be consumed through a seekable descriptor.
 * Callers own the returned files only until this store is closed.
 */
class FormatStagingStore(
    cacheDirectory: File,
    sessionId: String = UUID.randomUUID().toString()
) : Closeable {
    val sessionDirectory = File(File(cacheDirectory, ROOT_DIRECTORY), validateSessionId(sessionId))

    init {
        if (!sessionDirectory.mkdirs() && !sessionDirectory.isDirectory) {
            throw IOException("Unable to create format staging session")
        }
    }

    @Throws(IOException::class)
    fun stageFile(
        fileName: String,
        input: InputStream,
        expectedSizeBytes: Long?,
        maxBytes: Long = FormatSourcePlanner.MAX_STAGED_SOURCE_BYTES,
        isCancelled: () -> Boolean = { false },
        onProgress: (Long) -> Unit = {}
    ): File {
        val safeName = validateSimpleFileName(fileName)
        validateLimits(expectedSizeBytes, maxBytes)
        val finalFile = File(sessionDirectory, "source-${UUID.randomUUID()}-$safeName")
        val partialFile = File(sessionDirectory, ".${finalFile.name}.partial")
        try {
            copyToFile(input, partialFile, expectedSizeBytes, maxBytes, isCancelled, onProgress)
            moveCompleted(partialFile, finalFile)
            return finalFile
        } catch (exception: Exception) {
            partialFile.delete()
            throw exception
        }
    }

    @Throws(IOException::class)
    fun stageBundle(
        entries: List<FormatBundleSource>,
        maxBytes: Long = FormatSourcePlanner.MAX_STAGED_SOURCE_BYTES,
        isCancelled: () -> Boolean = { false },
        onProgress: (Long) -> Unit = {}
    ): File {
        require(maxBytes in 1..FormatSourcePlanner.MAX_STAGED_SOURCE_BYTES) {
            "Invalid staging limit"
        }
        val partialDirectory = File(sessionDirectory, ".bundle-${UUID.randomUUID()}.partial")
        val finalDirectory = File(sessionDirectory, "bundle-${UUID.randomUUID()}")
        if (!partialDirectory.mkdir()) throw IOException("Unable to create staged bundle")
        var totalBytes = 0L
        try {
            entries.forEach { entry ->
                val relativePath = validateRelativePath(entry.relativePath)
                val target = containedFile(partialDirectory, relativePath)
                val parent = target.parentFile ?: throw IOException("Invalid bundle target")
                if (!parent.mkdirs() && !parent.isDirectory) {
                    throw IOException("Unable to create staged bundle directory")
                }
                val remaining = maxBytes - totalBytes
                validateLimits(entry.expectedSizeBytes, remaining)
                val copied = copyToFile(
                    entry.openInput(),
                    target,
                    entry.expectedSizeBytes,
                    remaining,
                    isCancelled
                ) { entryBytes -> onProgress(totalBytes + entryBytes) }
                totalBytes += copied
            }
            moveCompleted(partialDirectory, finalDirectory)
            return finalDirectory
        } catch (exception: Exception) {
            partialDirectory.deleteRecursively()
            throw exception
        }
    }

    override fun close() {
        sessionDirectory.deleteRecursively()
        sessionDirectory.parentFile?.delete()
    }

    private fun copyToFile(
        input: InputStream,
        target: File,
        expectedSizeBytes: Long?,
        maxBytes: Long,
        isCancelled: () -> Boolean,
        onProgress: (Long) -> Unit
    ): Long {
        var copied = 0L
        input.use { source ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    if (isCancelled() || Thread.currentThread().isInterrupted) {
                        throw InterruptedIOException("Format staging cancelled")
                    }
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (copied + count > maxBytes) {
                        throw FormatStagingLimitException(maxBytes)
                    }
                    output.write(buffer, 0, count)
                    copied += count
                    onProgress(copied)
                }
                output.fd.sync()
            }
        }
        if (expectedSizeBytes != null && copied != expectedSizeBytes) {
            throw IOException("Source size changed while staging")
        }
        return copied
    }

    private fun validateLimits(expectedSizeBytes: Long?, maxBytes: Long) {
        require(maxBytes in 1..FormatSourcePlanner.MAX_STAGED_SOURCE_BYTES) {
            "Invalid staging limit"
        }
        if (expectedSizeBytes != null) {
            require(expectedSizeBytes >= 0) { "Invalid source size" }
            if (expectedSizeBytes > maxBytes) throw FormatStagingLimitException(maxBytes)
        }
    }

    private fun containedFile(root: File, relativePath: String): File {
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, relativePath).canonicalFile
        val rootPrefix = canonicalRoot.path + File.separator
        if (target != canonicalRoot && !target.path.startsWith(rootPrefix)) {
            throw IOException("Bundle entry escapes the staging directory")
        }
        return target
    }

    private fun moveCompleted(source: File, target: File) {
        if (!source.renameTo(target)) throw IOException("Unable to finalize staged source")
    }

    companion object {
        private const val ROOT_DIRECTORY = "advanced_format_staging"
        private const val BUFFER_SIZE = 64 * 1024

        private fun validateSessionId(sessionId: String): String {
            require(SESSION_ID.matches(sessionId)) { "Invalid staging session ID" }
            return sessionId
        }

        private fun validateSimpleFileName(fileName: String): String {
            require(
                fileName.isNotBlank() && fileName != "." && fileName != ".." &&
                    '/' !in fileName && '\\' !in fileName && '\u0000' !in fileName
            ) { "Invalid staged file name" }
            return fileName
        }

        private fun validateRelativePath(relativePath: String): String {
            val normalized = relativePath.replace('\\', '/').trimStart('/')
            require(normalized.isNotBlank() && '\u0000' !in normalized) {
                "Invalid bundle path"
            }
            require(relativePath.firstOrNull() != '/' && !WINDOWS_ABSOLUTE.matches(relativePath)) {
                "Absolute bundle paths are not allowed"
            }
            val segments = normalized.split('/')
            require(segments.none { it.isBlank() || it == "." || it == ".." }) {
                "Bundle traversal is not allowed"
            }
            return segments.joinToString(File.separator)
        }

        private val SESSION_ID = Regex("[A-Za-z0-9_-]{1,80}")
        private val WINDOWS_ABSOLUTE = Regex("[A-Za-z]:[\\\\/].*")
    }
}

class FormatBundleSource(
    val relativePath: String,
    val expectedSizeBytes: Long?,
    val openInput: () -> InputStream
)

class FormatStagingLimitException(maxBytes: Long) :
    IOException("Staged source exceeds the $maxBytes byte limit")
