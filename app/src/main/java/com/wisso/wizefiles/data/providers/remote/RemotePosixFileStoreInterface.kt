// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote

import com.wisso.wizefiles.provider.common.PosixFileStore

class RemotePosixFileStoreInterface(
    private val fileStore: PosixFileStore
) : IPosixStoreBridge.Stub() {
    override fun updateReadOnly(readOnly: Boolean, exception: BridgeFailure) {
        serveBridge(exception) { fileStore.isReadOnly = readOnly }
    }

    override fun totalBytes(exception: BridgeFailure): Long =
        serveBridge(exception) { fileStore.totalSpace } ?: 0

    override fun usableBytes(exception: BridgeFailure): Long =
        serveBridge(exception) { fileStore.usableSpace } ?: 0

    override fun unallocatedBytes(exception: BridgeFailure): Long =
        serveBridge(exception) { fileStore.unallocatedSpace } ?: 0
}
