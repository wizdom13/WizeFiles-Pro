// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.io.IOException
import java.nio.file.FileStore
import java.nio.file.attribute.FileStoreAttributeView

abstract class AbstractFileStore : FileStore() {
    final override fun <V : FileStoreAttributeView?> getFileStoreAttributeView(
        type: Class<V>
    ): V? = null

    @Throws(IOException::class)
    final override fun getAttribute(attribute: String): Any {
        throw UnsupportedOperationException(
            "File-store attribute is not exposed by this provider: $attribute"
        )
    }
}
