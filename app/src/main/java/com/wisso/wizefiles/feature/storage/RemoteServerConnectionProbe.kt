package com.wisso.wizefiles.storage

import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import com.wisso.wizefiles.provider.common.newDirectoryStream

internal suspend fun <S> probeRemoteServer(
    server: S,
    register: (S) -> Unit,
    unregister: (S) -> Unit,
    rootPath: (S) -> Path
) {
    runInterruptible(Dispatchers.IO) {
        register(server)
        try {
            val root = rootPath(server)
            root.fileSystem.use {
                root.newDirectoryStream().use { listing ->
                    val iterator = listing.iterator()
                    while (iterator.hasNext()) iterator.next()
                }
            }
        } finally {
            unregister(server)
        }
    }
}
