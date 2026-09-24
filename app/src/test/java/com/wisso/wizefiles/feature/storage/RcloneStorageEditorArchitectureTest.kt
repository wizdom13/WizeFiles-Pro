// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RcloneStorageEditorArchitectureTest {
    @Test
    fun `fragment delegates durable state schema validation and persistence`() {
        val source = projectFile(
            "src/main/java/com/wisso/wizefiles/feature/storage/EditRcloneStorageFragment.kt"
        )
        listOf(
            "RcloneStorageEditorViewModel",
            "RcloneProviderSchema",
            "RcloneFormRenderer",
            "RcloneConfigurationValidator",
            "RcloneAuthenticationCoordinator",
            "RcloneStorageRepository"
        ).forEach { assertTrue("Missing $it delegation", it in source) }
    }

    private fun projectFile(path: String): String =
        listOf(File(path), File("app/$path")).first(File::exists).readText()
}
