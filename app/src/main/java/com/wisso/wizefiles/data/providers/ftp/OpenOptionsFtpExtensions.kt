package com.wisso.wizefiles.provider.ftp

import java.nio.file.StandardOpenOption
import com.wisso.wizefiles.provider.common.OpenOptions

internal fun OpenOptions.checkForFtp() {
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
