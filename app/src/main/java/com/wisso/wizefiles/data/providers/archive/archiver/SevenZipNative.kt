package com.wisso.wizefiles.provider.archive.archiver

import com.wisso.wizefiles.provider.common.PosixFileMode
import com.wisso.wizefiles.provider.common.PosixFileType
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/** Read-only bridge to the official 7-Zip format module. */
internal object SevenZipNative {
    private val loadFailure: Throwable? by lazy {
        runCatching { System.loadLibrary("sevenzip-jni") }.exceptionOrNull()
    }

    fun isAvailable(): Boolean = loadFailure == null

    @Throws(IOException::class)
    fun readEntries(file: Path, passwords: List<String>): List<ReadArchive.Entry> {
        checkAvailable()
        val source = requireLocalFile(file)
        val json = callWithPasswords(passwords) { password ->
            list(source.path, source.parent.orEmpty(), password)
        }
        val items = JSONArray(json)
        return buildList(items.length()) {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val directory = item.optBoolean("directory", false)
                val symlinkTarget = item.optString("symlinkTarget", "").ifEmpty { null }
                val type = when {
                    directory -> PosixFileType.DIRECTORY
                    symlinkTarget != null -> PosixFileType.SYMBOLIC_LINK
                    else -> PosixFileType.REGULAR_FILE
                }
                add(
                    ReadArchive.Entry(
                        item.getString("name"),
                        item.optBoolean("encrypted", false),
                        item.optLongOrNull("modifiedMillis")?.let(FileTime::fromMillis),
                        null,
                        null,
                        type,
                        item.optLong("size", 0L),
                        null,
                        null,
                        when (type) {
                            PosixFileType.DIRECTORY -> PosixFileMode.DIRECTORY_DEFAULT
                            PosixFileType.SYMBOLIC_LINK -> PosixFileMode.SYMBOLIC_LINK_DEFAULT
                            else -> PosixFileMode.FILE_DEFAULT
                        },
                        symlinkTarget,
                        item.optLongOrNull("compressedSize"),
                        ArchiveReadBackend.SEVEN_ZIP,
                        item.getInt("index")
                    )
                )
            }
        }
    }

    @Throws(IOException::class)
    fun newInputStream(
        file: Path,
        passwords: List<String>,
        entry: ReadArchive.Entry,
        cacheDirectory: File
    ): InputStream {
        checkAvailable()
        val itemIndex = entry.backendIndex
            ?: throw IOException("7-Zip entry index is unavailable")
        if (entry.size < 0 || entry.size > MAX_EXTRACTED_ENTRY_BYTES) {
            throw IOException("Archive entry is too large")
        }
        val source = requireLocalFile(file)
        val output = File.createTempFile("sevenzip-entry-", ".tmp", cacheDirectory)
        var successful = false
        try {
            callWithPasswords(passwords) { password ->
                extract(
                    source.path,
                    source.parent.orEmpty(),
                    itemIndex,
                    output.path,
                    password,
                    MAX_EXTRACTED_ENTRY_BYTES
                )
            }
            successful = true
            return DeleteOnCloseInputStream(output)
        } finally {
            if (!successful) output.delete()
        }
    }

    private inline fun <T> callWithPasswords(
        passwords: List<String>,
        action: (String?) -> T
    ): T {
        var lastFailure: IOException? = null
        val candidates = (passwords.asReversed().map<String, String?> { it } + null).distinct()
        for (password in candidates) {
            try {
                return action(password)
            } catch (failure: IOException) {
                lastFailure = failure
            }
        }
        throw lastFailure ?: IOException("7-Zip could not open the container")
    }

    private fun checkAvailable() {
        loadFailure?.let { throw IOException("7-Zip backend is unavailable", it) }
    }

    private fun requireLocalFile(path: Path): File {
        val file = runCatching { path.toFile() }.getOrElse {
            throw IOException("7-Zip requires a staged local source", it)
        }
        if (!file.isFile) throw IOException("7-Zip source is not a regular file")
        return file
    }

    @Throws(IOException::class)
    private external fun list(sourcePath: String, volumeDirectory: String, password: String?): String

    @Throws(IOException::class)
    private external fun extract(
        sourcePath: String,
        volumeDirectory: String,
        itemIndex: Int,
        outputPath: String,
        password: String?,
        maxOutputBytes: Long
    )

    private class DeleteOnCloseInputStream(private val file: File) : FileInputStream(file) {
        override fun close() {
            try {
                super.close()
            } finally {
                file.delete()
            }
        }
    }

    private fun org.json.JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) getLong(name) else null

    private const val MAX_EXTRACTED_ENTRY_BYTES = 1024L * 1024L * 1024L
}

enum class ArchiveReadBackend {
    LIBARCHIVE,
    SEVEN_ZIP
}
