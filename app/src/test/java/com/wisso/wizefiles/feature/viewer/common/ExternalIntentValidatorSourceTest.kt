// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.viewer.common

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalIntentValidatorSourceTest {
    @Test
    fun `validator allows own file provider wrapped uris`() {
        val source = readProjectFile(
            "src/main/java/com/wisso/wizefiles/feature/viewer/common/ExternalIntentValidator.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/viewer/common/ExternalIntentValidator.kt"
        )
        assertTrue(source.contains("parsed?.authority == BuildConfig.FILE_PROVIDER_AUTHORITY"))
        assertTrue(source.contains("if (!isOwnFileProviderWrapper && blockedNestedSchemes.any { it in normalized })"))
    }

    @Test
    fun `validator performs metadata lookup on the io dispatcher`() {
        val source = readProjectFile(
            "src/main/java/com/wisso/wizefiles/feature/viewer/common/ExternalIntentValidator.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/viewer/common/ExternalIntentValidator.kt"
        )
        val ioDispatcherIndex = source.indexOf("withContext(Dispatchers.IO)")
        val sizeQueryIndex = source.indexOf("querySize(context, safeUri)")

        assertTrue(source.contains("suspend fun validate("))
        assertTrue(ioDispatcherIndex >= 0)
        assertTrue(sizeQueryIndex > ioDispatcherIndex)
    }

    @Test
    fun `text editor awaits validation before attaching its fragment`() {
        val source = readProjectFile(
            "src/main/java/com/wisso/wizefiles/feature/viewer/text/TextEditorActivity.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/viewer/text/TextEditorActivity.kt"
        )

        assertTrue(source.contains("lifecycleScope.launch"))
        assertTrue(source.contains("ExternalIntentValidator.validate("))
        assertTrue(source.contains("externalIntentValidated = true"))
        assertTrue(source.contains("Lifecycle.State.STARTED"))
        assertTrue(source.contains("supportFragmentManager.isStateSaved"))
    }

    private fun readProjectFile(vararg candidates: String): String {
        for (candidate in candidates) {
            val path = Path.of(candidate)
            if (Files.exists(path)) return String(Files.readAllBytes(path))
        }
        error("Unable to locate any candidate paths: ${candidates.joinToString()}")
    }
}
