package com.wisso.wizefiles.feature.appmanager

import android.content.Context
import com.wisso.wizefiles.core.entitlement.ProFeature
import com.wisso.wizefiles.core.entitlement.ProFeatureAccess
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ExportedAppBackup(
    val file: File,
    val mimeType: String
)

class AppBackupExporter(
    private val context: Context,
    private val scheduleCleanup: (Context, Boolean) -> Unit =
        { appContext, retained -> AppBackupCleanupWorker.schedule(appContext, retained) }
) {
    suspend fun export(
        apps: List<InstalledApp>,
        retainedForTransfer: Boolean
    ): List<ExportedAppBackup> = withContext(Dispatchers.IO) {
        require(apps.isNotEmpty())
        if (apps.size > 1) {
            ProFeatureAccess.require(ProFeature.BATCH_APP_MANAGER_OPERATIONS)
        }
        AppBackupCache.cleanupExpired(context.cacheDir)
        val session = AppBackupCache.createSession(context.cacheDir, retainedForTransfer)
        val usedNames = linkedSetOf<String>()
        try {
            apps.map { app ->
                currentCoroutineContext().ensureActive()
                val preferredName = appBackupFileName(
                    label = app.label,
                    versionName = app.versionName,
                    versionCode = app.versionCode,
                    packageName = app.packageName,
                    isSplit = app.isSplit
                )
                val fileName = uniqueBackupFileName(preferredName, usedNames)
                val target = File(session, fileName)
                if (app.isSplit) {
                    writeSplitBackup(app, target)
                    ExportedAppBackup(target, MIME_APKS)
                } else {
                    writeSingleApk(app, target)
                    ExportedAppBackup(target, MIME_APK)
                }
            }.also {
                scheduleCleanup(context, retainedForTransfer)
            }
        } catch (exception: Exception) {
            session.deleteRecursively()
            throw exception
        }
    }

    private suspend fun writeSingleApk(app: InstalledApp, target: File) {
        val source = app.sourceApkPaths.singleOrNull()?.let(::File)
            ?: throw IOException("Single APK export has an invalid source count")
        if (!source.isFile) throw FileNotFoundException("APK source is no longer available")
        val partial = File(target.parentFile, "${target.name}.partial")
        try {
            BufferedInputStream(FileInputStream(source)).use { input ->
                BufferedOutputStream(FileOutputStream(partial)).use { output ->
                    copyCancellable(input, output)
                }
            }
            moveAtomically(partial, target)
        } finally {
            partial.delete()
        }
    }

    private suspend fun writeSplitBackup(app: InstalledApp, target: File) {
        val sources = app.sourceApkPaths.map(::File)
        if (sources.size < 2 || sources.any { !it.isFile }) {
            throw FileNotFoundException("One or more split APK sources are no longer available")
        }
        val partial = File(target.parentFile, "${target.name}.partial")
        try {
            val apkMetadata = mutableListOf<JSONObject>()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(partial))).use { zip ->
                val usedEntryNames = linkedSetOf<String>()
                sources.forEachIndexed { index, source ->
                    currentCoroutineContext().ensureActive()
                    val preferredEntryName = if (index == 0) {
                        "base.apk"
                    } else {
                        val base = sanitizeBackupComponent(
                            source.nameWithoutExtension,
                            "split_$index"
                        )
                        "$base.apk"
                    }
                    val entryName = uniqueBackupFileName(preferredEntryName, usedEntryNames)
                    val digest = MessageDigest.getInstance("SHA-256")
                    zip.putNextEntry(ZipEntry(entryName).apply { time = 0L })
                    BufferedInputStream(FileInputStream(source)).use { input ->
                        copyCancellable(input, zip, digest)
                    }
                    zip.closeEntry()
                    apkMetadata += JSONObject()
                        .put("file", entryName)
                        .put("sha256", digest.digest().toHexString())
                }
                val metadata = JSONObject()
                    .put("formatVersion", 1)
                    .put("packageName", app.packageName)
                    .put("appName", app.label)
                    .put("versionName", app.versionName)
                    .put("versionCode", app.versionCode)
                    .put("createdAtEpochMillis", System.currentTimeMillis())
                    .put("apks", JSONArray(apkMetadata))
                zip.putNextEntry(ZipEntry("metadata.json").apply { time = 0L })
                zip.write(metadata.toString(2).toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            moveAtomically(partial, target)
        } finally {
            partial.delete()
        }
    }

    private suspend fun copyCancellable(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        digest: MessageDigest? = null
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            digest?.update(buffer, 0, count)
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    companion object {
        const val MIME_APK = "application/vnd.android.package-archive"
        const val MIME_APKS = "application/zip"
    }
}

internal fun appBackupFileName(
    label: String,
    versionName: String,
    versionCode: Long,
    packageName: String,
    isSplit: Boolean
): String {
    val fallbackName = packageName.substringAfterLast('.').ifBlank { "app" }
    val safeLabel = sanitizeBackupComponent(label, fallbackName).take(MAX_COMPONENT_LENGTH)
    val version = versionName.trim().ifEmpty { versionCode.toString() }
    val safeVersion = sanitizeBackupComponent(version, versionCode.toString())
        .take(MAX_COMPONENT_LENGTH)
    return "${safeLabel}_${safeVersion}.${if (isSplit) "apks" else "apk"}"
}

internal fun uniqueBackupFileName(preferredName: String, usedNames: MutableSet<String>): String {
    if (usedNames.add(preferredName)) return preferredName
    val extension = preferredName.substringAfterLast('.', missingDelimiterValue = "")
    val baseName = if (extension.isEmpty()) preferredName else preferredName.dropLast(extension.length + 1)
    var index = 2
    while (true) {
        val candidate = if (extension.isEmpty()) {
            "${baseName}_$index"
        } else {
            "${baseName}_$index.$extension"
        }
        if (usedNames.add(candidate)) return candidate
        index++
    }
}

private fun sanitizeBackupComponent(value: String, fallback: String): String {
    val sanitized = value.trim()
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('.', '_', '-')
    return sanitized.ifBlank { fallback }
}

private const val MAX_COMPONENT_LENGTH = 96
