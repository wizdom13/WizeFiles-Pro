package com.wisso.wizefiles.provider.smb

import java.net.URI
import java.nio.file.StandardWatchEventKinds
import org.junit.Assert.assertThrows
import org.junit.Test

class SmbPathWatchRegistrationTest {

    @Test
    fun `register rejects smb root path without share`() {
        val path = SmbFileSystemProvider.getPath(URI.create("smb://user@192.169.0.64/")) as SmbPath
        val watcher = path.fileSystem.newWatchService()
        try {
            assertThrows(UnsupportedOperationException::class.java) {
                path.register(watcher, arrayOf(StandardWatchEventKinds.ENTRY_CREATE))
            }
        } finally {
            watcher.close()
        }
    }
}
