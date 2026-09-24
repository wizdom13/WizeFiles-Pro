// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.files.model

import java.io.File

object JavaFile {
    fun isDirectory(path: String): Boolean = File(path).isDirectory

    fun getFreeSpace(path: String): Long = File(path).freeSpace

    fun getTotalSpace(path: String): Long = File(path).totalSpace
}
