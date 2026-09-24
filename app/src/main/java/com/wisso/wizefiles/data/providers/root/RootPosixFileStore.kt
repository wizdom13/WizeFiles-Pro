// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.root

import com.wisso.wizefiles.provider.common.PosixFileStore
import com.wisso.wizefiles.provider.remote.BinderEndpoint
import com.wisso.wizefiles.provider.remote.RemotePosixFileStore

class RootPosixFileStore(fileStore: PosixFileStore) : RemotePosixFileStore(
    BinderEndpoint { RootFileService.connectPosixStore(fileStore) }
)
