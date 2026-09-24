// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.rclone

import java.io.InterruptedIOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RcloneMutationConformanceTest {
    @Test fun `preexisting interruption stops before RPC and preserves state`() {
        val operations = FakeOperations()
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedIOException::class.java) {
                RcloneFileSystemProvider.copyWithOperations(source, target, emptyArray(), operations)
            }
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(operations.calls.isEmpty())
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun `target conflict stops before source lookup or mutation`() {
        val operations = FakeOperations().apply { entries[target.key] = file("target") }
        assertThrows(FileAlreadyExistsException::class.java) {
            RcloneFileSystemProvider.copyWithOperations(source, target, emptyArray(), operations)
        }
        assertEquals(listOf("stat:${target.key}"), operations.calls)
    }

    @Test fun `stale source stops before copy mutation`() {
        val operations = FakeOperations().apply { entries.remove(source.key) }
        assertThrows(NoSuchFileException::class.java) {
            RcloneFileSystemProvider.copyWithOperations(source, target, emptyArray(), operations)
        }
        assertTrue(operations.calls.none { it.startsWith("copy:") })
    }

    @Test fun `directory copy remains delegated to transfer engine`() {
        val operations = FakeOperations().apply { entries[source.key] = directory("source") }
        assertThrows(UnsupportedOperationException::class.java) {
            RcloneFileSystemProvider.copyWithOperations(source, target, emptyArray(), operations)
        }
        assertTrue(operations.calls.none { it.startsWith("copy:") })
    }

    @Test fun `replace copy reaches cross-remote mutation once`() {
        val operations = FakeOperations().apply { entries[target.key] = file("old") }
        RcloneFileSystemProvider.copyWithOperations(
            source,
            target,
            arrayOf(StandardCopyOption.REPLACE_EXISTING),
            operations
        )
        assertEquals(1, operations.calls.count { it.startsWith("copy:") })
        assertTrue(operations.calls.last() == "copy:${source.key}->${target.key}")
    }

    @Test fun `move validates source before concrete mutation`() {
        val operations = FakeOperations()
        RcloneFileSystemProvider.moveWithOperations(source, target, emptyArray(), operations)
        assertEquals(1, operations.calls.count { it.startsWith("move:") })
        assertTrue(operations.calls.last() == "move:${source.key}->${target.key}")
    }

    @Test fun `delete dispatches files and empty directories distinctly`() {
        val fileOperations = FakeOperations()
        RcloneFileSystemProvider.deleteWithOperations(source, fileOperations)
        assertTrue(fileOperations.calls.last() == "deleteFile:${source.key}")

        val directoryOperations = FakeOperations().apply {
            entries[source.key] = directory("folder")
        }
        RcloneFileSystemProvider.deleteWithOperations(source, directoryOperations)
        assertTrue(directoryOperations.calls.last() == "deleteDirectory:${source.key}")
    }

    private class FakeOperations : RcloneMutationOperations {
        val entries = mutableMapOf(source.key to file("source"))
        val calls = mutableListOf<String>()

        override fun stat(remoteName: String, path: String): RcloneEntry? {
            val key = "$remoteName:$path"
            calls += "stat:$key"
            return entries[key]
        }

        override fun createDirectory(remoteName: String, path: String) {
            calls += "mkdir:$remoteName:$path"
        }

        override fun deleteFile(remoteName: String, path: String) {
            calls += "deleteFile:$remoteName:$path"
        }

        override fun deleteEmptyDirectory(remoteName: String, path: String) {
            calls += "deleteDirectory:$remoteName:$path"
        }

        override fun copy(
            sourceRemote: String,
            sourcePath: String,
            targetRemote: String,
            targetPath: String
        ) {
            calls += "copy:$sourceRemote:$sourcePath->$targetRemote:$targetPath"
        }

        override fun move(
            sourceRemote: String,
            sourcePath: String,
            targetRemote: String,
            targetPath: String
        ) {
            calls += "move:$sourceRemote:$sourcePath->$targetRemote:$targetPath"
        }
    }

    companion object {
        private val source = RcloneFileSystemProvider
            .getOrNewFileSystem("source")
            .getPath("/file") as RclonePath
        private val target = RcloneFileSystemProvider
            .getOrNewFileSystem("target")
            .getPath("/copy") as RclonePath

        private val RclonePath.key: String
            get() = "$remoteName:$remotePath"

        private fun file(name: String) = RcloneEntry(
            path = name,
            name = name,
            size = 10,
            modifiedAt = null,
            isDirectory = false
        )

        private fun directory(name: String) = file(name).copy(isDirectory = true)
    }
}
