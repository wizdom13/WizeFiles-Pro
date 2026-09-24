// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.core.files.mime.MimeType

class PickOptions(
    val mode: Mode,
    val fileName: String?,
    val readOnly: Boolean,
    val mimeTypes: List<MimeType>,
    val localOnly: Boolean,
    val allowMultiple: Boolean,
    val allowDirectories: Boolean = false,
    val selectWithLongPress: Boolean = false
) {
    enum class Mode {
        OPEN_FILE,
        CREATE_FILE,
        OPEN_DIRECTORY
    }
}
