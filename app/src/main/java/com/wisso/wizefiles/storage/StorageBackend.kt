package com.wisso.wizefiles.storage

import java.io.IOException

/**
 * Narrow app-oriented backend contract used to introduce the storage replacement incrementally.
 *
 * This intentionally avoids recreating java.nio APIs and only exposes operations needed for
 * initial migration slices.
 */
interface StorageBackend<N : FileNode> {
    val backendId: String

    @Throws(IOException::class)
    fun exists(node: N): Boolean

    @Throws(IOException::class)
    fun stat(node: N): FileMetadata?

    @Throws(IOException::class)
    fun list(node: N): List<N>
}
