package com.wisso.wizefiles.feature.audioplayer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.provider.common.newInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Imports the selected audio into the public Ringtones collection and makes it the default ringtone. */
internal object AudioRingtoneSetter {
    fun canWriteSystemSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun createWriteSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )

    fun needsLegacyStoragePermission(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

    suspend fun setAsDefaultRingtone(
        context: Context,
        source: Path,
        displayName: String,
        mimeType: MimeType
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val ringtoneUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                importModern(context, source, displayName, mimeType)
            } else {
                importLegacy(context, source, displayName, mimeType)
            }
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                ringtoneUri
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun importModern(
        context: Context,
        source: Path,
        displayName: String,
        mimeType: MimeType
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, safeDisplayName(displayName))
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType.value)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
            put(MediaStore.Audio.Media.IS_RINGTONE, 1)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
            put(MediaStore.Audio.Media.IS_ALARM, 0)
            put(MediaStore.Audio.Media.IS_MUSIC, 0)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Unable to create ringtone media entry")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.newInputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Unable to open ringtone destination")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                null,
                null
            )
            return uri
        } catch (failure: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw failure
        }
    }

    private suspend fun importLegacy(
        context: Context,
        source: Path,
        displayName: String,
        mimeType: MimeType
    ): Uri {
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create public Ringtones directory")
        }
        val destination = uniqueDestination(directory, safeDisplayName(displayName))
        try {
            source.newInputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            val uri = scanFile(context, destination, mimeType.value)
                ?: throw IOException("Media scanner did not return a ringtone URI")
            context.contentResolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                    put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
                    put(MediaStore.Audio.Media.IS_ALARM, 0)
                    put(MediaStore.Audio.Media.IS_MUSIC, 0)
                },
                null,
                null
            )
            return uri
        } catch (failure: Exception) {
            destination.delete()
            throw failure
        }
    }

    private suspend fun scanFile(context: Context, file: File, mimeType: String): Uri? =
        suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(mimeType)
            ) { _, uri ->
                if (continuation.isActive) continuation.resume(uri)
            }
        }

    internal fun safeDisplayName(displayName: String): String {
        val leaf = displayName.trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace('\u0000', '_')
            .takeUnless { it == "." || it == ".." }
        return leaf?.ifBlank { "ringtone" } ?: "ringtone"
    }

    private fun uniqueDestination(directory: File, displayName: String): File {
        val initial = File(directory, displayName)
        if (!initial.exists()) return initial
        val dotIndex = displayName.lastIndexOf('.').takeIf { it > 0 } ?: displayName.length
        val baseName = displayName.substring(0, dotIndex)
        val extension = displayName.substring(dotIndex)
        for (suffix in 1..999) {
            val candidate = File(directory, "$baseName ($suffix)$extension")
            if (!candidate.exists()) return candidate
        }
        return File(directory, "$baseName-${System.currentTimeMillis()}$extension")
    }
}
