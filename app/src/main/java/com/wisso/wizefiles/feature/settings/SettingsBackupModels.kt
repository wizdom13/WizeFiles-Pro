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

object SettingsBackupFileName {
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyyyy")

    fun default(date: LocalDate = LocalDate.now()): String =
        "WizeFiles_backup${date.format(DATE_FORMAT)}${SettingsBackupRestoreManager.BACKUP_EXTENSION}"
}

data class SettingsBackupData(
    val formatVersion: Int,
    val settings: Map<String, BackupValue>,
    val secretSettings: Map<String, String?>
)

data class BackupValue(val type: String, val value: Any?)

data class SettingsBackupEncryptionInfo(val isEncrypted: Boolean)
data class SettingsRestorePreview(
    val schemaVersion: Int,
    val settingsToApply: Map<String, BackupValue>,
    val skippedSettings: Map<String, String>,
    val warnings: List<String>
)

object SettingsBackupRegistry {
    /**
     * Authoritative list of user-configurable app settings that are eligible for backup.
     *
     * This list is tied to the settings layer (Settings + settings XML).
     * Intentionally excluded:
     * - one-shot UI actions (backup/restore trigger preferences),
     * - runtime/session state.
     * - secret value payloads (stored separately via SecretStore).
     */
    val backupSettingKeyResIds: Set<Int> = setOf(
        R.string.pref_key_file_list_default_directory,
        R.string.pref_key_file_list_persistent_drawer_open,
        R.string.pref_key_file_list_show_hidden_files,
        R.string.pref_key_file_list_view_type,
        R.string.pref_key_file_list_grid_column_overrides,
        R.string.pref_key_file_list_sort_options,
        R.string.pref_key_create_archive_type,
        R.string.pref_key_locale,
        R.string.pref_key_night_mode,
        R.string.pref_key_black_night_mode,
        R.string.pref_key_file_icon_shape,
        R.string.pref_key_file_list_animation,
        R.string.pref_key_file_name_ellipsize,
        R.string.pref_key_standard_directory_settings,
        R.string.pref_key_bookmark_directories,
        R.string.pref_key_recycle_bin,
        R.string.pref_key_root_strategy,
        R.string.pref_key_archive_file_name_encoding,
        R.string.pref_key_open_apk_default_action,
        R.string.pref_key_show_pdf_thumbnail_pre_28,
        R.string.pref_key_read_remote_files_for_thumbnail,
        R.string.pref_key_indexed_search,
        R.string.pref_key_protect_browser,
        R.string.pref_key_enable_biometric,
        R.string.pref_key_time_to_relock,
        R.string.pref_key_sftp_pinned_host_keys
    )
}
