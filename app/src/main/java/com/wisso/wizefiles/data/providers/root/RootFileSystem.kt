// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.root

import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider
import com.wisso.wizefiles.provider.remote.BinderEndpoint
import com.wisso.wizefiles.provider.remote.RemoteFileSystem
import java.io.IOException

open class RootFileSystem(fileSystem: FileSystem) : RemoteFileSystem(
    BinderEndpoint { RootFileService.connectFileSystem(fileSystem) }
) {
    override fun provider(): FileSystemProvider {
        throw AssertionError()
    }

    override fun isOpen(): Boolean {
        throw AssertionError()
    }

    override fun isReadOnly(): Boolean {
        throw AssertionError()
    }

    override fun getSeparator(): String {
        throw AssertionError()
    }

    override fun getRootDirectories(): Iterable<Path> {
        throw AssertionError()
    }

    override fun getFileStores(): Iterable<FileStore> {
        throw AssertionError()
    }

    override fun supportedFileAttributeViews(): Set<String> {
        throw AssertionError()
    }

    override fun getPath(first: String, vararg more: String): Path {
        throw AssertionError()
    }

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
        throw AssertionError()
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw AssertionError()
    }

    @Throws(IOException::class)
    override fun newWatchService(): WatchService {
        throw UnsupportedOperationException()
    }
}
