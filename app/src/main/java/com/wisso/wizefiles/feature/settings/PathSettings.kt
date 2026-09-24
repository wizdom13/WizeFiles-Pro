// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import androidx.core.content.res.ResourcesCompat
import java.nio.file.Path
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.filebrowser.FileSortOptions
import com.wisso.wizefiles.feature.filebrowser.FileViewType
import com.wisso.wizefiles.feature.filebrowser.GridColumnOverrides

object PathSettings {
    private const val NAME_SUFFIX = "path"

    @Suppress("UNCHECKED_CAST")
    fun getFileListViewType(path: Path): SettingLiveData<FileViewType?> =
        EnumSettingLiveData(
            NAME_SUFFIX, R.string.pref_key_file_list_view_type, path.toString(),
            ResourcesCompat.ID_NULL, FileViewType::class.java
        ) as SettingLiveData<FileViewType?>

    fun getFileListGridColumnOverrides(
        path: Path
    ): SettingLiveData<GridColumnOverrides?> =
        ParcelValueSettingLiveData(
            NAME_SUFFIX,
            R.string.pref_key_file_list_grid_column_overrides,
            path.toString(),
            null
        )

    fun getFileListSortOptions(path: Path): SettingLiveData<FileSortOptions?> =
        ParcelValueSettingLiveData(
            NAME_SUFFIX, R.string.pref_key_file_list_sort_options, path.toString(), null
        )

    fun getFileListViewSortPathSpecific(path: Path): SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            NAME_SUFFIX,
            R.string.pref_key_file_list_view_sort_path_specific,
            path.toString(),
            R.bool.pref_default_value_file_list_view_sort_path_specific
        )
}
