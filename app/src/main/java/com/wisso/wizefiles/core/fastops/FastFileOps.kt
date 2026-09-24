package com.wisso.wizefiles.core.fastops

import com.wisso.wizefiles.feature.details.checksum.ChecksumInfo
import com.wisso.wizefiles.provider.os.isLinuxPath
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

fun interface NativePathBatchListener {
    fun onBatch(paths: Array<String>)
}

object FastFileOps {
    private val isNativeAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("fastops")
            true
        }.getOrDefault(false)
    }

    private fun Path.isSupportedLocalPath(): Boolean =
        isLinuxPath || runCatching {
            fileSystem.provider().scheme.equals("file", ignoreCase = true)
        }.getOrDefault(false)

    fun computeChecksums(path: Path): Map<ChecksumInfo.Algorithm, String>? {
        if (!isNativeAvailable || !path.isSupportedLocalPath()) {
            return null
        }
        val values = runCatching { nativeComputeChecksums(path.toString()) }.getOrNull() ?: return null
        if (values.size != ChecksumInfo.Algorithm.entries.size) {
            return null
        }
        return ChecksumInfo.Algorithm.entries.zip(values.asList()).toMap()
    }

    fun computeSha256(file: File): String? {
        if (!isNativeAvailable) {
            return null
        }
        return runCatching { nativeComputeSha256(file.path) }.getOrNull()
    }

    @Throws(IOException::class)
    fun deleteLocalTree(path: Path, secureShred: Boolean): Int? {
        if (!isNativeAvailable || !path.isSupportedLocalPath()) {
            return null
        }
        return nativeDeleteLocalTree(path.toString(), secureShred)
    }

    @Throws(IOException::class)
    fun moveLocalTree(source: Path, target: Path): Boolean {
        if (!isNativeAvailable || !source.isSupportedLocalPath() || !target.isSupportedLocalPath()) {
            return false
        }
        nativeMoveLocalTree(source.toString(), target.toString())
        return true
    }

    fun searchLocalTree(
        root: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ): Boolean {
        if (!isNativeAvailable || !root.isSupportedLocalPath() || query.any { it.code > 0x7F }) {
            return false
        }
        return runCatching {
            nativeSearchLocalTree(root.toString(), query, intervalMillis, NativePathBatchListener { matches ->
                listener(matches.map(Paths::get))
            })
            true
        }.getOrDefault(false)
    }

    @Throws(IOException::class)
    private external fun nativeComputeChecksums(path: String): Array<String>

    @Throws(IOException::class)
    private external fun nativeComputeSha256(path: String): String

    @Throws(IOException::class)
    private external fun nativeDeleteLocalTree(path: String, secureShred: Boolean): Int

    @Throws(IOException::class)
    private external fun nativeMoveLocalTree(source: String, target: String)

    @Throws(IOException::class)
    private external fun nativeSearchLocalTree(
        root: String,
        query: String,
        intervalMillis: Long,
        listener: NativePathBatchListener
    )
}
