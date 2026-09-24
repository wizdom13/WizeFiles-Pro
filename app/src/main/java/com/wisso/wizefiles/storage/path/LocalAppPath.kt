// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.path

import java.io.File
import kotlinx.parcelize.Parcelize

/**
 * Local [AppPath] backed by [java.io.File].
 */
@Parcelize
data class LocalAppPath(val file: File) : AppPath {
    override val rawPath: String
        get() = file.path

    override val name: String
        get() = file.name.ifEmpty { file.path }
}
