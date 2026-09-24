// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.provider

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderTreeTraverserTest {
    @Test
    fun `walks a non-local NIO provider tree without converting it to a local file`() {
        val archive = Files.createTempDirectory("provider-traversal").resolve("tree.zip")
        val uri = URI.create("jar:${archive.toUri()}")

        FileSystems.newFileSystem(uri, mapOf("create" to "true")).use { fileSystem ->
            val root = fileSystem.getPath("/")
            Files.createDirectories(root.resolve("folder/nested"))
            Files.write(root.resolve("folder/file.txt"), "one".toByteArray())
            Files.write(root.resolve("folder/nested/file.txt"), "two".toByteArray())
            val directories = mutableListOf<String>()
            val files = mutableListOf<String>()

            ProviderTreeTraverser.walkPreOrder(
                root.resolve("folder"),
                onDirectory = { path, _ -> directories += path.toString() },
                onFile = { path, _ -> files += path.toString() }
            )

            assertEquals(2, directories.size)
            assertEquals(2, files.size)
        }
    }
}
