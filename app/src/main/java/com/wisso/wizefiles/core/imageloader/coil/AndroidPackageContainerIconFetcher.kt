// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.imageloader.coil

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toDrawable
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.wisso.wizefiles.core.android.compat.getPackageArchiveInfoCompat
import com.wisso.wizefiles.core.imageloader.coil.legacy.isDocumentUriLike
import com.wisso.wizefiles.core.imageloader.coil.legacy.isRemoteUriLike
import com.wisso.wizefiles.core.imageloader.coil.legacy.openInputStream
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.storage.path.toLocalFileOrNull
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.LinkedHashMap
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal class AndroidPackageContainerIconFetcher(
    private val data: Pair<AppPath, FileMetadata>,
    private val options: Options,
    iconSize: Int,
    private val reader: AndroidPackageIconReader = AndroidPackageIconReader()
) : Fetcher {
    private val appIconLoader = AppIconCompatLoader(options.context, iconSize)

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        concurrency.withPermit {
            val (path, metadata) = data
            val kind = AndroidPackageArchiveKind.fromPath(path.rawPath) ?: return@withPermit null
            if (path.isRemoteUriLike &&
                !AndroidPackageContainerIconPolicy.shouldMaterializeRemote(metadata.sizeBytes)
            ) {
                return@withPermit null
            }
            val cacheKey = "$path:${metadata.sizeBytes}:${metadata.lastModifiedEpochMillis}:$RESOLVER_VERSION"
            if (failureCache.contains(cacheKey)) return@withPermit null
            try {
                materialize(path, metadata, options.context).use { container ->
                    val payload = reader.find(container.file, kind) ?: return@use null
                    when (payload) {
                        is AndroidPackageIconPayload.NestedApk ->
                            loadNestedApkIcon(container.file, payload.entryName, options.context)
                        is AndroidPackageIconPayload.Raster ->
                            loadRasterIcon(container.file, payload.entryName, options.context)
                    }
                }.also { result ->
                    if (result == null) failureCache.add(cacheKey) else failureCache.remove(cacheKey)
                }
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                failureCache.add(cacheKey)
                com.wisso.wizefiles.util.AppLog.e(
                    "PackageIcon", "Unable to read icon from ${path.rawPath}", error
                )
                null
            }
        }
    }

    private suspend fun loadNestedApkIcon(
        container: File,
        entryName: String,
        context: Context
    ): FetchResult? {
        val temporaryApk = File.createTempFile("package-icon-", ".apk", context.cacheDir)
        try {
            ZipFile(container).use { archive ->
                val entry = archive.getEntry(entryName) ?: throw IOException("APK entry disappeared")
                archive.getInputStream(entry).use { input ->
                    temporaryApk.outputStream().use { output ->
                        input.copyBoundedTo(output, MAXIMUM_NESTED_APK_BYTES)
                    }
                }
            }
            coroutineContext.ensureActive()
            val packageInfo = context.packageManager.getPackageArchiveInfoCompat(
                temporaryApk.path, 0
            ) ?: return null
            val applicationInfo = packageInfo.applicationInfo ?: return null
            applicationInfo.sourceDir = temporaryApk.path
            applicationInfo.publicSourceDir = temporaryApk.path
            return DrawableResult(
                appIconLoader.loadIcon(applicationInfo),
                false,
                DataSource.DISK
            )
        } finally {
            temporaryApk.delete()
        }
    }

    private fun loadRasterIcon(
        container: File,
        entryName: String,
        context: Context
    ): FetchResult? = ZipFile(container).use { archive ->
        val entry = archive.getEntry(entryName) ?: return@use null
        val bytes = archive.getInputStream(entry).use { input ->
            input.readBoundedBytes(MAXIMUM_RASTER_BYTES)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
        DrawableResult(bitmap.toDrawable(context.resources), false, DataSource.DISK)
    }

    private fun materialize(
        path: AppPath,
        metadata: FileMetadata,
        context: Context
    ): MaterializedContainer {
        path.toLocalFileOrNull()?.let { return MaterializedContainer(it, false) }
        if (path.isRemoteUriLike &&
            !AndroidPackageContainerIconPolicy.shouldMaterializeRemote(metadata.sizeBytes)
        ) {
            throw IOException("Remote package container exceeds the thumbnail download policy")
        }
        val temporary = File.createTempFile("package-container-", ".zip", context.cacheDir)
        try {
            val input = when {
                path.isDocumentUriLike -> path.openInputStream(context)
                else -> path.toLegacyPathOrNull()?.let { Files.newInputStream(it) }
                    ?: throw IOException("Unsupported package-container path")
            }
            val maximumBytes = if (path.isRemoteUriLike) {
                AndroidPackageContainerIconPolicy.MAXIMUM_REMOTE_CONTAINER_BYTES
            } else {
                MAXIMUM_MATERIALIZED_CONTAINER_BYTES
            }
            input.use { source ->
                temporary.outputStream().use { output ->
                    source.copyBoundedTo(output, maximumBytes)
                }
            }
            return MaterializedContainer(temporary, true)
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private data class MaterializedContainer(val file: File, val temporary: Boolean) : AutoCloseable {
        override fun close() {
            if (temporary) file.delete()
        }
    }

    private companion object {
        const val RESOLVER_VERSION = 2
        const val MAXIMUM_MATERIALIZED_CONTAINER_BYTES = 1024L * 1024L * 1024L
        const val MAXIMUM_NESTED_APK_BYTES = 512L * 1024 * 1024
        const val MAXIMUM_RASTER_BYTES = 16L * 1024 * 1024
        val concurrency = Semaphore(2)
        val failureCache = ExpiringBoundedStringSet(
            maximumSize = 128,
            ttlMillis = 5L * 60L * 1000L
        )
    }
}

private fun InputStream.copyBoundedTo(output: java.io.OutputStream, maximumBytes: Long) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return
        total += count
        if (total > maximumBytes) throw IOException("Package icon input exceeds its limit")
        output.write(buffer, 0, count)
    }
}

private fun InputStream.readBoundedBytes(maximumBytes: Long): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    copyBoundedTo(output, maximumBytes)
    return output.toByteArray()
}

internal object AndroidPackageContainerIconPolicy {
    const val MAXIMUM_REMOTE_CONTAINER_BYTES = 64L * 1024L * 1024L

    fun shouldMaterializeRemote(sizeBytes: Long?): Boolean =
        sizeBytes != null && sizeBytes in 0L..MAXIMUM_REMOTE_CONTAINER_BYTES
}

internal class ExpiringBoundedStringSet(
    private val maximumSize: Int,
    private val ttlMillis: Long,
    private val nowMillisProvider: () -> Long = System::currentTimeMillis
) {
    init {
        require(maximumSize > 0)
        require(ttlMillis > 0)
    }

    private val values = object : LinkedHashMap<String, Long>(maximumSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > maximumSize
    }

    @Synchronized
    fun contains(value: String): Boolean {
        val failedAtMillis = values[value] ?: return false
        val ageMillis = (nowMillisProvider() - failedAtMillis).coerceAtLeast(0L)
        if (ageMillis < ttlMillis) return true
        values.remove(value)
        return false
    }

    @Synchronized
    fun add(value: String) {
        values[value] = nowMillisProvider()
    }

    @Synchronized
    fun remove(value: String) {
        values.remove(value)
    }
}
