package com.wisso.wizefiles.settings

import android.content.ContextWrapper
import com.wisso.wizefiles.R

internal object SettingsRestoreTestKeys {
    val idToKey: Map<Int, String> = mapOf(
        R.string.pref_key_file_list_default_directory to "key_file_list_default_directory",
        R.string.pref_key_file_list_persistent_drawer_open to "key_file_list_persistent_drawer_open",
        R.string.pref_key_file_list_show_hidden_files to "key_file_list_show_hidden_files",
        R.string.pref_key_file_list_view_type to "key_file_list_view_type",
        R.string.pref_key_file_list_grid_column_overrides to
            "key_file_list_grid_column_overrides",
        R.string.pref_key_file_list_sort_options to "key_file_list_sort_options",
        R.string.pref_key_create_archive_type to "key_create_archive_type",
        R.string.pref_key_locale to "key_locale",
        R.string.pref_key_night_mode to "key_night_mode",
        R.string.pref_key_black_night_mode to "key_black_night_mode",
        R.string.pref_key_file_icon_shape to "key_file_icon_shape",
        R.string.pref_key_file_list_animation to "key_file_list_animation",
        R.string.pref_key_file_name_ellipsize to "key_file_name_ellipsize",
        R.string.pref_key_standard_directory_settings to "key_standard_directories",
        R.string.pref_key_bookmark_directories to "key_bookmark_directories",
        R.string.pref_key_recycle_bin to "key_recycle_bin",
        R.string.pref_key_root_strategy to "key_root_strategy",
        R.string.pref_key_archive_file_name_encoding to "key_archive_file_name_encoding",
        R.string.pref_key_open_apk_default_action to "key_open_apk_default_action",
        R.string.pref_key_show_pdf_thumbnail_pre_28 to "key_show_pdf_thumbnail_pre_28",
        R.string.pref_key_read_remote_files_for_thumbnail to "key_read_remote_files_for_thumbnail",
        R.string.pref_key_indexed_search to "key_indexed_search",
        R.string.pref_key_protect_browser to "key_protect_browser",
        R.string.pref_key_enable_biometric to "key_enable_biometric",
        R.string.pref_key_time_to_relock to "key_time_to_relock",
        R.string.pref_key_sftp_pinned_host_keys to "key_sftp_pinned_host_keys"
    )

    val allKeys: Set<String> = idToKey.values.toSet()
    val mockContext = ContextWrapper(null)

    fun key(id: Int): String = idToKey[id]
        ?: error("Missing test key mapping for id=$id. Known ids=${idToKey.keys.sorted()}")

    fun requireRestorableKey(description: String, predicate: (String) -> Boolean): String =
        allKeys.firstOrNull(predicate)
            ?: error("Missing restorable key for $description. Available keys: ${allKeys.sorted().joinToString()}")

    fun requirePolicyKey(description: String, key: String): String {
        require(allKeys.contains(key)) {
            "Missing policy key for $description: $key. Available keys: ${allKeys.sorted().joinToString()}"
        }
        return key
    }
}
