// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive

import java.nio.file.StandardOpenOption
import com.wisso.wizefiles.provider.common.OpenOptions

internal fun OpenOptions.checkForArchive() {
    if (write) {
        throw UnsupportedOperationException(StandardOpenOption.WRITE.toString())
    }
    if (append) {
        throw UnsupportedOperationException(StandardOpenOption.APPEND.toString())
    }
    if (truncateExisting) {
        throw UnsupportedOperationException(StandardOpenOption.TRUNCATE_EXISTING.toString())
    }
    if (create) {
        throw UnsupportedOperationException(StandardOpenOption.CREATE.toString())
    }
    if (createNew) {
        throw UnsupportedOperationException(StandardOpenOption.CREATE_NEW.toString())
    }
    if (deleteOnClose) {
        throw UnsupportedOperationException(StandardOpenOption.DELETE_ON_CLOSE.toString())
    }
    if (sync) {
        throw UnsupportedOperationException(StandardOpenOption.SYNC.toString())
    }
    if (dsync) {
        throw UnsupportedOperationException(StandardOpenOption.DSYNC.toString())
    }
}
