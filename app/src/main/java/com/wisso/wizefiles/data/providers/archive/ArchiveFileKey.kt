package com.wisso.wizefiles.provider.archive

import android.os.Parcelable
import com.wisso.wizefiles.storage.path.AppPath
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class ArchiveFileKey(
    private val archiveFile: AppPath,
    private val entryName: String
) : Parcelable
