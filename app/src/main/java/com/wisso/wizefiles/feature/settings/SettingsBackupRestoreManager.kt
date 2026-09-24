// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Parcel
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.appClassLoader
import com.wisso.wizefiles.core.app.defaultSharedPreferences
import com.wisso.wizefiles.core.app.secretStore
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.feature.filebrowser.GridColumnOverrides
import com.wisso.wizefiles.navigation.BookmarkDirectory
import com.wisso.wizefiles.navigation.StandardDirectorySettings
import com.wisso.wizefiles.searchindex.SearchIndexManager
import com.wisso.wizefiles.security.AppSecurityManager
import com.wisso.wizefiles.security.SecretStore
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toLocalFileOrNull
import com.wisso.wizefiles.storage.path.toLegacyPathOrNull
import com.wisso.wizefiles.ui.FileIconShape
import com.wisso.wizefiles.util.asBase64
import com.wisso.wizefiles.util.toBase64
import com.wisso.wizefiles.util.toByteArray
import com.wisso.wizefiles.util.use
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONException
import org.json.JSONObject

class SettingsBackupRestoreManager(
    private val context: Context,
    private val store: SettingsBackupStore = SharedPreferencesSettingsBackupStore(context, secretStore),
    private val serializer: SettingsBackupSerializer = SettingsBackupSerializer()
) {
    fun defaultBackupFileName(date: LocalDate = LocalDate.now()): String =
        SettingsBackupFileName.default(date)

    fun ensureBackupExtension(fileName: String): String {
        val normalized = fileName.trim().ifEmpty { defaultBackupFileName() }
        return if (normalized.endsWith(BACKUP_EXTENSION, ignoreCase = true)) {
            normalized
        } else {
            "$normalized$BACKUP_EXTENSION"
        }
    }

    fun backupToDownloads(
        fileName: String,
        encrypt: Boolean = true,
        password: CharArray? = null
    ): File {
        if (encrypt && (password == null || password.isEmpty())) {
            throw IllegalArgumentException(context.getString(R.string.settings_security_password_required))
        }
        val backupFile = File(downloadsDirectory(), ensureBackupExtension(fileName))
        backupFile.parentFile?.mkdirs()
        return try {
            val content = serializer.serialize(
                data = store.readBackupState(),
                password = password,
                encrypt = encrypt
            )
            backupFile.writeText(content)
            backupFile
        } finally {
            password?.fill('\u0000')
        }
    }

    fun previewRestoreFromUri(uri: Uri, password: CharArray? = null): SettingsRestorePreview {
        val content = readContent(uri)
        return previewRestoreFromContent(content, password)
    }

    fun previewRestoreFromPath(path: AppPath, password: CharArray? = null): SettingsRestorePreview {
        val content = readContent(path)
        return previewRestoreFromContent(content, password)
    }

    fun restoreFromUri(uri: Uri, password: CharArray? = null) {
        val preview = previewRestoreFromUri(uri, password)
        applyRestorePreview(preview)
    }

    fun restoreFromPath(path: String, password: CharArray? = null) {
        val content = readContent(File(path))
        val preview = previewRestoreFromContent(content, password)
        applyRestorePreview(preview)
    }

    fun inspectBackupEncryption(uri: Uri): SettingsBackupEncryptionInfo =
        serializer.inspect(readContent(uri))

    fun inspectBackupEncryption(path: AppPath): SettingsBackupEncryptionInfo =
        serializer.inspect(readContent(path))

    fun inspectBackupEncryption(path: String): SettingsBackupEncryptionInfo =
        serializer.inspect(readContent(File(path)))

    private fun readContent(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            readBoundedUtf8Content(stream, MAX_SETTINGS_BACKUP_JSON_BYTES)
        }
            ?: throw IllegalArgumentException(context.getString(R.string.settings_backup_restore_error_open_file))

    private fun readContent(file: File): String = file.inputStream().use { stream ->
        readBoundedUtf8Content(stream, MAX_SETTINGS_BACKUP_JSON_BYTES)
    }

    private fun readContent(path: AppPath): String {
        val inputStream = path.toLocalFileOrNull()?.inputStream()
            ?: path.toLegacyPathOrNull()?.let { Files.newInputStream(it) }
            ?: throw IllegalArgumentException(
                context.getString(R.string.settings_backup_restore_error_open_file)
            )
        return inputStream.use { stream ->
            readBoundedUtf8Content(stream, MAX_SETTINGS_BACKUP_JSON_BYTES)
        }
    }

    private fun previewRestoreFromContent(content: String, password: CharArray? = null): SettingsRestorePreview {
        if (content.toByteArray(StandardCharsets.UTF_8).size > MAX_SETTINGS_BACKUP_JSON_BYTES) {
            throw IllegalArgumentException(context.getString(R.string.settings_backup_restore_error_too_large))
        }
        val backupData = serializer.deserialize(content, password)
        return store.previewRestoreBackupState(backupData)
    }

    fun applyRestorePreview(preview: SettingsRestorePreview) {
        store.restorePreview(preview)
    }

    private fun readBoundedUtf8Content(input: InputStream, maxBytes: Int): String {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) {
                break
            }
            if (out.size() + read > maxBytes) {
                throw IllegalArgumentException(context.getString(R.string.settings_backup_restore_error_too_large))
            }
            out.write(buffer, 0, read)
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }

    private fun downloadsDirectory(): File =
        @Suppress("DEPRECATION")
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    companion object {
        const val BACKUP_EXTENSION = ".wzf"
        const val MAX_SETTINGS_BACKUP_JSON_BYTES = 262_144
    }
}
