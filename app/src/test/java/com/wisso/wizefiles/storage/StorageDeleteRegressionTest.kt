// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Test

class StorageDeleteRegressionTest {

    @Test
    fun `delete succeeds for local file uri path`() {
        val file = Files.createTempFile("storage-delete", ".tmp")
        val fileUriPath = Paths.get(file.toUri())

        StorageFacade().delete(fileUriPath)

        assertFalse(Files.exists(file))
    }
}
