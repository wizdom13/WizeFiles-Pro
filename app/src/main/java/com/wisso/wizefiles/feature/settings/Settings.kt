// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import android.os.Environment
import android.text.TextUtils
import java.nio.file.Path
import java.nio.file.Paths
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.appClassLoader
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.core.android.compat.EnvironmentCompat2
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.feature.filebrowser.FileViewType
import com.wisso.wizefiles.feature.filebrowser.GridColumnOverrides
import com.wisso.wizefiles.feature.filebrowser.OpenApkDefaultAction
import com.wisso.wizefiles.navigation.BookmarkDirectory
import com.wisso.wizefiles.navigation.StandardDirectorySettings
import com.wisso.wizefiles.provider.root.RootStrategy
import com.wisso.wizefiles.storage.FileSystemRoot
import com.wisso.wizefiles.storage.PrimaryStorageVolume
import com.wisso.wizefiles.storage.Storage
import com.wisso.wizefiles.theme.night.NightMode
import java.io.File

object Settings {
    @Suppress("DEPRECATION")
    private fun externalStorageRootPath(): Path =
        Paths.get(Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0")

    val STORAGES: SettingLiveData<List<Storage>> by lazy {
        ParcelValueSettingLiveData(
            R.string.pref_key_storages,
            listOf(FileSystemRoot(null, true), PrimaryStorageVolume(null, true)),
            sanitizer = ::sanitizeLegacyStorages,
            classLoaderProvider = { legacyRemovedStorageClassLoader(appClassLoader ?: Settings::class.java.classLoader!!) },
            normalizer = ::normalizeLegacyRemovedStorages
        )
    }
    val FILE_LIST_DEFAULT_DIRECTORY: SettingLiveData<Path> by lazy {
        ParcelValueSettingLiveData(
            R.string.pref_key_file_list_default_directory,
            externalStorageRootPath()
        )
    }

    val FILE_LIST_PERSISTENT_DRAWER_OPEN: SettingLiveData<Boolean> by lazy {
        BooleanSettingLiveData(
            R.string.pref_key_file_list_persistent_drawer_open,
            R.bool.pref_default_value_file_list_persistent_drawer_open
        )
    }

    val FILE_LIST_SHOW_HIDDEN_FILES: SettingLiveData<Boolean> by lazy {
        BooleanSettingLiveData(
            R.string.pref_key_file_list_show_hidden_files,
            R.bool.pref_default_value_file_list_show_hidden_files
        )
    }

    val FILE_LIST_VIEW_TYPE: SettingLiveData<FileViewType> by lazy {
        EnumSettingLiveData(
            R.string.pref_key_file_list_view_type, R.string.pref_default_value_file_list_view_type,
            FileViewType::class.java
        )
    }

    val FILE_LIST_GRID_COLUMN_OVERRIDES: SettingLiveData<GridColumnOverrides> by lazy {
        ParcelValueSettingLiveData(
            R.string.pref_key_file_list_grid_column_overrides,
            GridColumnOverrides()
        )
    }

    val FILE_LIST_SORT_OPTIONS: SettingLiveData<FileSortOptions> by lazy {
        ParcelValueSettingLiveData(
            R.string.pref_key_file_list_sort_options,
            FileSortOptions(FileSortOptions.By.NAME, FileSortOptions.Order.ASCENDING, true)
        )
    }

    val CREATE_ARCHIVE_TYPE: SettingLiveData<Int> by lazy {
        ResourceIdSettingLiveData(R.string.pref_key_create_archive_type, R.id.zipRadio)
    }

    val NIGHT_MODE: SettingLiveData<NightMode> by lazy {
        EnumSettingLiveData(
            R.string.pref_key_night_mode, R.string.pref_default_value_night_mode,
            NightMode::class.java
        )
    }

    val BLACK_NIGHT_MODE: SettingLiveData<Boolean> by lazy {
        BooleanSettingLiveData(
            R.string.pref_key_black_night_mode, R.bool.pref_default_value_black_night_mode
        )
    }

    val FILE_ICON_SHAPE: SettingLiveData<String> by lazy {
        StringSettingLiveData(
            R.string.pref_key_file_icon_shape,
            R.string.pref_default_value_file_icon_shape
        )
    }

    val FILE_LIST_ANIMATION: SettingLiveData<Boolean> by lazy {
        BooleanSettingLiveData(
            R.string.pref_key_file_list_animation, R.bool.pref_default_value_file_list_animation
        )
    }


    val FILE_NAME_ELLIPSIZE: SettingLiveData<TextUtils.TruncateAt> by lazy {
        EnumSettingLiveData(
            R.string.pref_key_file_name_ellipsize, R.string.pref_default_value_file_name_ellipsize,
            TextUtils.TruncateAt::class.java
        )
    }

    val STANDARD_DIRECTORY_SETTINGS: SettingLiveData<List<StandardDirectorySettings>> by lazy {
        ParcelValueSettingLiveData(R.string.pref_key_standard_directory_settings, emptyList())
    }

    val BOOKMARK_DIRECTORIES: SettingLiveData<List<BookmarkDirectory>> by lazy {
        ParcelValueSettingLiveData(
            R.string.pref_key_bookmark_directories, listOf(
                BookmarkDirectory(
                    application.getString(R.string.settings_bookmark_directory_screenshots),
                    Paths.get(
                        File(
                            @Suppress("DEPRECATION")
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES
                            ), EnvironmentCompat2.DIRECTORY_SCREENSHOTS
                        ).absolutePath
                    )
                )
            )
        )
    }

