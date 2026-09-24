// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.os

import android.system.OsConstants
import com.wisso.wizefiles.provider.common.OpenOptions
import com.wisso.wizefiles.provider.os.syscall.LinuxSyscallConstants

internal fun OpenOptions.toLinuxFlags(): Int {
    var flags = if (read && write) {
        OsConstants.O_RDWR
    } else if (write) {
        OsConstants.O_WRONLY
    } else {
        OsConstants.O_RDONLY
    }
    if (append) {
        flags = flags or OsConstants.O_APPEND
    }
    if (truncateExisting) {
        flags = flags or OsConstants.O_TRUNC
    }
    if (createNew) {
        flags = flags or OsConstants.O_CREAT or OsConstants.O_EXCL
    } else if (create) {
        flags = flags or OsConstants.O_CREAT
    }
    if (sync) {
        flags = flags or OsConstants.O_SYNC
    }
    if (dsync) {
        flags = flags or LinuxSyscallConstants.O_DSYNC
    }
    if (noFollowLinks || (!createNew && deleteOnClose)) {
        flags = flags or OsConstants.O_NOFOLLOW
    }
    return flags
}
