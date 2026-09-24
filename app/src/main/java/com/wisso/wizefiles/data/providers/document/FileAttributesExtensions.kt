// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.document

import android.provider.DocumentsContract
import java.nio.file.ProviderMismatchException
import java.nio.file.attribute.BasicFileAttributes
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.util.hasBits

val BasicFileAttributes.documentSupportsThumbnail: Boolean
    get() {
        this as? DocumentFileAttributes ?: throw ProviderMismatchException(toString())
        return flags().hasBits(DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL)
    }

val FileMetadata.documentSupportsThumbnail: Boolean
    get() = false
