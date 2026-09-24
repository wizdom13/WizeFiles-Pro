// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive

import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchivePasswordHandlingTest {
    @Test
    fun limitsPasswordRetryAttemptsAndDeduplicates() {
        val fs = ArchiveFileSystemProvider.getOrNewFileSystem(Paths.get("/tmp/fake.zip"))
        fs.addPassword("one")
        fs.addPassword("two")
        fs.addPassword("two")
        fs.addPassword("three")
        fs.addPassword("four")

        val passwords = passwords(fs)
        assertEquals(listOf("two", "three", "four"), passwords)

        fs.close()
    }

    @Test
    fun clearsCachedPasswordOnClose() {
        val fs = ArchiveFileSystemProvider.getOrNewFileSystem(Paths.get("/tmp/fake2.zip"))
        fs.addPassword("secret")
        fs.close()

        assertEquals(emptyList<String>(), passwords(fs))
    }

    private fun passwords(fs: ArchiveFileSystem): List<String> {
        val field = fs.javaClass.getDeclaredField("passwords")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(fs) as List<String>
    }
}
