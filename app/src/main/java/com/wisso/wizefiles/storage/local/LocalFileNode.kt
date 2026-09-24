// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.local

import com.wisso.wizefiles.storage.FileNode
import java.io.File

/**
 * Local filesystem node for the new internal storage abstraction.
 */
data class LocalFileNode(val file: File) : FileNode {
    override val backendId: String = LocalStorageBackend.BACKEND_ID
    override val path: String = file.absolutePath
    override val name: String = file.name.ifEmpty { file.absolutePath }
}
