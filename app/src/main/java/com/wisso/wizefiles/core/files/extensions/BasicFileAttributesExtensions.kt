// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.files.extensions

import com.wisso.wizefiles.core.files.model.FileSize
import com.wisso.wizefiles.core.files.model.asFileSize
import com.wisso.wizefiles.storage.FileMetadata
import java.time.Instant

val FileMetadata.fileSize: FileSize
    get() = size().asFileSize()

val FileMetadata.lastModifiedInstant: Instant
    get() = Instant.ofEpochMilli(lastModifiedEpochMillis ?: 0L)
