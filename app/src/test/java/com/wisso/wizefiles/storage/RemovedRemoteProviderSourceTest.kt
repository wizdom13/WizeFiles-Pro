// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class RemovedRemoteProviderSourceTest {

    @Test
    fun removedRemoteProviderReferencesAreGoneFromMainSource() {
        val files = File("app/src/main").walkTopDown().filter { it.isFile }
        files.forEach { file ->
            if (file.extension in setOf("kt", "java", "xml", "html", "gradle")) {
                val content = file.readText()
                assertFalse(
                    "Found forbidden symbol in ${file.path}",
                    Regex(buildString { intArrayOf(119,101,98,100,97,118).forEach { append(it.toChar()) } }, RegexOption.IGNORE_CASE).containsMatchIn(content)
                )
            }
        }
    }
}
