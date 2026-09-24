// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.root

import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileAttributeView
import com.wisso.wizefiles.provider.remote.BinderEndpoint
import com.wisso.wizefiles.provider.remote.RemoteFileSystemProvider
import java.net.URI

open class RootFileSystemProvider(scheme: String) : RemoteFileSystemProvider(
    BinderEndpoint { RootFileService.providerFor(scheme) }
) {
    override fun getScheme(): String {
        throw AssertionError()
    }

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        throw AssertionError()
    }

    override fun getFileSystem(uri: URI): FileSystem {
        throw AssertionError()
    }

    override fun getPath(uri: URI): Path {
        throw AssertionError()
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? {
        throw AssertionError()
    }
}
