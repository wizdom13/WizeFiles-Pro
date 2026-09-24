// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive

import android.os.Parcelable
import com.wisso.wizefiles.storage.path.AppPath
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class ArchiveFileKey(
    private val archiveFile: AppPath,
    private val entryName: String
) : Parcelable