    val RECYCLE_BIN: SettingLiveData<Boolean> by lazy {
        BooleanSettingLiveData(
            R.string.pref_key_recycle_bin, R.bool.pref_default_value_recycle_bin
        )
    }

    val ROOT_STRATEGY: SettingLiveData<RootStrategy> by lazy {
        EnumSettingLiveData(
            R.string.pref_key_root_strategy, R.string.pref_default_value_root_strategy,
            RootStrategy::class.java
        )
    }

    val ARCHIVE_FILE_NAME_ENCODING: SettingLiveData<String> by lazy {
        StringSettingLiveData(
            R.string.pref_key_archive_file_name_encoding,
            R.string.pref_default_value_archive_file_name_encoding
        )
    }

    val OPEN_APK_DEFAULT_ACTION: SettingLiveData<OpenApkDefaultAction> by lazy {
        EnumSettingLiveData(
            R.string.pref_key_open_apk_default_action,
            R.string.pref_default_value_open_apk_default_action,
            OpenApkDefaultAction::class.java
        )
    }

    val SHOW_PDF_THUMBNAIL_PRE_28: SettingLiveData<Boolean> by lazy {
        BooleanSettingLiveData(
            R.string.pref_key_show_pdf_thumbnail_pre_28,
            R.bool.pref_default_value_show_pdf_thumbnail_pre_28
        )
    }

    val READ_REMOTE_FILES_FOR_THUMBNAIL: SettingLiveData<Boolean> by lazy {
        BooleanSettingLiveData(
            R.string.pref_key_read_remote_files_for_thumbnail,
            R.bool.pref_default_value_read_remote_files_for_thumbnail
        )
    }

    val INDEXED_SEARCH: SettingLiveData<Boolean> by lazy {
        BooleanSettingLiveData(
            R.string.pref_key_indexed_search,
            R.bool.pref_default_value_indexed_search
        )
    }

    val PROTECT_BROWSER: SettingLiveData<Boolean> by lazy {
        BooleanSettingLiveData(
            R.string.pref_key_protect_browser,
            R.bool.pref_default_value_protect_browser
        )
    }

    val ENABLE_BIOMETRIC: SettingLiveData<Boolean> by lazy {
        BooleanSettingLiveData(
            R.string.pref_key_enable_biometric,
            R.bool.pref_default_value_enable_biometric
        )
    }

    val TIME_TO_RELOCK: SettingLiveData<String> by lazy {
        StringSettingLiveData(
            R.string.pref_key_time_to_relock,
            R.string.pref_default_value_time_to_relock
        )
    }

    val SFTP_PINNED_HOST_KEYS: SettingLiveData<String> by lazy {
        StringSettingLiveData(
            R.string.pref_key_sftp_pinned_host_keys,
            R.string.pref_default_value_sftp_pinned_host_keys
        )
    }
}
