// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.remote

import java.nio.file.FileSystem

class RemoteFileSystemInterface(private val fileSystem: FileSystem) : IFileSystemBridge.Stub() {
    override fun closeFileSystem(exception: BridgeFailure) {
        serveBridge(exception) { fileSystem.close() }
    }
}
