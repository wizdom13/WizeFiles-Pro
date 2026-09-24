package com.wisso.wizefiles.provider.common

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Provider hook for checking committed storage without consulting display-only overlays.
 *
 * File browsers may expose optimistic entries through
 * [java.nio.file.spi.FileSystemProvider.readAttributes].
 * Transfer conflict detection must not treat those entries as committed destination files.
 */
interface CommittedPathStateProvider {
    @Throws(IOException::class)
    fun existsInCommittedStorage(path: Path, vararg options: LinkOption): Boolean
}

internal fun Path.existsInCommittedStorage(vararg options: LinkOption): Boolean {
    val committedProvider = fileSystem.provider() as? CommittedPathStateProvider
    return committedProvider?.existsInCommittedStorage(this, *options)
        ?: Files.exists(this, *options)
}
