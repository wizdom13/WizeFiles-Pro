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

interface SettingsBackupStore {
    fun readBackupState(): SettingsBackupData
    fun previewRestoreBackupState(data: SettingsBackupData): SettingsRestorePreview
    fun restorePreview(preview: SettingsRestorePreview)
}

class SharedPreferencesSettingsBackupStore(
    private val context: Context,
    private val secretStore: SecretStore
) : SettingsBackupStore {

    private val fileSortOptionsKey by lazy {
        context.getString(R.string.pref_key_file_list_sort_options)
    }

    private val indexedSearchKey by lazy {
        context.getString(R.string.pref_key_indexed_search)
    }

    private val keySet: Set<String> by lazy {
        SettingsBackupRegistry.backupSettingKeyResIds.mapTo(mutableSetOf()) { context.getString(it) }
    }

    override fun readBackupState(): SettingsBackupData {
        val settings = mutableMapOf<String, BackupValue>()
        defaultSharedPreferences.all
            .filterKeys { it in keySet }
            .forEach { (key, value) ->
                settings[key] =
                    if (key == fileSortOptionsKey) {
                        BackupValue(
                            TYPE_FILE_SORT_OPTIONS_V2,
                            serializeFileSortOptions(
                                Settings.FILE_LIST_SORT_OPTIONS.value ?: defaultFileSortOptions()
                            )
                        )
                    } else {
                        BackupValue(resolveType(value), value)
                    }
            }
        return SettingsBackupData(
            formatVersion = SettingsBackupSerializer.FORMAT_VERSION,
            settings = settings,
            secretSettings = emptyMap()
        )
    }

    override fun previewRestoreBackupState(data: SettingsBackupData): SettingsRestorePreview {
        return SettingsRestoreValidator(
            context = context,
            keySet = keySet,
            hasSecurityPassword = { AppSecurityManager.create(context, defaultSharedPreferences, secretStore).hasPassword() }
        ).buildPreview(data)
    }

    override fun restorePreview(preview: SettingsRestorePreview) {
        val current = defaultSharedPreferences.all
        defaultSharedPreferences.edit().apply {
            preview.settingsToApply.forEach { (key, value) ->
                if (key == fileSortOptionsKey) {
                    putString(key, restoreFileSortOptions(value))
                    return@forEach
                }
                when (value.type) {
                    TYPE_STRING -> putString(key, value.value as? String)
                    TYPE_BOOLEAN -> putBoolean(key, value.value as? Boolean ?: false)
                    TYPE_INT -> putInt(key, (value.value as Number).toInt())
                    TYPE_LONG -> putLong(key, (value.value as Number).toLong())
                    TYPE_FLOAT -> putFloat(key, (value.value as Number).toFloat())
                    else -> {
                        when (val currentValue = current[key]) {
                            is Boolean -> putBoolean(key, value.value as? Boolean ?: currentValue)
                            is Int -> putInt(key, (value.value as? Number)?.toInt() ?: currentValue)
                            is Long -> putLong(key, (value.value as? Number)?.toLong() ?: currentValue)
                            is Float -> putFloat(key, (value.value as? Number)?.toFloat() ?: currentValue)
                            else -> putString(key, value.value as? String)
                        }
                    }
                }
            }
        }.apply()

        preview.settingsToApply[indexedSearchKey]?.value
            ?.let { it as? Boolean }
            ?.let(SearchIndexManager::setEnabled)
        enforceSecurityProtectionPrerequisites()
    }

    private fun enforceSecurityProtectionPrerequisites() {
        val hasSecurityPassword =
            AppSecurityManager.create(context, defaultSharedPreferences, secretStore).hasPassword()
        if (hasSecurityPassword) {
            return
        }
        val protectBrowserKey = context.getString(R.string.pref_key_protect_browser)
        val enableBiometricKey = context.getString(R.string.pref_key_enable_biometric)
        defaultSharedPreferences.edit().apply {
            putBoolean(protectBrowserKey, false)
            putBoolean(enableBiometricKey, false)
        }.apply()
    }

    private fun resolveType(value: Any?): String = when (value) {
        is Boolean -> TYPE_BOOLEAN
        is Int -> TYPE_INT
        is Long -> TYPE_LONG
        is Float -> TYPE_FLOAT
        else -> TYPE_STRING
    }

    private fun serializeFileSortOptions(value: FileSortOptions): Map<String, Any> = mapOf(
        FILE_SORT_BY to value.by.name,
        FILE_SORT_ORDER to value.order.name,
        FILE_SORT_DIRECTORIES_FIRST to value.isDirectoriesFirst
    )

    private fun restoreFileSortOptions(value: BackupValue): String {
        val defaultValue = defaultFileSortOptions()
        val options = if (value.type == TYPE_FILE_SORT_OPTIONS_V2) {
            val payload = value.value as? JSONObject
            val by = FileSortOptions.By.entries.firstOrNull { it.name == payload?.optString(FILE_SORT_BY) }
                ?: defaultValue.by
            val order = FileSortOptions.Order.entries.firstOrNull { it.name == payload?.optString(FILE_SORT_ORDER) }
                ?: defaultValue.order
            val directoriesFirst = if (payload?.has(FILE_SORT_DIRECTORIES_FIRST) == true) {
                payload.optBoolean(FILE_SORT_DIRECTORIES_FIRST)
            } else {
                defaultValue.isDirectoriesFirst
            }
            FileSortOptions(by, order, directoriesFirst)
        } else {
            defaultValue
        }
        return Parcel.obtain().use { parcel ->
            parcel.writeValue(options)
            parcel.marshall().toBase64().value
        }
    }

    companion object {
        private const val TYPE_STRING = "string"
        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_INT = "int"
        private const val TYPE_LONG = "long"
        private const val TYPE_FLOAT = "float"
        private const val TYPE_FILE_SORT_OPTIONS_V2 = "file_sort_options_v2"
        private const val FILE_SORT_BY = "by"
        private const val FILE_SORT_ORDER = "order"
        private const val FILE_SORT_DIRECTORIES_FIRST = "isDirectoriesFirst"
    }

    private fun defaultFileSortOptions(): FileSortOptions = FileSortOptions(
        by = FileSortOptions.By.NAME,
        order = FileSortOptions.Order.ASCENDING,
        isDirectoriesFirst = true
    )
}

