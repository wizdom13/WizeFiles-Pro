// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import java.nio.file.Files
import java.nio.file.FileSystems
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteOptionsSupportTest {

    @Test
    fun supportsSecureShred_returnsTrueForWritableDirectory() {
        val directory = Files.createTempDirectory("delete-options-dir")

        assertTrue(DeleteOptionsSupport.supportsSecureShred(directory))
    }

    @Test
    fun supportsSecureShred_returnsTrueForWritableFile() {
        val file = Files.createTempFile("delete-options", ".tmp")

        assertTrue(DeleteOptionsSupport.supportsSecureShred(file))
    }

    @Test
    fun supportsSecureShred_returnsFalseForNonLocalProviderPath() {
        val zipPath = Files.createTempDirectory("delete-options").resolve("provider.zip")
        val uri = java.net.URI.create("jar:${zipPath.toUri()}")

        FileSystems.newFileSystem(uri, mapOf("create" to "true")).use { fileSystem ->
            val providerFile = fileSystem.getPath("/inside.txt")
            Files.write(providerFile, "data".toByteArray())

            assertFalse(DeleteOptionsSupport.supportsSecureShred(providerFile))
        }
    }

    @Test
    fun sessionStore_keepsOptionsOnlyWhenSkipConfirmationEnabled() {
        DeleteConfirmationSessionStore.clear()
        DeleteConfirmationSessionStore.update(
            DeleteTargetMode.LOCAL_TRASH,
            DeleteOptions(permanentDelete = true, skipConfirmationForSession = false)
        )

        assertNull(DeleteConfirmationSessionStore.get(DeleteTargetMode.LOCAL_TRASH))

        val saved = DeleteOptions(permanentDelete = true, skipConfirmationForSession = true, secureShred = true)
        DeleteConfirmationSessionStore.update(DeleteTargetMode.LOCAL_TRASH, saved)

        assertTrue(DeleteConfirmationSessionStore.get(DeleteTargetMode.LOCAL_TRASH) == saved)
        assertNull(DeleteConfirmationSessionStore.get(DeleteTargetMode.PERMANENT_ONLY))
        DeleteConfirmationSessionStore.clear()
    }
}
